package app.simplecloud.api.persistentserver;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Query parameters for filtering and sorting persistent server lists.
 */
public class PersistentServerQuery {
    private List<String> tags;
    private Boolean active;
    private String serverhostId;
    private Integer limit;

    public PersistentServerQuery() {
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
