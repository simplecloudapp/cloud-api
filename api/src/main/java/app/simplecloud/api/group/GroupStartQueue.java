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
