package app.simplecloud.api.group;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Filters for {@link GroupApi#getAllGroups(GroupQuery)}. A group has to match all filters that are set.
 */
public class GroupQuery {
    @Nullable
    private GroupServerType type;

    private List<String> tags;
    private Boolean active;
    private SourceType sourceType;
    private String blueprintId;
    private Integer limit;
    private Integer offset;
    private String sortBy;
    private String sortOrder;

    public GroupQuery() {}

    /**
     * @return the sort field set with {@link #sortBy(String)}
     * @deprecated the controller does not sort groups, so this value is ignored
     */
    @Deprecated
    @Nullable
    public String getSortBy() {
        return sortBy;
    }

    /**
     * @param sortBy the field to sort by
     * @return this query for chaining
     * @deprecated the controller does not sort groups, so this value is ignored
     */
    @Deprecated
    public GroupQuery sortBy(String sortBy) {
        this.sortBy = sortBy;
        return this;
    }

    /**
     * @return the sort order set with {@link #sortOrder(String)}
     * @deprecated the controller does not sort groups, so this value is ignored
     */
    @Deprecated
    @Nullable
    public String getSortOrder() {
        return sortOrder;
    }

    /**
     * @param sortOrder the sort order
     * @return this query for chaining
     * @deprecated the controller does not sort groups, so this value is ignored
     */
    @Deprecated
    public GroupQuery sortOrder(String sortOrder) {
        this.sortOrder = sortOrder;
        return this;
    }

    @Nullable
    public GroupServerType getType() {
        return type;
    }

    public GroupQuery filterByType(@Nullable GroupServerType type) {
        this.type = type;
        return this;
    }

    /**
     * @return the first tag filter, or null if none is set
     * @deprecated use {@link #getTags()}
     */
    @Deprecated
    @Nullable
    public String getTag() {
        return tags == null || tags.isEmpty() ? null : tags.getFirst();
    }

    @Nullable
    public List<String> getTags() {
        return tags;
    }

    /**
     * Filter by a single tag, replacing any tags set before.
     *
     * @param tag the tag to filter by
     * @return this query for chaining
     */
    public GroupQuery filterByTag(String tag) {
        this.tags = new ArrayList<>(List.of(tag));
        return this;
    }

    /**
     * Filter by one or more tags (matches if any tag matches). Repeated calls add tags.
     *
     * @param tags the tags to filter by
     * @return this query for chaining
     */
    public GroupQuery filterByTags(String... tags) {
        if (this.tags == null) {
            this.tags = new ArrayList<>();
        }
        this.tags.addAll(Arrays.asList(tags));
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
    public SourceType getSourceType() {
        return sourceType;
    }

    public GroupQuery filterBySourceType(@Nullable SourceType sourceType) {
        this.sourceType = sourceType;
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

    /**
     * Limit the number of results.
     *
     * @param limit maximum number of results to return
     * @return this query for chaining
     */
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
        copy.tags = tags == null ? null : new ArrayList<>(tags);
        copy.active = active;
        copy.sourceType = sourceType;
        copy.blueprintId = blueprintId;
        copy.limit = limit;
        copy.offset = offset;
        copy.sortBy = sortBy;
        copy.sortOrder = sortOrder;
        return copy;
    }
}
