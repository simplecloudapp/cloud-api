package app.simplecloud.api.group;

import org.jetbrains.annotations.Nullable;

/**
 * One persisted manual server-start queue item.
 */
public record GroupStartQueueItem(@Nullable String createdAt, @Nullable String failureReason, @Nullable String id, GroupStartQueueItemStatus status) { }
