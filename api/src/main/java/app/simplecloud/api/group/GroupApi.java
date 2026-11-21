package app.simplecloud.api.group;

import app.simplecloud.api.CloudApiException;
import app.simplecloud.api.persistentserver.PersistentServer;
import app.simplecloud.api.workflow.WorkflowExecutionResult;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * API for managing server groups.
 *
 * <p>Server groups define templates for server instances, including memory limits,
 * scaling configuration, deployment strategy, and source configuration.
 */
public interface GroupApi {

    /**
     * Retrieves the current server-start queue for all server groups.
     *
     * @return a CompletableFuture that completes with the aggregated queue state
     */
    CompletableFuture<GroupStartQueue> getServerStartQueue();

    /**
     * Requests one server start for a server group.
     *
     * @param serverGroupId the server group ID
     * @return a CompletableFuture that completes when the start request was accepted
     */
    CompletableFuture<Void> requestServerStart(String serverGroupId);

    /**
     * Requests one server start for the provided group.
     *
     * @param group the target group
     * @return a CompletableFuture that completes when the start request was accepted
     */
    default CompletableFuture<Void> requestServerStart(Group group) {
        return requestServerStart(group.getServerGroupId());
    }

    /**
     * Clears all queued starts for one server group.
     *
     * @param serverGroupId the server group ID
     * @return a CompletableFuture that completes when the queue was cleared
     */
    CompletableFuture<Void> clearServerStartQueue(String serverGroupId);

    /**
     * Clears all queued starts for the provided group.
     *
     * @param group the target group
     * @return a CompletableFuture that completes when the queue was cleared
     */
    default CompletableFuture<Void> clearServerStartQueue(Group group) {
        return clearServerStartQueue(group.getServerGroupId());
    }

    /**
     * Retrieves a server group by its name.
     *
     * @param name the name of the server group
     * @return a CompletableFuture that completes with the group, or fails with a
     * {@link CloudApiException} ({@link CloudApiException.Reason#NOT_FOUND}) if it does not exist
     */
    CompletableFuture<Group> getGroupByName(String name);

    /**
     * Finds a server group by its name.
     *
     * @param name the name of the server group
     * @return a CompletableFuture that completes with the group, or an empty Optional if it does not exist
     */
    CompletableFuture<Optional<Group>> findGroupByName(String name);

    /**
     * Retrieves a server group by its unique identifier.
     *
     * @param id the unique ID of the server group
     * @return a CompletableFuture that completes with the group, or fails with a
     * {@link CloudApiException} ({@link CloudApiException.Reason#NOT_FOUND}) if it does not exist
     */
    CompletableFuture<Group> getGroupById(String id);

    /**
     * Finds a server group by its unique identifier.
     *
     * @param id the unique ID of the server group
     * @return a CompletableFuture that completes with the group, or an empty Optional if it does not exist
     */
    CompletableFuture<Optional<Group>> findGroupById(String id);

    /**
     * Retrieves all server groups, optionally filtered by a query.
     *
     * @param query optional query parameters to filter results (type, tags, sorting, limit)
     * @return a CompletableFuture that completes with a list of matching groups
     */
    CompletableFuture<List<Group>> getAllGroups(@Nullable GroupQuery query);

    /**
     * Retrieves all server groups without any filtering.
     * <p>
     * This is a convenience method that calls {@link #getAllGroups(GroupQuery)} with a null query,
     * returning all available groups without any filters applied.
     *
     * @return a CompletableFuture that completes with a list of all groups
     */
    default CompletableFuture<List<Group>> getAllGroups() {
        return getAllGroups(null);
    }

    /**
     * Creates a new server group.
     *
     * @param request the configuration for the new server group
     * @return a CompletableFuture that completes with the created group
     */
    CompletableFuture<Group> createGroup(CreateGroupRequest request);

    /**
     * Updates an existing server group.
     *
     * <p>Set fields of the deployment, scaling and workflows configs are merged into the current configs;
     * fields left unset keep their current values.
     *
     * @param id      the unique ID of the server group to update
     * @param request the updated configuration (only specified fields will be updated)
     * @return a CompletableFuture that completes with the updated group
     */
    CompletableFuture<Group> updateGroup(String id, UpdateGroupRequest request);

    /**
     * Replaces the whole configuration of a server group.
     *
     * <p>Unset fields get the same defaults as in {@link #createGroup(CreateGroupRequest)}.
     * Creating an inline blueprint is not supported here; reference an existing blueprint in the source instead.
     *
     * @param id      the unique ID of the server group to replace
     * @param request the new configuration
     * @return a CompletableFuture that completes with the replaced group
     */
    CompletableFuture<Group> replaceGroup(String id, CreateGroupRequest request);

    /**
     * Deletes a server group and its referenced blueprint, if present.
     *
     * <p>Note: The controller rejects the deletion with {@code CONFLICT} while the group still has active servers.
     *
     * @param id the unique ID of the server group to delete
     * @return a CompletableFuture that completes when the group and its referenced blueprint are deleted
     */
    CompletableFuture<Void> deleteGroup(String id);

    /**
     * Updates group properties by merging with existing properties (deep merge).
     *
     * @param id the unique ID of the server group
     * @param properties the properties to merge
     * @return a CompletableFuture that completes with the updated properties
     */
    CompletableFuture<Map<String, Object>> updateGroupProperties(String id, Map<String, Object> properties);

    /**
     * Replaces all group properties with the given properties.
     *
     * @param id the unique ID of the server group
     * @param properties the new properties
     * @return a CompletableFuture that completes with the stored properties
     */
    CompletableFuture<Map<String, Object>> setGroupProperties(String id, Map<String, Object> properties);

    /**
     * Updates a single group property by merging it with existing properties.
     *
     * @param id the unique ID of the server group
     * @param key the property key to update
     * @param value the property value to set
     * @return a CompletableFuture that completes with the updated properties
     */
    default CompletableFuture<Map<String, Object>> updateGroupProperty(String id, String key, Object value) {
        return updateGroupProperties(id, Map.of(key, value));
    }

    /**
     * Deletes specific property keys from a server group.
     *
     * @param id the unique ID of the server group
     * @param keys the property keys to delete
     * @return a CompletableFuture that completes with the remaining properties
     */
    CompletableFuture<Map<String, Object>> deleteGroupProperties(String id, List<String> keys);

    /**
     * Deletes a single property key from a server group.
     *
     * @param id the unique ID of the server group
     * @param key the property key to delete
     * @return a CompletableFuture that completes with the remaining properties
     */
    default CompletableFuture<Map<String, Object>> deleteGroupProperty(String id, String key) {
        return deleteGroupProperties(id, List.of(key));
    }

    /**
     * Starts a rolling restart, which replaces the group's servers one after another.
     *
     * <p>Only groups with scaling mode {@code SLOTS} support rolling restarts; others fail with {@code INVALID_REQUEST}.
     *
     * @param id the unique ID of the server group
     * @return a CompletableFuture that completes when the controller accepted the rolling restart
     */
    CompletableFuture<Void> startRollingRestart(String id);

    /**
     * Retrieves whether a rolling restart is running for a server group.
     *
     * @param id the unique ID of the server group
     * @return a CompletableFuture that completes with the rolling restart state
     */
    CompletableFuture<RollingRestartStatus> getRollingRestartStatus(String id);

    /**
     * Cancels the running rolling restart of a server group.
     *
     * @param id the unique ID of the server group
     * @return a CompletableFuture that completes when the rolling restart was cancelled
     */
    CompletableFuture<Void> cancelRollingRestart(String id);

    /**
     * Stops all running servers of a server group.
     *
     * <p>The group's scaling may start new servers afterwards; deactivate the group to prevent that.
     *
     * @param id the unique ID of the server group
     * @return a CompletableFuture that completes with the number of servers a stop was sent to
     */
    CompletableFuture<Integer> stopAllServers(String id);

    /**
     * Runs a manual workflow on every server of a server group.
     *
     * @param id         the unique ID of the server group
     * @param workflowId the workflow to run, for example {@code default/backup}
     * @return a CompletableFuture that completes with the per-server results
     */
    CompletableFuture<WorkflowExecutionResult> executeWorkflow(String id, String workflowId);

    /**
     * Converts a server group into a persistent server on the given serverhost.
     *
     * <p>The group must be inactive and have no servers, no queued starts and no running rolling restart,
     * otherwise this fails with {@code CONFLICT}. The group is deleted; the persistent server gets a new ID,
     * starts inactive and does not keep the group's scaling and deployment.
     *
     * @param id           the unique ID of the server group
     * @param serverhostId the serverhost the persistent server runs on
     * @return a CompletableFuture that completes with the created persistent server
     */
    CompletableFuture<PersistentServer> convertToPersistentServer(String id, String serverhostId);
}
