package app.simplecloud.api.group;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/**
 * One persisted manual server-start queue item.
 */
public record GroupStartQueueItem(
        @Nullable String createdAt,
        @Nullable String failureReason,
        @Nullable String id,
        GroupStartQueueItemStatus status) {

    /**
     * @deprecated use {@link #createdAt()}; will be removed in 1.0.0
     */
    @Deprecated(forRemoval = true)
    @ApiStatus.ScheduledForRemoval(inVersion = "1.0.0")
    @Nullable public String getCreatedAt() {
        return createdAt;
    }

    /**
     * @deprecated use {@link #failureReason()}; will be removed in 1.0.0
     */
    @Deprecated(forRemoval = true)
    @ApiStatus.ScheduledForRemoval(inVersion = "1.0.0")
    @Nullable public String getFailureReason() {
        return failureReason;
    }

    /**
     * @deprecated use {@link #id()}; will be removed in 1.0.0
     */
    @Deprecated(forRemoval = true)
    @ApiStatus.ScheduledForRemoval(inVersion = "1.0.0")
    @Nullable public String getId() {
        return id;
    }

    /**
     * @deprecated use {@link #status()}; will be removed in 1.0.0
     */
    @Deprecated(forRemoval = true)
    @ApiStatus.ScheduledForRemoval(inVersion = "1.0.0")
    public GroupStartQueueItemStatus getStatus() {
        return status;
    }
}
