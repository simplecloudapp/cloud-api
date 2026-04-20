package app.simplecloud.api;

import app.simplecloud.api.blueprint.BlueprintApi;
import app.simplecloud.api.cache.QueryCache;
import app.simplecloud.api.cache.QueryKey;
import app.simplecloud.api.event.EventApi;
import app.simplecloud.api.group.GroupApi;
import app.simplecloud.api.internal.CloudApiImpl;
import app.simplecloud.api.persistentserver.PersistentServerApi;
import app.simplecloud.api.player.PlayerApi;
import app.simplecloud.api.server.ServerApi;

/**
 * Main entry point for the SimpleCloud API.
 *
 * <p>This interface provides access to manage server groups, individual servers,
 * and subscribe to system events. Use the static factory methods to create an instance.
 *
 * <p>Example usage:
 * <pre>{@code
 * CloudApi api = CloudApi.create(CloudApiOptions.builder()
 *     .networkId("your-network-id")
 *     .networkSecret("your-secret")
 *     .build());
 *
 * // Manage server groups
 * api.group().getAllGroups().thenAccept(groups -> {
 *     groups.forEach(group -> System.out.println(group.getName()));
 * });
 *
 * // Query servers
 * api.server().getAllServers().thenAccept(servers -> {
 *     System.out.println("Active servers: " + servers.size());
 * });
 *
 * // Subscribe to events
 * Subscription sub = api.event().group().onCreated(event -> {
 *     System.out.println("Group created: " + event.getServerGroupId());
 * });
 * }</pre>
 */
public interface CloudApi extends AutoCloseable {

    /**
     * Creates a CloudAPI instance with default options.
     *
     * <p>Default options are loaded from environment variables:
     * <ul>
     *   <li>SIMPLECLOUD_NETWORK_ID (default: "default")</li>
     *   <li>SIMPLECLOUD_NETWORK_SECRET (default: "")</li>
     *   <li>SIMPLECLOUD_NATS_URL (default: "nats://platform.simplecloud.app:4222")</li>
     *   <li>SIMPLECLOUD_NATS_FAILOVER_RECONNECT_AFTER (default: "30s")</li>
     *   <li>SIMPLECLOUD_CONTROLLER_URL (default: "https://controller.platform.simplecloud.app")</li>
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
     * <p>Use this to query running servers, start new instances, and stop servers.
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
     * <p>Use this to create, read, update, and delete blueprints that groups and servers can use
     * as reusable source templates.
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
     * <p>Use this to query online players, send messages, kick, connect, etc.
     * Players implement Adventure's Audience interface for sending content.
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
     * Returns the query cache for manual cache operations.
     *
     * <p>Use this for manual invalidation or to inspect cache state:
     * <pre>{@code
     * // Invalidate a specific server
     * cloudApi.cache().invalidate(QueryKey.of("server", serverId));
     *
     * // Invalidate all servers
     * cloudApi.cache().invalidateAll(QueryKey.of("servers"));
     *
     * // Get cache statistics
     * CacheStats stats = cloudApi.cache().getStats();
     * System.out.println("Cache hit rate: " + stats.getHitRate());
     * }</pre>
     *
     * @return the query cache
     */
    QueryCache cache();

    @Override
    default void close() {
    }

}
