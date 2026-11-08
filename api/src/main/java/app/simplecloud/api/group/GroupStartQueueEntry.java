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
        int totalStarts) {

    public GroupStartQueueEntry {
        starts = List.copyOf(starts);
    }

    /** @deprecated use {@link #failedStarts()} */
    @Deprecated
    public int getFailedStarts() {
        return failedStarts;
    }

    /** @deprecated use {@link #queuedStarts()} */
    @Deprecated
    public int getQueuedStarts() {
        return queuedStarts;
    }

    /** @deprecated use {@link #serverGroupId()} */
    @Deprecated
    @Nullable
    public String getServerGroupId() {
        return serverGroupId;
    }

    /** @deprecated use {@link #serverGroupName()} */
    @Deprecated
    @Nullable
    public String getServerGroupName() {
        return serverGroupName;
    }

    /** @deprecated use {@link #starts()} */
    @Deprecated
    public List<GroupStartQueueItem> getStarts() {
        return starts;
    }

    /** @deprecated use {@link #totalStarts()} */
    @Deprecated
    public int getTotalStarts() {
        return totalStarts;
    }
}
