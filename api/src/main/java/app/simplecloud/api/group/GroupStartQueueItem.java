package app.simplecloud.api.group;

import org.jetbrains.annotations.Nullable;

/**
 * One persisted manual server-start queue item.
 */
public final class GroupStartQueueItem {
    @Nullable
    private final String createdAt;
    @Nullable
    private final String failureReason;
    @Nullable
    private final String id;
    private final GroupStartQueueItemStatus status;

    public GroupStartQueueItem(
            @Nullable String createdAt,
            @Nullable String failureReason,
            @Nullable String id,
            GroupStartQueueItemStatus status
    ) {
        this.createdAt = createdAt;
        this.failureReason = failureReason;
        this.id = id;
        this.status = status;
    }

    @Nullable
    public String getCreatedAt() {
        return createdAt;
    }

    @Nullable
    public String getFailureReason() {
        return failureReason;
    }

    @Nullable
    public String getId() {
        return id;
    }

    public GroupStartQueueItemStatus getStatus() {
        return status;
    }
}
