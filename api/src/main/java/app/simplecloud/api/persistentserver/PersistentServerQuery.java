package app.simplecloud.api.persistentserver;

import app.simplecloud.api.group.GroupServerType;
import app.simplecloud.api.group.SourceType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Filters for {@link PersistentServerApi#getAllPersistentServers(PersistentServerQuery)}.
 */
public class PersistentServerQuery {
    private List<String> tags;
    private Boolean active;
    private String serverhostId;
    private GroupServerType type;
    private SourceType sourceType;
    private String blueprintId;
    private Integer limit;
    private Integer offset;

    public PersistentServerQuery() {}

    /**
     * Creates an independent copy of this query.
     *
     * @return a new query with the same filters
     */
    public PersistentServerQuery copy() {
        PersistentServerQuery copy = new PersistentServerQuery();
        copy.tags = tags == null ? null : new ArrayList<>(tags);
        copy.active = active;
        copy.serverhostId = serverhostId;
        copy.type = type;
        copy.sourceType = sourceType;
        copy.blueprintId = blueprintId;
        copy.limit = limit;
        copy.offset = offset;
        return copy;
    }

    @Nullable
    public List<String> getTags() {
        return tags;
    }

    /**
     * Filter by one or more tags (matches if any tag matches).
     *
     * @param tags the tags to filter by
     * @return this query for chaining
     */
    public PersistentServerQuery filterByTags(String... tags) {
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

    /**
     * Filter by active status.
     *
     * @param active whether to filter for active persistent servers
     * @return this query for chaining
     */
    public PersistentServerQuery filterByActive(boolean active) {
        this.active = active;
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
    public PersistentServerQuery filterByServerhostId(String serverhostId) {
        this.serverhostId = serverhostId;
        return this;
    }

    @Nullable
    public GroupServerType getType() {
        return type;
    }

    /**
     * Filter by server type.
     *
     * @param type the server type to filter by
     * @return this query for chaining
     */
    public PersistentServerQuery filterByType(GroupServerType type) {
        this.type = type;
        return this;
    }

    @Nullable
    public SourceType getSourceType() {
        return sourceType;
    }

    /**
     * Filter by source type.
     *
     * @param sourceType the source type to filter by
     * @return this query for chaining
     */
    public PersistentServerQuery filterBySourceType(SourceType sourceType) {
        this.sourceType = sourceType;
        return this;
    }

    @Nullable
    public String getBlueprintId() {
        return blueprintId;
    }

    /**
     * Filter by the blueprint the persistent server's source references.
     *
     * @param blueprintId the blueprint ID
     * @return this query for chaining
     */
    public PersistentServerQuery filterByBlueprintId(String blueprintId) {
        this.blueprintId = blueprintId;
        return this;
    }

    @Nullable
    public Integer getOffset() {
        return offset;
    }

    /**
     * Skips the first persistent servers of the result. The controller only accepts an offset together
     * with a limit.
     *
     * @param offset the number of persistent servers to skip
     * @return this query for chaining
     */
    public PersistentServerQuery offset(int offset) {
        this.offset = offset;
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
    public PersistentServerQuery limit(int limit) {
        this.limit = limit;
        return this;
    }

    /**
     * Creates a new empty persistent server query.
     *
     * @return a new PersistentServerQuery instance
     */
    public static PersistentServerQuery create() {
        return new PersistentServerQuery();
    }
}
