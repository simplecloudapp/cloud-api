package app.simplecloud.api.group;

import org.jetbrains.annotations.Nullable;

/**
 * Status of one persisted manual server-start queue item.
 */
public enum GroupStartQueueItemStatus {
    PENDING,
    FAILED,
    UNKNOWN;

    public static GroupStartQueueItemStatus fromApiValue(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return UNKNOWN;
        }
        return switch (value.toLowerCase()) {
            case "pending" -> PENDING;
            case "failed" -> FAILED;
            default -> UNKNOWN;
        };
    }
}
