package app.simplecloud.api.server;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * API for managing server instances.
 *
 * <p>Provides methods to query running servers, start new server instances
 * from server groups, and stop running servers.
 */
public interface ServerApi {

    /**
     * Retrieves a server by its unique identifier.
     *
     * @param id the unique server ID
     * @return a CompletableFuture that completes with the server, or fails if not found
     */
    CompletableFuture<Server> getServerById(String id);

    /**
     * Retrieves a server by its numerical ID.
     *
     * @param groupName the name of the server group
     * @param numericalId the numerical server ID
     * @return a CompletableFuture that completes with the server, or fails if not found
     */
    CompletableFuture<Server> getServerByNumericalId(String groupName, int numericalId);

    /**
     * Retrieves all servers belonging to a specific group.
     *
     * @param groupName the name of the server group
     * @return a CompletableFuture that completes with a list of servers in the specified group
     */
    CompletableFuture<List<Server>> getServersByGroup(String groupName);

    /**
     * Retrieves all servers, optionally filtered by a query.
     *
     * @param query optional query parameters to filter results (group, host, state, sorting, limit)
     * @return a CompletableFuture that completes with a list of matching servers
     */
    CompletableFuture<List<Server>> getAllServers(@Nullable ServerQuery query);

    /**
     * Retrieves all servers without any filters.
     *
     * @return a CompletableFuture that completes with a list of all servers
     */
    default CompletableFuture<List<Server>> getAllServers() {
        return getAllServers(null);
    }

    /**
     * Starts a new server instance from a server group.
     *
     * @param request the start request specifying which group to start from
     * @return a CompletableFuture that completes with the started Server instance
     */
    CompletableFuture<Server> startServer(StartServerRequest request);

    /**
     * Stops a running server.
     *
     * @param id the unique ID of the server to stop
     * @return a CompletableFuture that completes when the server stop is initiated
     */
    CompletableFuture<Void> stopServer(String id);

    /**
     * Updates an existing server instance.
     *
     * @param id the unique ID of the server to update
     * @param request the update request containing fields to update
     * @return a CompletableFuture that completes with the updated Server instance
     */
    CompletableFuture<Server> updateServer(String id, UpdateServerRequest request);

    /**
     * Retrieves the current server instance using the SIMPLECLOUD_UNIQUE_ID environment variable.
     *
     * @return a CompletableFuture that completes with the current server, or fails if not found
     */
    CompletableFuture<Server> getCurrentServer();

    /**
     * Updates server properties by merging with existing properties (deep merge).
     *
     * @param id the unique ID of the server
     * @param properties the properties to merge
     * @return a CompletableFuture that completes with the updated properties
     */
    CompletableFuture<Map<String, Object>> updateServerProperties(String id, Map<String, Object> properties);

    /**
     * Deletes specific property keys from a server.
     *
     * @param id the unique ID of the server
     * @param keys the property keys to delete
     * @return a CompletableFuture that completes with the remaining properties
     */
    CompletableFuture<Map<String, Object>> deleteServerProperties(String id, List<String> keys);
}

