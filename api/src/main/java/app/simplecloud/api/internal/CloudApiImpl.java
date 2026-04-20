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
import app.simplecloud.api.internal.server.ServerApiImpl;
import app.simplecloud.api.internal.player.PlayerApiImpl;
import app.simplecloud.api.persistentserver.PersistentServerApi;
import app.simplecloud.api.player.PlayerApi;
import app.simplecloud.api.server.ServerApi;
import io.nats.client.Connection;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

public class CloudApiImpl implements CloudApi {

    private final CloudApiOptions options;

    private final NatsFailoverConnectionManager natsConnectionManager;
    private final Connection natsClient;
    private final QueryCache queryCache;
    private final CacheEventListener cacheEventListener;
    private final ServerApi serverApi;
    private final GroupApi groupApi;
    private final BlueprintApi blueprintApi;
    private final PersistentServerApi persistentServerApi;
    private final EventApi eventApi;
    private final PlayerApi playerApi;
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
        this.eventApi = new EventApiImpl(natsClient, options.getNetworkId());
        this.serverApi = new ServerApiImpl(options, queryCache);
        this.groupApi = new GroupApiImpl(options, queryCache);
        this.blueprintApi = new BlueprintApiImpl(options, queryCache);
        this.persistentServerApi = new PersistentServerApiImpl(options, queryCache);
        this.playerApi = new PlayerApiImpl(options, natsClient);

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
    public BlueprintApi blueprint() {
        return blueprintApi;
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
            cacheEventListener.shutdown();
        }
        if (queryCache instanceof QueryCacheImpl queryCacheImpl) {
            queryCacheImpl.shutdown();
        }
        natsConnectionManager.shutdown();
    }

}
