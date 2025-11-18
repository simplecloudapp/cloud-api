package app.simplecloud.api.group;

import org.jetbrains.annotations.Nullable;

public class GroupQuery {
    @Nullable
    private GroupServerType type;
    private String tag;
    private String sortBy;
    private String sortOrder;
    private Integer limit;

    public GroupQuery() {
    }

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
    public String getSortBy() {
        return sortBy;
    }

    public GroupQuery sortBy(String sortBy) {
        this.sortBy = sortBy;
        return this;
    }

    @Nullable
    public String getSortOrder() {
        return sortOrder;
    }

    public GroupQuery sortOrder(String sortOrder) {
        this.sortOrder = sortOrder;
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
}


