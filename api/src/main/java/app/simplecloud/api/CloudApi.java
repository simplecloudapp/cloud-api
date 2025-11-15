package app.simplecloud.api;

import app.simplecloud.api.event.EventApi;
import app.simplecloud.api.group.GroupApi;
import app.simplecloud.api.internal.CloudApiImpl;
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
public interface CloudApi {

    /**
     * Creates a CloudAPI instance with default options.
     * 
     * <p>Default options are loaded from environment variables:
     * <ul>
     *   <li>SIMPLECLOUD_NETWORK_ID (default: "default")</li>
     *   <li>SIMPLECLOUD_NETWORK_SECRET (default: "")</li>
     *   <li>SIMPLECLOUD_NATS_URL (default: "nats://localhost:4222")</li>
     *   <li>SIMPLECLOUD_CONTROLLER_URL (default: "http://localhost:1337")</li>
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
     * Returns the event subscription API.
     * 
     * <p>Use this to subscribe to real-time events for groups, servers,
     * persistent servers, and blueprints.
     * 
     * @return the event API
     */
    EventApi event();

}

