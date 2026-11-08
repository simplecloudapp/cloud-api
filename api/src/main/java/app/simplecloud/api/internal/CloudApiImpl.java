package app.simplecloud.api.internal;

import app.simplecloud.api.CloudApi;
import app.simplecloud.api.CloudApiOptions;
import app.simplecloud.api.blueprint.BlueprintApi;
import app.simplecloud.api.cache.CacheConfig;
import app.simplecloud.api.cache.QueryCache;
import app.simplecloud.api.event.EventApi;
import app.simplecloud.api.group.GroupApi;
import app.simplecloud.api.internal.blueprint.BlueprintApiImpl;
import app.simplecloud.api.internal.cache.CacheEventListener;
import app.simplecloud.api.internal.cache.NoOpQueryCache;
import app.simplecloud.api.internal.cache.QueryCacheImpl;
import app.simplecloud.api.internal.event.EventApiImpl;
import app.simplecloud.api.internal.group.GroupApiImpl;
import app.simplecloud.api.internal.nats.NatsFailoverConnectionManager;
import app.simplecloud.api.internal.persistentserver.PersistentServerApiImpl;
import app.simplecloud.api.internal.player.PlayerApiImpl;
import app.simplecloud.api.internal.server.ServerApiImpl;
import app.simplecloud.api.internal.stats.StatsApiImpl;
import app.simplecloud.api.internal.web.ApiClientFactory;
import app.simplecloud.api.persistentserver.PersistentServerApi;
import app.simplecloud.api.player.PlayerApi;
import app.simplecloud.api.server.ServerApi;
import app.simplecloud.api.stats.StatsApi;
import app.simplecloud.api.web.ApiClient;
import io.nats.client.Connection;

import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CloudApiImpl implements CloudApi {

    private static final Logger LOGGER = Logger.getLogger(CloudApiImpl.class.getName());
    private static final Duration SHUTDOWN_GRACE_PERIOD = Duration.ofSeconds(2);

    private final CloudApiOptions options;
    private final ApiClient httpClient;

    private final NatsFailoverConnectionManager natsConnectionManager;
    private final Connection natsClient;
    private final QueryCache queryCache;
    private final CacheEventListener cacheEventListener;
    private final ServerApi serverApi;
    private final GroupApi groupApi;
    private final PersistentServerApi persistentServerApi;
    private final BlueprintApi blueprintApi;
    private final StatsApi statsApi;
    private final EventApi eventApi;
    private final PlayerApi playerApi;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public CloudApiImpl(CloudApiOptions options) {
        this.options = options;

        this.natsConnectionManager = new NatsFailoverConnectionManager(
                options.getNatsUrl(),
                options.getNetworkId(),
                options.getNetworkSecret(),
                options.getNatsFailoverReconnectAfter());
        this.natsClient = natsConnectionManager.getConnection();

        // Initialize cache
        CacheConfig cacheConfig = options.getCacheConfig();
        if (cacheConfig.isEnabled()) {
            this.queryCache = new QueryCacheImpl(cacheConfig, ApiClientFactory.maxRequestDuration(options));
        } else {
            this.queryCache = new NoOpQueryCache();
        }

        // Initialize APIs with cache
        this.httpClient = ApiClientFactory.create(options);
        this.playerApi = new PlayerApiImpl(options, natsClient, httpClient, executor);
        this.eventApi = new EventApiImpl(natsClient, options.getNetworkId(), playerApi);
        this.serverApi = new ServerApiImpl(options, queryCache, httpClient, executor);
        this.groupApi = new GroupApiImpl(options, queryCache, httpClient, executor);
        this.persistentServerApi = new PersistentServerApiImpl(options, queryCache, httpClient, executor);
        this.blueprintApi = new BlueprintApiImpl(options, queryCache, httpClient, executor);
        this.statsApi = new StatsApiImpl(options, httpClient, executor);

        // Setup event-based cache invalidation with debouncing
        if (cacheConfig.isEnabled() && cacheConfig.isAutoInvalidateOnEvents()) {
            long debounceMs = cacheConfig.getEventDebounceTime().toMillis();
            EventApi cacheEvents = new EventApiImpl(natsClient, options.getNetworkId(), playerApi);
            this.cacheEventListener = new CacheEventListener(queryCache, cacheEvents, debounceMs);
            natsConnectionManager.addReconnectListener(queryCache::clear);
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
    public BlueprintApi blueprint() {
        return blueprintApi;
    }

    @Override
    public StatsApi stats() {
        return statsApi;
    }

    @Override
    public EventApi event() {
        return eventApi;
    }

    @Override
    public PlayerApi player() {
        return playerApi;
    }

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

        executor.shutdown();
        try {
            if (!executor.awaitTermination(SHUTDOWN_GRACE_PERIOD.toMillis(), TimeUnit.MILLISECONDS)) {
                LOGGER.fine("Closing CloudApi while requests are still running");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
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
    }
}
