package app.simplecloud.api.persistentserver;

import app.simplecloud.api.CloudApiException;
import app.simplecloud.api.group.DeploymentHost;
import app.simplecloud.api.group.Group;
import app.simplecloud.api.group.GroupApi;
import app.simplecloud.api.group.ScalingConfig;
import app.simplecloud.api.workflow.WorkflowExecutionResult;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * API for managing persistent servers.
 */
public interface PersistentServerApi {

    /**
     * Retrieves a persistent server by its unique identifier.
     *
     * @param id the unique ID of the persistent server
     * @return a future that completes with the persistent server, or fails with a
     * {@link CloudApiException} ({@link CloudApiException.Reason#NOT_FOUND}) if it does not exist
     */
    CompletableFuture<PersistentServer> getPersistentServerById(String id);

    /**
     * Finds a persistent server by its unique identifier.
     *
     * <p>An ID that isn't a UUID returns an empty Optional without asking the controller.
     *
     * @param id the unique ID of the persistent server
     * @return a future that completes with the persistent server, or an empty Optional if it does not exist
     */
    CompletableFuture<Optional<PersistentServer>> findPersistentServerById(String id);

    /**
     * Retrieves a persistent server by its name.
     *
     * @param name the name of the persistent server
     * @return a future that completes with the persistent server, or fails with a
     * {@link CloudApiException} ({@link CloudApiException.Reason#NOT_FOUND}) if it does not exist
     */
    CompletableFuture<PersistentServer> getPersistentServerByName(String name);

    /**
     * Finds a persistent server by its name.
     *
     * @param name the name of the persistent server
     * @return a future that completes with the persistent server, or an empty Optional if it does not exist
     */
    CompletableFuture<Optional<PersistentServer>> findPersistentServerByName(String name);

    /**
     * Retrieves all persistent servers, optionally filtered by a query.
     *
     * @param query optional query parameters to filter results
     * @return a future that completes with a list of matching persistent servers
     */
    CompletableFuture<List<PersistentServer>> getAllPersistentServers(@Nullable PersistentServerQuery query);

    /**
     * Retrieves all persistent servers without any filtering.
     *
     * @return a future that completes with a list of all persistent servers
     */
    default CompletableFuture<List<PersistentServer>> getAllPersistentServers() {
        return getAllPersistentServers(null);
    }

    /**
     * Creates a new persistent server.
     *
     * @param request the configuration for the new persistent server
     * @return a future that completes with the created persistent server
     */
    CompletableFuture<PersistentServer> createPersistentServer(CreatePersistentServerRequest request);

    /**
     * Updates an existing persistent server.
     *
     * <p>Only the top-level fields set in the request are updated. A {@code workflows}
     * configuration replaces the stored one as a whole, so it must be complete.
     *
     * @param id      the unique ID of the persistent server to update
     * @param request the updated configuration
     * @return a future that completes with the updated persistent server
     */
    CompletableFuture<PersistentServer> updatePersistentServer(String id, UpdatePersistentServerRequest request);

    /**
     * Deletes a persistent server and its referenced blueprint, if present.
     *
     * <p>The controller cannot delete a persistent server while its server still exists, including a server that
     * is still stopping. Stop the server, wait until it is gone and retry.
     *
     * @param id the unique ID of the persistent server to delete
     * @return a future that completes when the persistent server and its referenced blueprint are deleted
     */
    CompletableFuture<Void> deletePersistentServer(String id);

    /**
     * Runs a manual workflow configured on the persistent server for all its eligible running servers.
     *
     * @param id the unique ID of the persistent server
     * @param workflowId the ID of the manual workflow
     * @return a future that completes with the result per server
     */
    CompletableFuture<WorkflowExecutionResult> executeWorkflow(String id, String workflowId);

    /**
     * Converts a persistent server into a server group.
     *
     * <p>The persistent server must be inactive and have no server instance; otherwise the future fails
     * with {@link CloudApiException.Reason#CONFLICT}. The group gets a new ID and starts inactive. The
     * persistent server is deleted and world files are not migrated.
     *
     * @param persistentServerId the ID of the persistent server to convert
     * @param hosts the hosts the group may run on; an empty list allows all hosts
     * @param scaling the complete scaling config of the group, see {@link GroupApi#updateGroup}
     * @return a future that completes with the new group
     */
    CompletableFuture<Group> convertToGroup(
            String persistentServerId, List<DeploymentHost> hosts, ScalingConfig scaling);

    /**
     * Replaces all properties of a persistent server.
     *
     * <p>Keys not contained in {@code properties} are removed. Use
     * {@link #updatePersistentServerProperties(String, Map)} to merge instead.
     *
     * @param id the unique ID of the persistent server
     * @param properties the new properties
     * @return a future that completes with the stored properties
     */
    CompletableFuture<Map<String, Object>> setPersistentServerProperties(String id, Map<String, Object> properties);

    /**
     * Updates persistent server properties by merging with existing properties (deep merge).
     *
     * @param id the unique ID of the persistent server
     * @param properties the properties to merge
     * @return a future that completes with the updated properties
     */
    CompletableFuture<Map<String, Object>> updatePersistentServerProperties(String id, Map<String, Object> properties);

    /**
     * Updates a single persistent server property by merging it with existing properties.
     *
     * @param id the unique ID of the persistent server
     * @param key the property key to update
     * @param value the property value to set
     * @return a future that completes with the updated properties
     */
    default CompletableFuture<Map<String, Object>> updatePersistentServerProperty(String id, String key, Object value) {
        return updatePersistentServerProperties(id, Map.of(key, value));
    }

    /**
     * Deletes specific property keys from a persistent server.
     *
     * @param id the unique ID of the persistent server
     * @param keys the property keys to delete
     * @return a future that completes with the remaining properties
     */
    CompletableFuture<Map<String, Object>> deletePersistentServerProperties(String id, List<String> keys);

    /**
     * Deletes a single property key from a persistent server.
     *
     * @param id the unique ID of the persistent server
     * @param key the property key to delete
     * @return a future that completes with the remaining properties
     */
    default CompletableFuture<Map<String, Object>> deletePersistentServerProperty(String id, String key) {
        return deletePersistentServerProperties(id, List.of(key));
    }
}
