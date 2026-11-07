package app.simplecloud.api;

import app.simplecloud.api.blueprint.BlueprintApi;
import app.simplecloud.api.cache.QueryCache;
import app.simplecloud.api.event.EventApi;
import app.simplecloud.api.group.GroupApi;
import app.simplecloud.api.internal.CloudApiImpl;
import app.simplecloud.api.persistentserver.PersistentServerApi;
import app.simplecloud.api.player.PlayerApi;
import app.simplecloud.api.server.ServerApi;

/**
 * Main entry point for the SimpleCloud API.
 */
public interface CloudApi extends AutoCloseable {

    /**
     * Creates a CloudAPI instance with default options.
     *
     * <p>Default options are loaded from environment variables:
     * <ul>
     *   <li>SIMPLECLOUD_NETWORK_ID (default: "default")</li>
     *   <li>SIMPLECLOUD_NETWORK_SECRET (default: "")</li>
     *   <li>SIMPLECLOUD_NATS_URL (default: "wss://nats.simplecloud.app:443")</li>
     *   <li>SIMPLECLOUD_NATS_FAILOVER_RECONNECT_AFTER (default: "30s")</li>
     *   <li>SIMPLECLOUD_CONTROLLER_URL (default: "<a href="https://controller.simplecloud.app">...</a>")</li>
     * </ul>
     *
     * @return a new CloudApi instance
     */
    static CloudApi create() {
        return create(CloudApiOptions.DEFAULT);
    }

    /**
     * Creates a CloudAPI instance with custom options.
     *
     * @param options the configuration options for the API client
     * @return a new CloudApi instance
     */
    static CloudApi create(CloudApiOptions options) {
        return new CloudApiImpl(options);
    }

    /**
     * Returns the group management API.
     *
     * <p>Use this to create, read, update, and delete server groups.
     *
     * @return the group API
     */
    GroupApi group();

    /**
     * Returns the server management API.
     *
     * <p>Use this to query, update and stop servers. To start a server of a group, use
     * {@link GroupApi#requestServerStart(String)}.
     *
     * @return the server API
     */
    ServerApi server();

    /**
     * Returns the persistent server management API.
     *
     * <p>Use this to create, read, update, and delete persistent servers.
     * Persistent servers are long-lived server instances that maintain state across restarts.
     *
     * @return the persistent server API
     */
    PersistentServerApi persistentServer();

    /**
     * Returns the blueprint management API.
     *
     * <p>Use this to create, read, update, and delete blueprints.
     *
     * @return the blueprint API
     */
    BlueprintApi blueprint();

    /**
     * Returns the event subscription API.
     *
     * <p>Use this to subscribe to real-time events for groups, servers,
     * persistent servers, and blueprints.
     *
     * @return the event API
     */
    EventApi event();

    /**
     * Returns the player management API.
     *
     * <p>Use this to query players, kick them or connect them to other servers.
     *
     * @return the player API
     */
    PlayerApi player();

    /**
     * Returns the network ID this API is connected to.
     *
     * @return the network ID
     */
    String getNetworkId();

    /**
     * Returns the query cache, for manual invalidation or to inspect cache statistics.
     *
     * @return the query cache
     */
    QueryCache cache();

    @Override
    default void close() {}
}
