package app.simplecloud.api.group;

import org.jetbrains.annotations.Nullable;

public class GroupQuery {
    @Nullable
    private GroupServerType type;

    private String tag;
    private Integer limit;

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
    public Integer getLimit() {
        return limit;
    }

    public GroupQuery limit(Integer limit) {
        this.limit = limit;
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
        copy.limit = limit;
        return copy;
    }
}
