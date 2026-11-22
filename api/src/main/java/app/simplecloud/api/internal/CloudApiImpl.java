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
import app.simplecloud.api.internal.util.blueprint.ManifestServerUrlResolver;
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
    private static final Duration EXECUTOR_SHUTDOWN_TIMEOUT = Duration.ofSeconds(1);

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
    private final EventApi eventApi;
    private final PlayerApi playerApi;
    private final StatsApi statsApi;
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

        CacheConfig cacheConfig = options.getCacheConfig();
        if (cacheConfig.isEnabled()) {
            this.queryCache = new QueryCacheImpl(cacheConfig, ApiClientFactory.maxRequestDuration(options));
        } else {
            this.queryCache = new NoOpQueryCache();
        }

        this.httpClient = ApiClientFactory.create(options);
        ApiContext context = new ApiContext(options, queryCache, httpClient, executor);
        this.playerApi = new PlayerApiImpl(context, natsClient);
        this.eventApi = new EventApiImpl(natsClient, options.getNetworkId(), playerApi);
        this.serverApi = new ServerApiImpl(context);
        ManifestServerUrlResolver serverUrlResolver =
                new ManifestServerUrlResolver(options, httpClient.getHttpClient());
        this.groupApi = new GroupApiImpl(context, serverUrlResolver);
        this.persistentServerApi = new PersistentServerApiImpl(context, serverUrlResolver);
        this.blueprintApi = new BlueprintApiImpl(context, serverUrlResolver);
        this.statsApi = new StatsApiImpl(context);

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
    public BlueprintApi blueprint() {
        return blueprintApi;
    }

    @Override
    public EventApi event() {
        return eventApi;
    }

    @Override
    public PlayerApi player() {
        return playerApi;
    }

    @Override
    public StatsApi stats() {
        return statsApi;
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

    /**
     * Blocks for up to about two seconds more than the NATS close itself: up to one second for running tasks,
     * then up to one second to send the NATS messages already queued.
     */
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
        // Player session reports publish on NATS from this executor,
        // so they get a short time to finish before NATS closes.
        executor.shutdown();
        try {
            if (!executor.awaitTermination(EXECUTOR_SHUTDOWN_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                LOGGER.warning("Tasks still running after waiting " + EXECUTOR_SHUTDOWN_TIMEOUT.toMillis()
                        + " ms at close; NATS messages they send may be lost");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
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
