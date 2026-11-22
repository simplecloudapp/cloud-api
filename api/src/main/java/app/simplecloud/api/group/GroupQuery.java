package app.simplecloud.api.group;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

public class GroupQuery {
    @Nullable private GroupServerType type;

    private String tag;
    private String sortBy;
    private String sortOrder;
    private Integer limit;

    public GroupQuery() {}

    @Nullable public GroupServerType getType() {
        return type;
    }

    public GroupQuery filterByType(@Nullable GroupServerType type) {
        this.type = type;
        return this;
    }

    @Nullable public String getTag() {
        return tag;
    }

    public GroupQuery filterByTag(String tag) {
        this.tag = tag;
        return this;
    }

    /**
     * @deprecated the controller does not support sorting groups, this value is ignored; will be removed in 1.0.0
     */
    @Deprecated(forRemoval = true)
    @ApiStatus.ScheduledForRemoval(inVersion = "1.0.0")
    @Nullable public String getSortBy() {
        return sortBy;
    }

    /**
     * @deprecated the controller does not support sorting groups, this value is ignored; will be removed in 1.0.0
     */
    @Deprecated(forRemoval = true)
    @ApiStatus.ScheduledForRemoval(inVersion = "1.0.0")
    public GroupQuery sortBy(String sortBy) {
        this.sortBy = sortBy;
        return this;
    }

    /**
     * @deprecated the controller does not support sorting groups, this value is ignored; will be removed in 1.0.0
     */
    @Deprecated(forRemoval = true)
    @ApiStatus.ScheduledForRemoval(inVersion = "1.0.0")
    @Nullable public String getSortOrder() {
        return sortOrder;
    }

    /**
     * @deprecated the controller does not support sorting groups, this value is ignored; will be removed in 1.0.0
     */
    @Deprecated(forRemoval = true)
    @ApiStatus.ScheduledForRemoval(inVersion = "1.0.0")
    public GroupQuery sortOrder(String sortOrder) {
        this.sortOrder = sortOrder;
        return this;
    }

    @Nullable public Integer getLimit() {
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
