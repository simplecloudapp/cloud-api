package app.simplecloud.api.group;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Aggregated manual server-start queue state across all server groups.
 */
public final class GroupStartQueue {
    private final int count;
    private final int failedStarts;
    private final List<GroupStartQueueEntry> items;
    private final int queuedStarts;
    private final int totalStarts;

    public GroupStartQueue(
            int count,
            int failedStarts,
            List<GroupStartQueueEntry> items,
            int queuedStarts,
            int totalStarts
    ) {
        this.count = count;
        this.failedStarts = failedStarts;
        this.items = List.copyOf(items);
        this.queuedStarts = queuedStarts;
        this.totalStarts = totalStarts;
    }

    public int getCount() {
        return count;
    }

    public int getFailedStarts() {
        return failedStarts;
    }

    public List<GroupStartQueueEntry> getItems() {
        return items;
    }

    public int getQueuedStarts() {
        return queuedStarts;
    }

    public int getTotalStarts() {
        return totalStarts;
    }

    @Nullable
    public GroupStartQueueEntry findByServerGroupId(String serverGroupId) {
        for (GroupStartQueueEntry item : items) {
            if (serverGroupId.equals(item.getServerGroupId())) {
                return item;
            }
        }
        return null;
    }

    @Nullable
    public GroupStartQueueEntry findByServerGroupName(String serverGroupName) {
        for (GroupStartQueueEntry item : items) {
            if (serverGroupName.equals(item.getServerGroupName())) {
                return item;
            }
        }
        return null;
    }
}
