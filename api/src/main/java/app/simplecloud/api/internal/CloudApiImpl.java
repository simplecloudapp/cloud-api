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
import app.simplecloud.api.internal.networkstate.CopyForReads;
import app.simplecloud.api.internal.networkstate.NetworkViewShell;
import app.simplecloud.api.internal.networkstate.StateRevisionInterceptor;
import app.simplecloud.api.internal.persistentserver.PersistentServerApiImpl;
import app.simplecloud.api.internal.server.ServerApiImpl;
import app.simplecloud.api.internal.player.PlayerApiImpl;
import app.simplecloud.api.internal.web.ApiClients;
import app.simplecloud.api.web.ApiClient;
import app.simplecloud.api.persistentserver.PersistentServerApi;
import app.simplecloud.api.player.PlayerApi;
import app.simplecloud.api.server.ServerApi;
import io.nats.client.Connection;
import okhttp3.OkHttpClient;

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
    private final SharedRuntime runtime;
    private final InstanceNatsConnection natsConnection;
    private final ApiClient apiClient;
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

        this.runtime = SharedRuntime.acquire(options);
        // Released again if a later step fails, or the shared runtime would stay open after its last user closed.
        InstanceNatsConnection natsConnection = null;
        QueryCache queryCache = null;
        try {
            natsConnection = new InstanceNatsConnection(runtime.natsConnectionManager().getConnection());
            this.natsConnection = natsConnection;

            // Initialize cache
            CacheConfig cacheConfig = options.getCacheConfig();
            if (cacheConfig.isEnabled()) {
                queryCache = new QueryCacheImpl(cacheConfig, ApiClients.maxRequestDuration(options));
            } else {
                queryCache = new NoOpQueryCache();
            }
            this.queryCache = queryCache;

            CopyForReads copyForReads = CopyForReads.NO_VIEW;
            OkHttpClient httpClient = runtime.httpClient();
            NetworkViewShell networkViewShell = runtime.networkViewShell();
            if (networkViewShell != null) {
                copyForReads = networkViewShell;
                httpClient = StateRevisionInterceptor.addTo(httpClient, networkViewShell);
            }

            // Initialize APIs with cache
            this.apiClient = ApiClients.create(options, httpClient);
            this.playerApi = new PlayerApiImpl(options, natsConnection.asConnection(), apiClient, executor);
            this.eventApi = new EventApiImpl(natsConnection.asConnection(), options.getNetworkId(), playerApi);
            this.serverApi = new ServerApiImpl(options, queryCache, apiClient, executor, copyForReads);
            this.groupApi = new GroupApiImpl(options, queryCache, apiClient, executor, copyForReads);
            this.persistentServerApi = new PersistentServerApiImpl(options, queryCache, apiClient, executor,
                    copyForReads);

            // Setup event-based cache invalidation with debouncing
            if (cacheConfig.isEnabled() && cacheConfig.isAutoInvalidateOnEvents()) {
                long debounceMs = cacheConfig.getEventDebounceTime().toMillis();
                this.cacheEventListener = new CacheEventListener(queryCache, eventApi, debounceMs);
            } else {
                this.cacheEventListener = null;
            }
        } catch (RuntimeException | Error e) {
            if (natsConnection != null) {
                natsConnection.close();
            }
            releaseRuntime();
            if (queryCache instanceof QueryCacheImpl queryCacheImpl) {
                queryCacheImpl.shutdown();
            }
            executor.shutdown();
            throw e;
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
     * Returns this instance's NATS connection, on top of a connection it may share with other instances.
     * Closing or draining it closes only the dispatchers and subscriptions this instance created.
     * This is for internal use by integration modules.
     *
     * @return this instance's NATS connection
     */
    public Connection getNatsConnection() {
        return natsConnection.asConnection();
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
     * then up to one second to send the NATS messages already queued. The shared NATS connection closes only
     * when this is the last open instance that uses it; otherwise close waits only for the running tasks.
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
        // so they get a short time to finish before this instance's NATS connection closes.
        executor.shutdown();
        try {
            if (!executor.awaitTermination(EXECUTOR_SHUTDOWN_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                LOGGER.warning("Tasks still running after waiting " + EXECUTOR_SHUTDOWN_TIMEOUT.toMillis()
                        + " ms at close; NATS messages they send may be lost");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        natsConnection.close();
        releaseRuntime();
    }

    private void releaseRuntime() {
        try {
            runtime.release();
        } catch (RuntimeException e) {
            LOGGER.log(Level.WARNING, "Failed to close the connections this instance shared", e);
        }
    }
}
