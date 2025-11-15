package app.simplecloud.api.group;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * API for managing server groups.
 * 
 * <p>Server groups define templates for server instances, including memory limits,
 * scaling configuration, deployment strategy, and source configuration.
 */
public interface GroupApi {

    /**
     * Retrieves a server group by its name.
     * 
     * @param name the name of the server group
     * @return a CompletableFuture that completes with the group, or fails if not found
     */
    CompletableFuture<Group> getGroupByName(String name);

    /**
     * Retrieves a server group by its unique identifier.
     * 
     * @param id the unique ID of the server group
     * @return a CompletableFuture that completes with the group, or fails if not found
     */
    CompletableFuture<Group> getGroupById(String id);

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
     * @param id the unique ID of the server group to update
     * @param request the updated configuration (only specified fields will be updated)
     * @return a CompletableFuture that completes with the updated group
     */
    CompletableFuture<Group> updateGroup(String id, UpdateGroupRequest request);

    /**
     * Deletes a server group.
     * 
     * <p>Note: This will not stop running servers, but prevents new servers from being created.
     * 
     * @param id the unique ID of the server group to delete
     * @return a CompletableFuture that completes when the group is deleted
     */
    CompletableFuture<Void> deleteGroup(String id);
}

