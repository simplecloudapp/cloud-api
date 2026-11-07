package app.simplecloud.api.group;

import org.jetbrains.annotations.Nullable;

/**
 * Filters for {@link GroupApi#getAllGroups(GroupQuery)}. A group has to match all filters that are set.
 */
public class GroupQuery {
    @Nullable
    private GroupServerType type;

    private String tag;
    private Boolean active;
    private String blueprintId;
    private Integer limit;
    private Integer offset;

    public GroupQuery() {}

    @Nullable
    public GroupServerType getType() {
        return type;
    }

    public GroupQuery filterByType(@Nullable GroupServerType type) {
        this.type = type;
        return this;
    }

    @Nullable
    public String getTag() {
        return tag;
    }

    public GroupQuery filterByTag(String tag) {
        this.tag = tag;
        return this;
    }

    @Nullable
    public Boolean getActive() {
        return active;
    }

    public GroupQuery filterByActive(@Nullable Boolean active) {
        this.active = active;
        return this;
    }

    @Nullable
    public String getBlueprintId() {
        return blueprintId;
    }

    /**
     * Filter by the blueprint the group's source references.
     *
     * @param blueprintId the blueprint ID
     * @return this query for chaining
     */
    public GroupQuery filterByBlueprintId(@Nullable String blueprintId) {
        this.blueprintId = blueprintId;
        return this;
    }

    @Nullable
    public Integer getLimit() {
        return limit;
    }

    public GroupQuery limit(Integer limit) {
        this.limit = limit;
        return this;
    }

    @Nullable
    public Integer getOffset() {
        return offset;
    }

    /**
     * Skips the first groups of the result. The controller only accepts an offset together with a limit.
     *
     * @param offset the number of groups to skip
     * @return this query for chaining
     */
    public GroupQuery offset(Integer offset) {
        this.offset = offset;
        return this;
    }

    public static GroupQuery create() {
        return new GroupQuery();
    }

    /**
     * Creates an independent copy of this query.
     *
     * @return a new query with the same filters
     */
    public GroupQuery copy() {
        GroupQuery copy = new GroupQuery();
        copy.type = type;
        copy.tag = tag;
        copy.active = active;
        copy.blueprintId = blueprintId;
        copy.limit = limit;
        copy.offset = offset;
        return copy;
    }
}
