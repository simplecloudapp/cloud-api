package app.simplecloud.api.server;

import app.simplecloud.api.group.GroupServerType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Query parameters for filtering and sorting server lists.
 * 
 * <p>Use the builder methods to construct queries. Example:
 * <pre>
 * ServerQuery query = ServerQuery.create()
 *     .filterByServerGroupId("group-123")
 *     .filterByState(ServerState.RUNNING, ServerState.STARTING)
 *     .sortBy("created_at")
 *     .sortOrder("desc");
 * </pre>
 */
public class ServerQuery {
    private List<String> serverGroupIds;
    private List<ServerState> states;
    private String serverhostId;
    private String persistentServerId;
    private List<GroupServerType> serverGroupTypes;
    private List<String> serverGroupNames;
    private List<String> serverGroupTags;
    private String sortBy;
    private String sortOrder;

    public ServerQuery() {
    }

    @Nullable
    public List<String> getServerGroupIds() {
        return serverGroupIds;
    }

    /**
     * Filter by one or more server group IDs.
     * 
     * @param serverGroupIds the server group IDs to filter by
     * @return this query for chaining
     */
    public ServerQuery filterByServerGroupId(String... serverGroupIds) {
        if (this.serverGroupIds == null) {
            this.serverGroupIds = new ArrayList<>();
        }
        this.serverGroupIds.addAll(Arrays.asList(serverGroupIds));
        return this;
    }

    @Nullable
    public List<ServerState> getStates() {
        return states;
    }

    /**
     * Filter by one or more server states.
     * 
     * @param states the states to filter by (e.g., RUNNING, STARTING)
     * @return this query for chaining
     */
    public ServerQuery filterByState(ServerState... states) {
        if (this.states == null) {
            this.states = new ArrayList<>();
        }
        this.states.addAll(Arrays.asList(states));
        return this;
    }

    @Nullable
    public String getServerhostId() {
        return serverhostId;
    }

    /**
     * Filter by serverhost ID.
     * 
     * @param serverhostId the serverhost ID to filter by
     * @return this query for chaining
     */
    public ServerQuery filterByServerhostId(String serverhostId) {
        this.serverhostId = serverhostId;
        return this;
    }

    @Nullable
    public String getPersistentServerId() {
        return persistentServerId;
    }

    /**
     * Filter by persistent server ID.
     * 
     * @param persistentServerId the persistent server ID to filter by
     * @return this query for chaining
     */
    public ServerQuery filterByPersistentServerId(String persistentServerId) {
        this.persistentServerId = persistentServerId;
        return this;
    }

    @Nullable
    public List<GroupServerType> getServerGroupTypes() {
        return serverGroupTypes;
    }

    /**
     * Filter by one or more server group types.
     * 
     * @param types the server group types to filter by (e.g., SERVER, PROXY)
     * @return this query for chaining
     */
    public ServerQuery filterByServerGroupType(GroupServerType... types) {
        if (this.serverGroupTypes == null) {
            this.serverGroupTypes = new ArrayList<>();
        }
        this.serverGroupTypes.addAll(Arrays.asList(types));
        return this;
    }

    @Nullable
    public List<String> getServerGroupNames() {
        return serverGroupNames;
    }

    /**
     * Filter by one or more server group names.
     * 
     * @param names the server group names to filter by
     * @return this query for chaining
     */
    public ServerQuery filterByServerGroupName(String... names) {
        if (this.serverGroupNames == null) {
            this.serverGroupNames = new ArrayList<>();
        }
        this.serverGroupNames.addAll(Arrays.asList(names));
        return this;
    }

    @Nullable
    public List<String> getServerGroupTags() {
        return serverGroupTags;
    }

    /**
     * Filter by one or more server group tags (matches if any tag matches).
     * 
     * @param tags the tags to filter by
     * @return this query for chaining
     */
    public ServerQuery filterByServerGroupTags(String... tags) {
        if (this.serverGroupTags == null) {
            this.serverGroupTags = new ArrayList<>();
        }
        this.serverGroupTags.addAll(Arrays.asList(tags));
        return this;
    }

    @Nullable
    public String getSortBy() {
        return sortBy;
    }

    /**
     * Set the field to sort by.
     * 
     * @param sortBy the field name (e.g., "created_at", "updated_at", "numerical_id", "state")
     * @return this query for chaining
     */
    public ServerQuery sortBy(String sortBy) {
        this.sortBy = sortBy;
        return this;
    }

    @Nullable
    public String getSortOrder() {
        return sortOrder;
    }

    /**
     * Set the sort order.
     * 
     * @param sortOrder "asc" for ascending or "desc" for descending
     * @return this query for chaining
     */
    public ServerQuery sortOrder(String sortOrder) {
        this.sortOrder = sortOrder;
        return this;
    }

    /**
     * Creates a new empty server query.
     * 
     * @return a new ServerQuery instance
     */
    public static ServerQuery create() {
        return new ServerQuery();
    }
}

