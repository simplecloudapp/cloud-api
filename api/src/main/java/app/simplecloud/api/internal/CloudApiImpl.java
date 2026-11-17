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
import app.simplecloud.api.internal.nats.InstanceNatsConnection;
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

    // Static, so every CloudApi instance in this class loader with equal settings uses the same connection.
    private static final SharedResources<NatsConnectionSettings, NatsFailoverConnectionManager> NATS_CONNECTIONS =
            new SharedResources<>(NatsFailoverConnectionManager::shutdown);
    // Pooled connections go to the controller, so instances of one controller share a pool; each ApiClient
    // sets its own headers and timeouts on top of it.
    private static final SharedResources<String, OkHttpClient> HTTP_CLIENTS_BY_CONTROLLER_URL =
            new SharedResources<>(ApiClients::shutdown);

    private final CloudApiOptions options;
    private final SharedResources.Handle<OkHttpClient> httpClientHandle;
    private final ApiClient apiClient;

    private final SharedResources.Handle<NatsFailoverConnectionManager> natsConnectionHandle;
    private final InstanceNatsConnection natsConnection;
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

        // Closed again if a later step fails, or a shared resource would stay open after its last user closed.
        SharedResources.Handle<NatsFailoverConnectionManager> natsConnectionHandle = null;
        SharedResources.Handle<OkHttpClient> httpClientHandle = null;
        InstanceNatsConnection natsConnection = null;
        QueryCache queryCache = null;
        try {
            NatsConnectionSettings natsSettings = NatsConnectionSettings.of(options);
            natsConnectionHandle = NATS_CONNECTIONS.acquire(natsSettings, natsSettings::openConnectionManager);
            this.natsConnectionHandle = natsConnectionHandle;
            httpClientHandle = HTTP_CLIENTS_BY_CONTROLLER_URL.acquire(options.getControllerUrl(), ApiClients::newHttpClient);
            this.httpClientHandle = httpClientHandle;
            natsConnection = new InstanceNatsConnection(natsConnectionHandle.resource().getConnection());
            this.natsConnection = natsConnection;

            // Initialize cache
            CacheConfig cacheConfig = options.getCacheConfig();
            if (cacheConfig.isEnabled()) {
                queryCache = new QueryCacheImpl(cacheConfig, ApiClients.maxRequestDuration(options));
            } else {
                queryCache = new NoOpQueryCache();
            }
            this.queryCache = queryCache;

            // Initialize APIs with cache
            this.apiClient = ApiClients.create(options, httpClientHandle.resource());
            this.playerApi = new PlayerApiImpl(options, natsConnection.asConnection(), apiClient, executor);
            this.eventApi = new EventApiImpl(natsConnection.asConnection(), options.getNetworkId(), playerApi);
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
            closeConnections(natsConnection, natsConnectionHandle, httpClientHandle);
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
     * then up to one second to send the NATS messages already queued. The NATS connection closes only when
     * this is the last open instance that uses it; otherwise close waits only for the running tasks.
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
        closeConnections(natsConnection, natsConnectionHandle, httpClientHandle);
    }

    /**
     * Closes this instance's NATS connection and its handles on the shared connections. Takes nulls,
     * for a constructor that failed before it had them all.
     */
    private static void closeConnections(
            InstanceNatsConnection natsConnection,
            SharedResources.Handle<NatsFailoverConnectionManager> natsConnectionHandle,
            SharedResources.Handle<OkHttpClient> httpClientHandle
    ) {
        if (natsConnection != null) {
            natsConnection.close();
        }
        if (natsConnectionHandle != null) {
            try {
                natsConnectionHandle.close();
            } catch (RuntimeException e) {
                LOGGER.log(Level.WARNING, "Failed to shutdown NATS connection", e);
            }
        }
        if (httpClientHandle != null) {
            httpClientHandle.close();
        }
    }

    private record NatsConnectionSettings(
            String natsUrl,
            String networkId,
            String networkSecret,
            Duration failoverReconnectAfter
    ) {

        static NatsConnectionSettings of(CloudApiOptions options) {
            return new NatsConnectionSettings(
                    options.getNatsUrl(),
                    options.getNetworkId(),
                    options.getNetworkSecret(),
                    options.getNatsFailoverReconnectAfter()
            );
        }

        NatsFailoverConnectionManager openConnectionManager() {
            return new NatsFailoverConnectionManager(natsUrl, networkId, networkSecret, failoverReconnectAfter);
        }
    }
}
