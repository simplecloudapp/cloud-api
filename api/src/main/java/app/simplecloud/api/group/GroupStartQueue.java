package app.simplecloud.api.group;

import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * Aggregated manual server-start queue state across all server groups.
 */
public record GroupStartQueue(
        int count, int failedStarts, List<GroupStartQueueEntry> items, int queuedStarts, int totalStarts) {

    public GroupStartQueue {
        items = List.copyOf(items);
    }

    /**
     * @deprecated use {@link #count()}; will be removed in 1.0.0
     */
    @Deprecated(forRemoval = true)
    public int getCount() {
        return count;
    }

    /**
     * @deprecated use {@link #failedStarts()}; will be removed in 1.0.0
     */
    @Deprecated(forRemoval = true)
    public int getFailedStarts() {
        return failedStarts;
    }

    /**
     * @deprecated use {@link #items()}; will be removed in 1.0.0
     */
    @Deprecated(forRemoval = true)
    public List<GroupStartQueueEntry> getItems() {
        return items;
    }

    /**
     * @deprecated use {@link #queuedStarts()}; will be removed in 1.0.0
     */
    @Deprecated(forRemoval = true)
    public int getQueuedStarts() {
        return queuedStarts;
    }

    /**
     * @deprecated use {@link #totalStarts()}; will be removed in 1.0.0
     */
    @Deprecated(forRemoval = true)
    public int getTotalStarts() {
        return totalStarts;
    }

    @Nullable public GroupStartQueueEntry findByServerGroupId(String serverGroupId) {
        for (GroupStartQueueEntry item : items) {
            if (serverGroupId.equals(item.serverGroupId())) {
                return item;
            }
        }
        return null;
    }

    @Nullable public GroupStartQueueEntry findByServerGroupName(String serverGroupName) {
        for (GroupStartQueueEntry item : items) {
            if (serverGroupName.equals(item.serverGroupName())) {
                return item;
            }
        }
        return null;
    }
}
