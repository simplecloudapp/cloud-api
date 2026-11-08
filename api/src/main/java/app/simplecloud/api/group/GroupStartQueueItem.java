package app.simplecloud.api.group;

import org.jetbrains.annotations.Nullable;

/**
 * One persisted manual server-start queue item.
 */
public record GroupStartQueueItem(
        @Nullable String createdAt,
        @Nullable String failureReason,
        @Nullable String id,
        GroupStartQueueItemStatus status) {

    /** @deprecated use {@link #createdAt()} */
    @Deprecated
    @Nullable
    public String getCreatedAt() {
        return createdAt;
    }

    /** @deprecated use {@link #failureReason()} */
    @Deprecated
    @Nullable
    public String getFailureReason() {
        return failureReason;
    }

    /** @deprecated use {@link #id()} */
    @Deprecated
    @Nullable
    public String getId() {
        return id;
    }

    /** @deprecated use {@link #status()} */
    @Deprecated
    public GroupStartQueueItemStatus getStatus() {
        return status;
    }
}
