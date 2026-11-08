package app.simplecloud.api.server;

import app.simplecloud.api.CloudApiException;
import app.simplecloud.api.workflow.WorkflowExecutionResult;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * API for managing server instances.
 */
public interface ServerApi {

    /**
     * Retrieves a server by its unique identifier.
     *
     * @param id the unique server ID
     * @return a future that completes with the server, or fails with a
     * {@link CloudApiException} ({@link CloudApiException.Reason#NOT_FOUND}) if it does not exist
     */
    CompletableFuture<Server> getServerById(String id);

    /**
     * Finds a server by its unique identifier.
     *
     * <p>An ID that isn't a UUID returns an empty Optional without asking the controller.
     *
     * @param id the unique server ID
     * @return a future that completes with the server, or an empty Optional if it does not exist
     */
    CompletableFuture<Optional<Server>> findServerById(String id);

    /**
     * Retrieves a server by its numerical ID, the number in its cloud name {@code <group>-<number>}.
     *
     * @param groupName the name of the server group
     * @param numericalId the numerical server ID
     * @return a future that completes with the server, or fails with a
     * {@link CloudApiException} ({@link CloudApiException.Reason#NOT_FOUND}) if it does not exist
     */
    CompletableFuture<Server> getServerByNumericalId(String groupName, int numericalId);

    /**
     * Finds a server by its numerical ID.
     *
     * @param groupName the name of the server group
     * @param numericalId the numerical server ID
     * @return a future that completes with the server, or an empty Optional if it does not exist
     */
    CompletableFuture<Optional<Server>> findServerByNumericalId(String groupName, int numericalId);

    /**
     * Retrieves all servers belonging to a specific group.
     *
     * @param groupName the name of the server group
     * @return a future that completes with a list of servers in the specified group
     */
    CompletableFuture<List<Server>> getServersByGroup(String groupName);

    /**
     * Retrieves all servers, optionally filtered by a query.
     *
     * @param query optional query parameters to filter results (group, host, state, sorting, limit)
     * @return a future that completes with a list of matching servers
     */
    CompletableFuture<List<Server>> getAllServers(@Nullable ServerQuery query);

    /**
     * Retrieves all servers without any filters.
     *
     * @return a future that completes with a list of all servers
     */
    default CompletableFuture<List<Server>> getAllServers() {
        return getAllServers(null);
    }

    /**
     * Stops a running server.
     *
     * @param id the unique ID of the server to stop
     * @return a future that completes when the server stop is initiated
     */
    CompletableFuture<Void> stopServer(String id);

    /**
     * Sends a console command to a server.
     *
     * @param id the unique ID of the server
     * @param command the command to run
     * @return a future that completes when the command was sent, failed with an
     *         {@link IllegalArgumentException} if {@code id} or {@code command} is blank
     */
    CompletableFuture<Void> sendCommand(String id, String command);

    /**
     * Runs a manual workflow configured for the server.
     *
     * @param id the unique ID of the server
     * @param workflowId the ID of the manual workflow
     * @return a future that completes with the result
     */
    CompletableFuture<WorkflowExecutionResult> executeWorkflow(String id, String workflowId);

    /**
     * Updates an existing server instance.
     *
     * @param id the unique ID of the server to update
     * @param request the update request containing fields to update
     * @return a future that completes with the updated Server instance
     */
    CompletableFuture<Server> updateServer(String id, UpdateServerRequest request);

    /**
     * Retrieves the server this code runs on, using the SIMPLECLOUD_UNIQUE_ID environment variable.
     *
     * @return a future that completes with the current server, failed with an
     *         {@link IllegalStateException} if the variable is not set, or with {@code NOT_FOUND} if
     *         the server doesn't exist
     */
    CompletableFuture<Server> getCurrentServer();

    /**
     * Replaces all properties of a server.
     *
     * <p>Keys not contained in {@code properties} are removed. Use
     * {@link #updateServerProperties(String, Map)} to merge instead.
     *
     * @param id the unique ID of the server
     * @param properties the new properties
     * @return a future that completes with the stored properties
     */
    CompletableFuture<Map<String, Object>> setServerProperties(String id, Map<String, Object> properties);

    /**
     * Updates server properties by merging with existing properties (deep merge).
     *
     * @param id the unique ID of the server
     * @param properties the properties to merge
     * @return a future that completes with the updated properties
     */
    CompletableFuture<Map<String, Object>> updateServerProperties(String id, Map<String, Object> properties);

    /**
     * Updates a single server property by merging it with existing properties.
     *
     * @param id the unique ID of the server
     * @param key the property key to update
     * @param value the property value to set
     * @return a future that completes with the updated properties
     */
    default CompletableFuture<Map<String, Object>> updateServerProperty(String id, String key, Object value) {
        return updateServerProperties(id, Map.of(key, value));
    }

    /**
     * Deletes specific property keys from a server.
     *
     * @param id the unique ID of the server
     * @param keys the property keys to delete
     * @return a future that completes with the remaining properties
     */
    CompletableFuture<Map<String, Object>> deleteServerProperties(String id, List<String> keys);

    /**
     * Deletes a single property key from a server.
     *
     * @param id the unique ID of the server
     * @param key the property key to delete
     * @return a future that completes with the remaining properties
     */
    default CompletableFuture<Map<String, Object>> deleteServerProperty(String id, String key) {
        return deleteServerProperties(id, List.of(key));
    }
}
