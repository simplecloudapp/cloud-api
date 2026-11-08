package app.simplecloud.api.group;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Manual server-start queue state across all server groups.
 */
public record GroupStartQueue(
        int count, int failedStarts, List<GroupStartQueueEntry> items, int queuedStarts, int totalStarts) {
    public GroupStartQueue {
        items = List.copyOf(items);
    }

    /** @deprecated use {@link #count()} */
    @Deprecated
    public int getCount() {
        return count;
    }

    /** @deprecated use {@link #failedStarts()} */
    @Deprecated
    public int getFailedStarts() {
        return failedStarts;
    }

    /** @deprecated use {@link #items()} */
    @Deprecated
    public List<GroupStartQueueEntry> getItems() {
        return items;
    }

    /** @deprecated use {@link #queuedStarts()} */
    @Deprecated
    public int getQueuedStarts() {
        return queuedStarts;
    }

    /** @deprecated use {@link #totalStarts()} */
    @Deprecated
    public int getTotalStarts() {
        return totalStarts;
    }

    /**
     * @return the queue entry of the group with this ID, or null if the group has no queued starts
     */
    @Nullable
    public GroupStartQueueEntry findByServerGroupId(String serverGroupId) {
        for (GroupStartQueueEntry item : items) {
            if (serverGroupId.equals(item.serverGroupId())) {
                return item;
            }
        }
        return null;
    }

    /**
     * @return the queue entry of the group with this name, or null if the group has no queued starts
     */
    @Nullable
    public GroupStartQueueEntry findByServerGroupName(String serverGroupName) {
        for (GroupStartQueueEntry item : items) {
            if (serverGroupName.equals(item.serverGroupName())) {
                return item;
            }
        }
        return null;
    }
}
