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
     * <p>Only active groups with {@link ScalingMode#SERVERS} support queued starts; other groups fail with
     * {@link CloudApiException.Reason#INVALID_REQUEST}. Accepting the request does not guarantee that the
     * server starts: a start that does not succeed within about 5 minutes is marked
     * {@link GroupStartQueueItemStatus#FAILED}, see {@link #getServerStartQueue()}.
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
     * @see #requestServerStart(String)
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
     * Sends a stop command to all running servers of a server group.
     *
     * @param serverGroupId the server group ID
     * @return a CompletableFuture that completes with the number of servers that were told to stop
     */
    CompletableFuture<Integer> stopAllServers(String serverGroupId);

    /**
     * Sends a stop command to all running servers of the provided group.
     *
     * @param group the target group
     * @return a CompletableFuture that completes with the number of servers that were told to stop
     */
    default CompletableFuture<Integer> stopAllServers(Group group) {
        return stopAllServers(group.getServerGroupId());
    }

    /**
     * Starts a rolling restart, which restarts the servers of a group one by one while keeping it available.
     *
     * <p>Only groups with {@link ScalingMode#SLOTS} support rolling restarts; other groups fail with
     * {@link CloudApiException.Reason#INVALID_REQUEST}.
     *
     * @param serverGroupId the server group ID
     * @return a CompletableFuture that completes when the rolling restart was started
     */
    CompletableFuture<Void> startRollingRestart(String serverGroupId);

    /**
     * Starts a rolling restart for the provided group.
     *
     * @param group the target group
     * @return a CompletableFuture that completes when the rolling restart was started
     * @see #startRollingRestart(String)
     */
    default CompletableFuture<Void> startRollingRestart(Group group) {
        return startRollingRestart(group.getServerGroupId());
    }

    /**
     * Retrieves whether a rolling restart is running for a server group.
     *
     * @param serverGroupId the server group ID
     * @return a CompletableFuture that completes with the rolling restart status
     */
    CompletableFuture<RollingRestartStatus> getRollingRestartStatus(String serverGroupId);

    /**
     * Retrieves whether a rolling restart is running for the provided group.
     *
     * @param group the target group
     * @return a CompletableFuture that completes with the rolling restart status
     */
    default CompletableFuture<RollingRestartStatus> getRollingRestartStatus(Group group) {
        return getRollingRestartStatus(group.getServerGroupId());
    }

    /**
     * Cancels the running rolling restart of a server group.
     *
     * @param serverGroupId the server group ID
     * @return a CompletableFuture that completes when the rolling restart was cancelled
     */
    CompletableFuture<Void> cancelRollingRestart(String serverGroupId);

    /**
     * Cancels the running rolling restart of the provided group.
     *
     * @param group the target group
     * @return a CompletableFuture that completes when the rolling restart was cancelled
     */
    default CompletableFuture<Void> cancelRollingRestart(Group group) {
        return cancelRollingRestart(group.getServerGroupId());
    }

    /**
     * Runs a manual workflow configured on the group for all eligible running servers of the group.
     *
     * @param serverGroupId the server group ID
     * @param workflowId the ID of the manual workflow
     * @return a CompletableFuture that completes with the result per server
     */
    CompletableFuture<WorkflowExecutionResult> executeWorkflow(String serverGroupId, String workflowId);

    /**
     * Runs a manual workflow configured on the group for all eligible running servers of the group.
     *
     * @param group the target group
     * @param workflowId the ID of the manual workflow
     * @return a CompletableFuture that completes with the result per server
     */
    default CompletableFuture<WorkflowExecutionResult> executeWorkflow(Group group, String workflowId) {
        return executeWorkflow(group.getServerGroupId(), workflowId);
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
     * <p>An ID that isn't a UUID returns an empty Optional without asking the controller.
     *
     * @param id the unique ID of the server group
     * @return a CompletableFuture that completes with the group, or an empty Optional if it does not exist
     */
    CompletableFuture<Optional<Group>> findGroupById(String id);

    /**
     * Retrieves all server groups, optionally filtered by a query.
     *
     * @param query optional filters and paging, see {@link GroupQuery}
     * @return a CompletableFuture that completes with a list of matching groups
     */
    CompletableFuture<List<Group>> getAllGroups(@Nullable GroupQuery query);

    /**
     * Retrieves all server groups without any filtering.
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
     * <p>Only the top-level fields set in the request are updated. Nested configurations
     * ({@code scaling}, {@code deployment}, {@code workflows}) replace the
     * stored configuration as a whole, so they must be complete; no create defaults are applied.
     * A {@code scaling} config without scaling mode, max servers or player threshold fails with an
     * {@link IllegalArgumentException}.
     *
     * @param id      the unique ID of the server group to update
     * @param request the updated configuration
     * @return a CompletableFuture that completes with the updated group
     */
    CompletableFuture<Group> updateGroup(String id, UpdateGroupRequest request);

    /**
     * Deletes a server group and its referenced blueprint, if present.
     *
     * <p>The controller refuses to delete a group that still has servers; the future then fails with
     * {@link CloudApiException.Reason#CONFLICT}. This includes servers that are still stopping, so after
     * {@link #stopAllServers(String)} retry once the stopped servers are gone.
     *
     * @param id the unique ID of the server group to delete
     * @return a CompletableFuture that completes when the group and its referenced blueprint are deleted
     */
    CompletableFuture<Void> deleteGroup(String id);

    /**
     * Converts a server group into a persistent server.
     *
     * <p>The group must be inactive and have no servers, queued starts or running rolling restart; otherwise
     * the future fails with {@link CloudApiException.Reason#CONFLICT}. The persistent server gets a new ID
     * and starts inactive. The group is deleted, its scaling and deployment settings are dropped, and world
     * files are not migrated.
     *
     * @param serverGroupId the ID of the group to convert
     * @param serverhostId the ID of the serverhost the persistent server runs on
     * @return a CompletableFuture that completes with the new persistent server
     */
    CompletableFuture<PersistentServer> convertToPersistentServer(String serverGroupId, String serverhostId);

    /**
     * Updates group properties by merging with existing properties (deep merge).
     *
     * @param id the unique ID of the server group
     * @param properties the properties to merge
     * @return a CompletableFuture that completes with the updated properties
     */
    CompletableFuture<Map<String, Object>> updateGroupProperties(String id, Map<String, Object> properties);

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
}
