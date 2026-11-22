package app.simplecloud.api.group;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Manual server-start queue state for one server group.
 */
public record GroupStartQueueEntry(
        int failedStarts,
        int queuedStarts,
        @Nullable String serverGroupId,
        @Nullable String serverGroupName,
        List<GroupStartQueueItem> starts,
        int totalStarts
) {

    public GroupStartQueueEntry {
        starts = List.copyOf(starts);
    }

    /**
     * @deprecated use {@link #failedStarts()}; will be removed in 1.0.0
     */
    @Deprecated(forRemoval = true)
    public int getFailedStarts() {
        return failedStarts;
    }

    /**
     * @deprecated use {@link #queuedStarts()}; will be removed in 1.0.0
     */
    @Deprecated(forRemoval = true)
    public int getQueuedStarts() {
        return queuedStarts;
    }

    /**
     * @deprecated use {@link #serverGroupId()}; will be removed in 1.0.0
     */
    @Deprecated(forRemoval = true)
    @Nullable
    public String getServerGroupId() {
        return serverGroupId;
    }

    /**
     * @deprecated use {@link #serverGroupName()}; will be removed in 1.0.0
     */
    @Deprecated(forRemoval = true)
    @Nullable
    public String getServerGroupName() {
        return serverGroupName;
    }

    /**
     * @deprecated use {@link #starts()}; will be removed in 1.0.0
     */
    @Deprecated(forRemoval = true)
    public List<GroupStartQueueItem> getStarts() {
        return starts;
    }

    /**
     * @deprecated use {@link #totalStarts()}; will be removed in 1.0.0
     */
    @Deprecated(forRemoval = true)
    public int getTotalStarts() {
        return totalStarts;
    }
}
