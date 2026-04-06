package app.simplecloud.api.group;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Manual server-start queue state for one server group.
 */
public final class GroupStartQueueEntry {
    private final int failedStarts;
    private final int queuedStarts;
    @Nullable
    private final String serverGroupId;
    @Nullable
    private final String serverGroupName;
    private final List<GroupStartQueueItem> starts;
    private final int totalStarts;

    public GroupStartQueueEntry(
            int failedStarts,
            int queuedStarts,
            @Nullable String serverGroupId,
            @Nullable String serverGroupName,
            List<GroupStartQueueItem> starts,
            int totalStarts
    ) {
        this.failedStarts = failedStarts;
        this.queuedStarts = queuedStarts;
        this.serverGroupId = serverGroupId;
        this.serverGroupName = serverGroupName;
        this.starts = List.copyOf(starts);
        this.totalStarts = totalStarts;
    }

    public int getFailedStarts() {
        return failedStarts;
    }

    public int getQueuedStarts() {
        return queuedStarts;
    }

    @Nullable
    public String getServerGroupId() {
        return serverGroupId;
    }

    @Nullable
    public String getServerGroupName() {
        return serverGroupName;
    }

    public List<GroupStartQueueItem> getStarts() {
        return starts;
    }

    public int getTotalStarts() {
        return totalStarts;
    }
}
