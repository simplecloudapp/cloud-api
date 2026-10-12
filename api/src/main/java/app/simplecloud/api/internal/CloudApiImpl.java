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
import app.simplecloud.api.internal.persistentserver.PersistentServerApiImpl;
import app.simplecloud.api.internal.server.ServerApiImpl;
import app.simplecloud.api.internal.player.PlayerApiImpl;
import app.simplecloud.api.internal.web.ApiClients;
import app.simplecloud.api.web.ApiClient;
import app.simplecloud.api.persistentserver.PersistentServerApi;
import app.simplecloud.api.player.PlayerApi;
import app.simplecloud.api.server.ServerApi;
import io.nats.client.Connection;

import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CloudApiImpl implements CloudApi {

    private static final Logger LOGGER = Logger.getLogger(CloudApiImpl.class.getName());

    private final CloudApiOptions options;
    private final ApiClient httpClient;

    private final NatsFailoverConnectionManager natsConnectionManager;
    private final Connection natsClient;
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

        try {
            this.natsConnectionManager = new NatsFailoverConnectionManager(
                    options.getNatsUrl(),
                    options.getNetworkId(),
                    options.getNetworkSecret(),
                    options.getNatsFailoverReconnectAfter()
            );
            this.natsClient = natsConnectionManager.getConnection();
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }

        // Initialize cache
        CacheConfig cacheConfig = options.getCacheConfig();
        if (cacheConfig.isEnabled()) {
            this.queryCache = new QueryCacheImpl(cacheConfig);
        } else {
            this.queryCache = new NoOpQueryCache();
        }

        // Initialize APIs with cache
        this.httpClient = ApiClients.create(options);
        this.playerApi = new PlayerApiImpl(options, natsClient, httpClient, executor);
        this.eventApi = new EventApiImpl(natsClient, options.getNetworkId(), playerApi);
        this.serverApi = new ServerApiImpl(options, queryCache, httpClient, executor);
        this.groupApi = new GroupApiImpl(options, queryCache, httpClient, executor);
        this.persistentServerApi = new PersistentServerApiImpl(options, queryCache, httpClient, executor);

        // Setup event-based cache invalidation with debouncing
        if (cacheConfig.isEnabled() && cacheConfig.isAutoInvalidateOnEvents()) {
            long debounceMs = cacheConfig.getEventDebounceTime().toMillis();
            this.cacheEventListener = new CacheEventListener(queryCache, eventApi, debounceMs);
        } else {
            this.cacheEventListener = null;
        }

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
     * Returns the underlying NATS connection.
     * This is for internal use by integration modules.
     *
     * @return the NATS connection
     */
    public Connection getNatsConnection() {
        return natsClient;
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
        try {
            natsConnectionManager.shutdown();
        } catch (RuntimeException e) {
            LOGGER.log(Level.WARNING, "Failed to shutdown NATS connection", e);
        }
        httpClient.getHttpClient().dispatcher().executorService().shutdown();
        httpClient.getHttpClient().connectionPool().evictAll();
        executor.shutdown();
    }

}
