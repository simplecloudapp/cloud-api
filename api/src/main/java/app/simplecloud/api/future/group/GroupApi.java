package app.simplecloud.api.future.group;

import app.simplecloud.api.GroupServerType;

import java.util.concurrent.CompletableFuture;

public interface GroupApi {

    /**
     * Gets a group by its name.
     *
     * @param name the name of the group
     * @return a future that completes with the group when it is found
     */
    CompletableFuture<Group> getGroupByName(String name);

    /**
     * Gets a group by its type.
     *
     * @param type the type of the group
     * @return a future that completes with the group when it is found
     */
    CompletableFuture<Group> getGroupByType(GroupServerType type);

    /**
     * Gets all groups.
     *
     * @return a future that completes with a list of all groups
     */
    CompletableFuture<Group[]> getAllGroups();

    /**
     * Creates a new group.
     *
     * @return a future that completes with the created group when the creation is complete
     */
    CompletableFuture<Group> createGroup();

    /**
     * Deletes a group.
     *
     * @param name the name of the group
     * @return a future that completes when the group is deleted
     */
    CompletableFuture<Void> deleteGroup(String name);

}
