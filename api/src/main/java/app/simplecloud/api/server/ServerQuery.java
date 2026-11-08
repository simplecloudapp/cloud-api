package app.simplecloud.api.server;

import app.simplecloud.api.group.GroupServerType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Filters and sorting for {@link ServerApi#getAllServers(ServerQuery)}.
 *
 * <p>Filter methods that take several values match any of them, and repeated calls add to the earlier values.
 * Filter values must not be null.
 *
 * <p>The server group type, name and tag filters also match persistent servers with that type, name or tag.
 */
public class ServerQuery {
    private List<String> serverGroupIds;
    private List<ServerState> states;
    private List<String> serverhostIds;
    private List<String> persistentServerIds;
    private List<GroupServerType> serverGroupTypes;
    private List<String> serverGroupNames;
    private List<String> serverGroupTags;
    private List<Integer> numericalIds;
    private SortField sortBy;
    private SortOrder sortOrder;
    private Integer limit;
    private Integer offset;

    public ServerQuery() {}

    /**
     * Creates an independent copy of this query.
     *
     * @return a new query with the same filters and sorting
     */
    public ServerQuery copy() {
        ServerQuery copy = new ServerQuery();
        copy.serverGroupIds = copyList(serverGroupIds);
        copy.states = copyList(states);
        copy.serverhostIds = copyList(serverhostIds);
        copy.persistentServerIds = copyList(persistentServerIds);
        copy.serverGroupTypes = copyList(serverGroupTypes);
        copy.serverGroupNames = copyList(serverGroupNames);
        copy.serverGroupTags = copyList(serverGroupTags);
        copy.numericalIds = copyList(numericalIds);
        copy.sortBy = sortBy;
        copy.sortOrder = sortOrder;
        copy.limit = limit;
        copy.offset = offset;
        return copy;
    }

    private static <T> List<T> copyList(@Nullable List<T> list) {
        return list == null ? null : new ArrayList<>(list);
    }

    @SafeVarargs
    private static <T> List<T> addAll(@Nullable List<T> target, String filter, T... values) {
        List<T> result = target == null ? new ArrayList<>() : target;
        for (T value : values) {
            result.add(Objects.requireNonNull(value, filter + " must not contain null"));
        }
        return result;
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
        this.serverGroupIds = addAll(this.serverGroupIds, "serverGroupIds", serverGroupIds);
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
     * @throws IllegalArgumentException if a state is {@link ServerState#UNKNOWN_STATE}
     */
    public ServerQuery filterByState(ServerState... states) {
        for (ServerState state : states) {
            if (state == ServerState.UNKNOWN_STATE) {
                throw new IllegalArgumentException("UNKNOWN_STATE cannot be used as a filter");
            }
        }
        this.states = addAll(this.states, "states", states);
        return this;
    }

    @Nullable
    public List<String> getServerhostIds() {
        return serverhostIds;
    }

    /**
     * Filter by one or more serverhost IDs.
     *
     * @param serverhostIds the serverhost IDs to filter by
     * @return this query for chaining
     */
    public ServerQuery filterByServerhostId(String... serverhostIds) {
        this.serverhostIds = addAll(this.serverhostIds, "serverhostIds", serverhostIds);
        return this;
    }

    @Nullable
    public List<String> getPersistentServerIds() {
        return persistentServerIds;
    }

    /**
     * Filter by one or more persistent server IDs.
     *
     * @param persistentServerIds the persistent server IDs to filter by
     * @return this query for chaining
     */
    public ServerQuery filterByPersistentServerId(String... persistentServerIds) {
        this.persistentServerIds = addAll(this.persistentServerIds, "persistentServerIds", persistentServerIds);
        return this;
    }

    @Nullable
    public List<GroupServerType> getServerGroupTypes() {
        return serverGroupTypes;
    }

    /**
     * Filter by one or more server group types. Persistent servers of these types match too.
     *
     * @param types the server group types to filter by (e.g., SERVER, PROXY)
     * @return this query for chaining
     * @throws IllegalArgumentException if a type is {@link GroupServerType#UNKNOWN_SERVER}
     */
    public ServerQuery filterByServerGroupType(GroupServerType... types) {
        for (GroupServerType type : types) {
            if (type == GroupServerType.UNKNOWN_SERVER) {
                throw new IllegalArgumentException("UNKNOWN_SERVER cannot be used as a filter");
            }
        }
        this.serverGroupTypes = addAll(this.serverGroupTypes, "types", types);
        return this;
    }

    @Nullable
    public List<String> getServerGroupNames() {
        return serverGroupNames;
    }

    /**
     * Filter by one or more server group names. Persistent servers with one of these names match too.
     *
     * @param names the server group names to filter by
     * @return this query for chaining
     */
    public ServerQuery filterByServerGroupName(String... names) {
        this.serverGroupNames = addAll(this.serverGroupNames, "names", names);
        return this;
    }

    @Nullable
    public List<String> getServerGroupTags() {
        return serverGroupTags;
    }

    /**
     * Filter by one or more server group tags (matches if any tag matches). Persistent servers with one
     * of these tags match too.
     *
     * @param tags the tags to filter by
     * @return this query for chaining
     */
    public ServerQuery filterByServerGroupTags(String... tags) {
        this.serverGroupTags = addAll(this.serverGroupTags, "tags", tags);
        return this;
    }

    @Nullable
    public List<Integer> getNumericalIds() {
        return numericalIds;
    }

    /**
     * Filter by one or more numerical IDs.
     *
     * @param numericalIds the numerical IDs to filter by (e.g., 1, 5, 10)
     * @return this query for chaining
     */
    public ServerQuery filterByNumericalId(Integer... numericalIds) {
        this.numericalIds = addAll(this.numericalIds, "numericalIds", numericalIds);
        return this;
    }

    @Nullable
    public SortField getSortBy() {
        return sortBy;
    }

    /**
     * Set the field to sort by.
     *
     * @param sortBy the field to sort by
     * @return this query for chaining
     */
    public ServerQuery sortBy(SortField sortBy) {
        this.sortBy = sortBy;
        return this;
    }

    @Nullable
    public SortOrder getSortOrder() {
        return sortOrder;
    }

    /**
     * Set the sort order. The controller sorts ascending if none is set.
     *
     * @param sortOrder the sort order
     * @return this query for chaining
     */
    public ServerQuery sortOrder(SortOrder sortOrder) {
        this.sortOrder = sortOrder;
        return this;
    }

    @Nullable
    public Integer getLimit() {
        return limit;
    }

    /**
     * Limit the number of results. The controller accepts 1 to 500.
     *
     * @param limit maximum number of results to return
     * @return this query for chaining
     */
    public ServerQuery limit(int limit) {
        this.limit = limit;
        return this;
    }

    @Nullable
    public Integer getOffset() {
        return offset;
    }

    /**
     * Skips the first servers of the result. The controller only accepts an offset together with a limit.
     *
     * @param offset the number of servers to skip
     * @return this query for chaining
     */
    public ServerQuery offset(int offset) {
        this.offset = offset;
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

    public enum SortField {
        CREATED_AT("created_at"),
        UPDATED_AT("updated_at"),
        NUMERICAL_ID("numerical_id"),
        STATE("state");

        private final String value;

        SortField(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    public enum SortOrder {
        ASC("asc"),
        DESC("desc");

        private final String value;

        SortOrder(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }
}
