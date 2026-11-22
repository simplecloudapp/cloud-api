package app.simplecloud.api.persistentserver;

import app.simplecloud.api.CloudApiException;
import app.simplecloud.api.group.DeploymentHost;
import app.simplecloud.api.group.Group;
import app.simplecloud.api.group.ScalingConfig;
import app.simplecloud.api.workflow.WorkflowExecutionResult;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.jetbrains.annotations.Nullable;

/**
 * API for managing persistent servers.
 *
 * <p>Persistent servers are long-lived server instances that maintain state across restarts.
 * Unlike servers spawned from a group, persistent servers don't have scaling or deployment
 * configuration - they are single instances that run on a specific host.
 */
public interface PersistentServerApi {

    /**
     * Retrieves a persistent server by its unique identifier.
     *
     * @param id the unique ID of the persistent server
     * @return a CompletableFuture that completes with the persistent server, or fails with a
     * {@link CloudApiException} ({@link CloudApiException.Reason#NOT_FOUND}) if it does not exist
     */
    CompletableFuture<PersistentServer> getPersistentServerById(String id);

    /**
     * Finds a persistent server by its unique identifier.
     *
     * @param id the unique ID of the persistent server
     * @return a CompletableFuture that completes with the persistent server, or an empty Optional if it does not exist
     */
    CompletableFuture<Optional<PersistentServer>> findPersistentServerById(String id);

    /**
     * Retrieves a persistent server by its name.
     *
     * @param name the name of the persistent server
     * @return a CompletableFuture that completes with the persistent server, or fails with a
     * {@link CloudApiException} ({@link CloudApiException.Reason#NOT_FOUND}) if it does not exist
     */
    CompletableFuture<PersistentServer> getPersistentServerByName(String name);

    /**
     * Finds a persistent server by its name.
     *
     * @param name the name of the persistent server
     * @return a CompletableFuture that completes with the persistent server, or an empty Optional if it does not exist
     */
    CompletableFuture<Optional<PersistentServer>> findPersistentServerByName(String name);

    /**
     * Retrieves all persistent servers, optionally filtered by a query.
     *
     * @param query optional query parameters to filter results
     * @return a CompletableFuture that completes with a list of matching persistent servers
     */
    CompletableFuture<List<PersistentServer>> getAllPersistentServers(@Nullable PersistentServerQuery query);

    /**
     * Retrieves all persistent servers without any filtering.
     *
     * @return a CompletableFuture that completes with a list of all persistent servers
     */
    default CompletableFuture<List<PersistentServer>> getAllPersistentServers() {
        return getAllPersistentServers(null);
    }

    /**
     * Creates a new persistent server.
     *
     * @param request the configuration for the new persistent server
     * @return a CompletableFuture that completes with the created persistent server
     */
    CompletableFuture<PersistentServer> createPersistentServer(CreatePersistentServerRequest request);

    /**
     * Updates an existing persistent server.
     *
     * <p>Set fields of the workflows config are merged into the current config;
     * fields left unset keep their current values.
     *
     * @param id      the unique ID of the persistent server to update
     * @param request the updated configuration (only specified fields will be updated)
     * @return a CompletableFuture that completes with the updated persistent server
     */
    CompletableFuture<PersistentServer> updatePersistentServer(String id, UpdatePersistentServerRequest request);

    /**
     * Replaces the whole configuration of a persistent server.
     *
     * <p>Unset fields get the same defaults as in {@link #createPersistentServer(CreatePersistentServerRequest)}.
     * Creating an inline blueprint is not supported here; reference an existing blueprint in the source instead.
     *
     * @param id      the unique ID of the persistent server to replace
     * @param request the new configuration
     * @return a CompletableFuture that completes with the replaced persistent server
     */
    CompletableFuture<PersistentServer> replacePersistentServer(String id, CreatePersistentServerRequest request);

    /**
     * Deletes a persistent server and its referenced blueprint, if present.
     *
     * @param id the unique ID of the persistent server to delete
     * @return a CompletableFuture that completes when the persistent server and its referenced blueprint are deleted
     */
    CompletableFuture<Void> deletePersistentServer(String id);

    /**
     * Updates persistent server properties by merging with existing properties (deep merge).
     *
     * @param id the unique ID of the persistent server
     * @param properties the properties to merge
     * @return a CompletableFuture that completes with the updated properties
     */
    CompletableFuture<Map<String, Object>> updatePersistentServerProperties(String id, Map<String, Object> properties);

    /**
     * Replaces all persistent server properties with the given properties.
     *
     * @param id the unique ID of the persistent server
     * @param properties the new properties
     * @return a CompletableFuture that completes with the stored properties
     */
    CompletableFuture<Map<String, Object>> setPersistentServerProperties(String id, Map<String, Object> properties);

    /**
     * Updates a single persistent server property by merging it with existing properties.
     *
     * @param id the unique ID of the persistent server
     * @param key the property key to update
     * @param value the property value to set
     * @return a CompletableFuture that completes with the updated properties
     */
    default CompletableFuture<Map<String, Object>> updatePersistentServerProperty(String id, String key, Object value) {
        return updatePersistentServerProperties(id, Map.of(key, value));
    }

    /**
     * Deletes specific property keys from a persistent server.
     *
     * @param id the unique ID of the persistent server
     * @param keys the property keys to delete
     * @return a CompletableFuture that completes with the remaining properties
     */
    CompletableFuture<Map<String, Object>> deletePersistentServerProperties(String id, List<String> keys);

    /**
     * Deletes a single property key from a persistent server.
     *
     * @param id the unique ID of the persistent server
     * @param key the property key to delete
     * @return a CompletableFuture that completes with the remaining properties
     */
    default CompletableFuture<Map<String, Object>> deletePersistentServerProperty(String id, String key) {
        return deletePersistentServerProperties(id, List.of(key));
    }

    /**
     * Runs a manual workflow on the running instance of a persistent server.
     *
     * @param id         the unique ID of the persistent server
     * @param workflowId the workflow to run, for example {@code default/backup}
     * @return a CompletableFuture that completes with the per-server results
     */
    CompletableFuture<WorkflowExecutionResult> executeWorkflow(String id, String workflowId);

    /**
     * Converts a persistent server into a server group.
     *
     * <p>The persistent server must be inactive and have no running server, otherwise this fails with
     * {@code CONFLICT}. The persistent server is deleted; the group gets a new ID and starts inactive.
     *
     * @param id      the unique ID of the persistent server
     * @param hosts   the serverhosts the group may deploy to; an empty list allows every serverhost
     * @param scaling the group's scaling, or null for the defaults of {@code GroupApi.createGroup}
     * @return a CompletableFuture that completes with the created group
     */
    CompletableFuture<Group> convertToGroup(String id, List<DeploymentHost> hosts, @Nullable ScalingConfig scaling);
}
