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
import app.simplecloud.api.internal.nats.ScopedNatsConnection;
import app.simplecloud.api.internal.persistentserver.PersistentServerApiImpl;
import app.simplecloud.api.internal.server.ServerApiImpl;
import app.simplecloud.api.internal.player.PlayerApiImpl;
import app.simplecloud.api.internal.shared.SharedResources;
import app.simplecloud.api.internal.shared.SharedResources.Lease;
import app.simplecloud.api.internal.web.ApiClients;
import app.simplecloud.api.web.ApiClient;
import app.simplecloud.api.persistentserver.PersistentServerApi;
import app.simplecloud.api.player.PlayerApi;
import app.simplecloud.api.server.ServerApi;
import io.nats.client.Connection;
import okhttp3.OkHttpClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CloudApiImpl implements CloudApi {

    private static final Logger LOGGER = Logger.getLogger(CloudApiImpl.class.getName());

    private final CloudApiOptions options;
    private final Lease<OkHttpClient> okHttpClientLease;
    private final ApiClient httpClient;

    private final Lease<NatsFailoverConnectionManager> natsLease;
    private final ScopedNatsConnection scopedNatsConnection;
    private final Connection natsClient;
    private final Lease<? extends QueryCache> queryCacheLease;
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

        // Leases taken before a failure must be returned, or other instances' resources never close.
        List<Lease<?>> acquiredLeases = new ArrayList<>();
        try {
            this.natsLease = acquireNats(options);
            acquiredLeases.add(natsLease);
            this.okHttpClientLease = acquireOkHttpClient(options);
            acquiredLeases.add(okHttpClientLease);
            this.queryCacheLease = acquireQueryCache(options);
            acquiredLeases.add(queryCacheLease);
            this.scopedNatsConnection = new ScopedNatsConnection(natsLease.get().getConnection());
            this.natsClient = scopedNatsConnection.connection();
            this.queryCache = queryCacheLease.get();
            CacheConfig cacheConfig = options.getCacheConfig();

            // Initialize APIs with cache
            this.httpClient = ApiClients.create(options, okHttpClientLease.get());
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
        } catch (RuntimeException | Error e) {
            for (Lease<?> lease : acquiredLeases) {
                closeQuietly("leased resource", lease);
            }
            executor.shutdown();
            throw e;
        }
    }

    private static Lease<NatsFailoverConnectionManager> acquireNats(CloudApiOptions options) {
        if (options.isDedicatedNatsConnection()) {
            return SharedResources.dedicated(newNatsConnectionManager(options), NatsFailoverConnectionManager::shutdown);
        }
        NatsKey key = new NatsKey(
                options.getNatsUrl(),
                options.getNetworkId(),
                options.getNetworkSecret(),
                options.getNatsFailoverReconnectAfter()
        );
        return SharedResources.acquire(
                key,
                () -> newNatsConnectionManager(options),
                NatsFailoverConnectionManager::shutdown
        );
    }

    private static NatsFailoverConnectionManager newNatsConnectionManager(CloudApiOptions options) {
        return new NatsFailoverConnectionManager(
                options.getNatsUrl(),
                options.getNetworkId(),
                options.getNetworkSecret(),
                options.getNatsFailoverReconnectAfter()
        );
    }

    private static Lease<OkHttpClient> acquireOkHttpClient(CloudApiOptions options) {
        if (options.isDedicatedHttpClient()) {
            return SharedResources.dedicated(ApiClients.newHttpClient(), ApiClients::close);
        }
        return SharedResources.acquire(HttpClientKey.INSTANCE, ApiClients::newHttpClient, ApiClients::close);
    }

    private static Lease<? extends QueryCache> acquireQueryCache(CloudApiOptions options) {
        CacheConfig cacheConfig = options.getCacheConfig();
        if (!cacheConfig.isEnabled()) {
            return SharedResources.dedicated(new NoOpQueryCache(), cache -> {
            });
        }
        Duration inFlightTimeout = ApiClients.maxRequestDuration(options);
        if (options.isDedicatedHttpClient()) {
            return SharedResources.dedicated(new QueryCacheImpl(cacheConfig, inFlightTimeout), QueryCacheImpl::shutdown);
        }
        QueryCacheKey key = new QueryCacheKey(
                options.getControllerUrl(),
                options.getNetworkId(),
                options.getNetworkSecret(),
                cacheConfig,
                inFlightTimeout
        );
        return SharedResources.acquire(
                key,
                () -> new QueryCacheImpl(cacheConfig, inFlightTimeout),
                QueryCacheImpl::shutdown
        );
    }

    private record NatsKey(String natsUrl, String networkId, String networkSecret, Duration failoverReconnectAfter) {
    }

    private record QueryCacheKey(
            String controllerUrl,
            String networkId,
            String networkSecret,
            CacheConfig cacheConfig,
            Duration inFlightTimeout
    ) {
    }

    private enum HttpClientKey {
        INSTANCE
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
        closeQuietly("query cache", queryCacheLease);
        scopedNatsConnection.close();
        closeQuietly("NATS connection", natsLease);
        closeQuietly("HTTP client", okHttpClientLease);
        executor.shutdown();
    }

    private static void closeQuietly(String name, Lease<?> lease) {
        try {
            lease.close();
        } catch (RuntimeException e) {
            LOGGER.log(Level.WARNING, "Failed to shutdown " + name, e);
        }
    }

}
