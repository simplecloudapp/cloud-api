package app.simplecloud.api.testing.internal.group;

import app.simplecloud.api.group.GroupStartQueueItem;
import app.simplecloud.api.group.GroupStartQueueItemStatus;
import org.jetbrains.annotations.Nullable;

public final class QueuedStart {

    public final String id;
    public final GroupData group;
    public final String createdAt;
    public GroupStartQueueItemStatus status = GroupStartQueueItemStatus.PENDING;
    public @Nullable String failureReason;

    public QueuedStart(String id, GroupData group, String createdAt) {
        this.id = id;
        this.group = group;
        this.createdAt = createdAt;
    }

    public GroupStartQueueItem toItem() {
        return new GroupStartQueueItem(createdAt, failureReason, id, status);
    }
}
