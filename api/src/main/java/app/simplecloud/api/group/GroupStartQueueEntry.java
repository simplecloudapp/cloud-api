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
}
