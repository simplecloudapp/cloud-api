package app.simplecloud.api.internal;

import app.simplecloud.api.CloudApi;
import app.simplecloud.api.CloudApiOptions;
import app.simplecloud.api.cache.CacheConfig;
import app.simplecloud.api.cache.QueryCache;
import app.simplecloud.api.event.EventApi;
import app.simplecloud.api.group.GroupApi;
import app.simplecloud.api.internal.cache.CacheEventListener;
import app.simplecloud.api.internal.cache.NoOpQueryCache;
import app.simplecloud.api.internal.cache.QueryCacheImpl;
import app.simplecloud.api.internal.event.EventApiImpl;
import app.simplecloud.api.internal.group.GroupApiImpl;
import app.simplecloud.api.internal.nats.NatsFailoverConnectionManager;
import app.simplecloud.api.internal.nats.PlatformNatsConnection;
import app.simplecloud.api.internal.nats.ScopedNatsConnection;
import app.simplecloud.api.internal.persistentserver.PersistentServerApiImpl;
import app.simplecloud.api.internal.server.ServerApiImpl;
import app.simplecloud.api.internal.player.PlayerApiImpl;
import app.simplecloud.api.internal.web.ApiClients;
import app.simplecloud.api.web.ApiClient;
import app.simplecloud.api.persistentserver.PersistentServerApi;
import app.simplecloud.api.player.PlayerApi;
import app.simplecloud.api.server.ServerApi;
import io.nats.client.Connection;

import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CloudApiImpl implements CloudApi {

    private static final Logger LOGGER = Logger.getLogger(CloudApiImpl.class.getName());

    private final CloudApiOptions options;
    private final ApiClient apiClient;

    private final NatsFailoverConnectionManager ownNatsConnectionManager;
    private final ScopedNatsConnection scopedNatsConnection;
    private final QueryCache queryCache;
    private final CacheEventListener cacheEventListener;
    private final ServerApi serverApi;
    private final GroupApi groupApi;
    private final PersistentServerApi persistentServerApi;
    private final EventApi eventApi;
    private final PlayerApi playerApi;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public CloudApiImpl(CloudApiOptions options) {
        this.options = options;

        NatsFailoverConnectionManager ownNatsConnectionManager = null;
        ScopedNatsConnection scopedNatsConnection = null;
        QueryCache queryCache = null;
        try {
            PlatformNatsConnection.Settings natsSettings = PlatformNatsConnection.Settings.of(options);
            Optional<NatsFailoverConnectionManager> platformNatsConnectionManager = PlatformNatsConnection.find(natsSettings);
            if (platformNatsConnectionManager.isEmpty()) {
                ownNatsConnectionManager = natsSettings.openConnectionManager();
            }
            NatsFailoverConnectionManager natsConnectionManager = platformNatsConnectionManager.orElse(ownNatsConnectionManager);
            scopedNatsConnection = new ScopedNatsConnection(natsConnectionManager.getConnection());
            this.ownNatsConnectionManager = ownNatsConnectionManager;
            this.scopedNatsConnection = scopedNatsConnection;
            Connection natsClient = scopedNatsConnection.connection();

            // Initialize cache
            CacheConfig cacheConfig = options.getCacheConfig();
            if (cacheConfig.isEnabled()) {
                queryCache = new QueryCacheImpl(cacheConfig, ApiClients.maxRequestDuration(options));
            } else {
                queryCache = new NoOpQueryCache();
            }
            this.queryCache = queryCache;

            // Initialize APIs with cache
            this.apiClient = ApiClients.create(options);
            this.playerApi = new PlayerApiImpl(options, natsClient, apiClient, executor);
            this.eventApi = new EventApiImpl(natsClient, options.getNetworkId(), playerApi);
            this.serverApi = new ServerApiImpl(options, queryCache, apiClient, executor);
            this.groupApi = new GroupApiImpl(options, queryCache, apiClient, executor);
            this.persistentServerApi = new PersistentServerApiImpl(options, queryCache, apiClient, executor);

            // Setup event-based cache invalidation with debouncing
            if (cacheConfig.isEnabled() && cacheConfig.isAutoInvalidateOnEvents()) {
                long debounceMs = cacheConfig.getEventDebounceTime().toMillis();
                this.cacheEventListener = new CacheEventListener(queryCache, eventApi, debounceMs);
            } else {
                this.cacheEventListener = null;
            }
        } catch (RuntimeException | Error e) {
            if (scopedNatsConnection != null) {
                scopedNatsConnection.close();
            }
            if (ownNatsConnectionManager != null) {
                ownNatsConnectionManager.shutdown();
            }
            if (queryCache instanceof QueryCacheImpl queryCacheImpl) {
                queryCacheImpl.shutdown();
            }
            executor.shutdown();
            throw e;
        }
    }

    /**
     * Creates the platform plugin's instance. Other instances in this class loader with the same
     * NATS settings use its NATS connection instead of opening their own, and closing this instance
     * closes that connection for all of them. If another connection is registered already, this
     * instance is created like any other.
     */
    public static CloudApiImpl createNatsConnectionOwner(CloudApiOptions options) {
        CloudApiImpl cloudApi = new CloudApiImpl(options);
        if (cloudApi.ownNatsConnectionManager != null) {
            PlatformNatsConnection.Settings natsSettings = PlatformNatsConnection.Settings.of(options);
            PlatformNatsConnection.register(natsSettings, cloudApi.ownNatsConnectionManager);
        }
        return cloudApi;
    }

    @Override
    public GroupApi group() {
        return groupApi;
    }

    @Override
    public ServerApi server() {
        return serverApi;
    }

    @Override
    public PersistentServerApi persistentServer() {
        return persistentServerApi;
    }

    @Override
    public EventApi event() {
        return eventApi;
    }

    @Override
    public PlayerApi player() {
        return playerApi;
    }

    /**
     * Returns this instance's view of the NATS connection, which may be shared with other instances.
     * Closing or draining the view only closes this instance's dispatchers.
     * This is for internal use by integration modules.
     *
     * @return the NATS connection
     */
    public Connection getNatsConnection() {
        return scopedNatsConnection.connection();
    }

    public Executor getExecutor() {
        return executor;
    }

    @Override
    public String getNetworkId() {
        return options.getNetworkId();
    }

    @Override
    public QueryCache cache() {
        return queryCache;
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }

        if (cacheEventListener != null) {
            try {
                cacheEventListener.shutdown();
            } catch (RuntimeException e) {
                LOGGER.log(Level.WARNING, "Failed to shutdown cache event listener", e);
            }
        }
        if (queryCache instanceof QueryCacheImpl queryCacheImpl) {
            try {
                queryCacheImpl.shutdown();
            } catch (RuntimeException e) {
                LOGGER.log(Level.WARNING, "Failed to shutdown query cache", e);
            }
        }
        scopedNatsConnection.close();
        if (ownNatsConnectionManager != null) {
            // Unregistered first, so instances created from now on open their own connection instead of this closing one.
            PlatformNatsConnection.unregister(ownNatsConnectionManager);
            try {
                ownNatsConnectionManager.shutdown();
            } catch (RuntimeException e) {
                LOGGER.log(Level.WARNING, "Failed to shutdown NATS connection", e);
            }
        }
        executor.shutdown();
    }

}
