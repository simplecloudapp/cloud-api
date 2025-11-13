package app.simplecloud.api.future.group;

import java.util.concurrent.CompletableFuture;

public interface GroupApi {

    /**
     * Gets a group by its name.
     *
     * @param name the name of the group
     * @return a future completing with the group or failing if not found
     */
    CompletableFuture<Group> getGroupByName(String name);

    /**
     * Gets a group by its ID.
     *
     * @param id the server group ID
     * @return a future completing with the group
     */
    CompletableFuture<Group> getGroupById(String id);

    /**
     * Gets all groups of the network.
     *
     * @return a future completing with an array of all groups
     */
    CompletableFuture<Group[]> getAllGroups();

    /**
     * Creates a new group.
     *
     * @param request the creation request
     * @return a future completing with the created group
     */
    CompletableFuture<Group> createGroup();

    /**
     * Updates an existing group.
     *
     * @param id the group ID
     * @param request update request
     * @return a future completing with the updated group
     */
    CompletableFuture<Group> updateGroup(String id);

    /**
     * Deletes a group.
     *
     * @param id the group ID
     * @return a future completing when deletion succeeded
     */
    CompletableFuture<Void> deleteGroup(String id);
}