package app.simplecloud.api.group;

import org.jetbrains.annotations.Nullable;

/**
 * Status of one persisted manual server-start queue item.
 */
public enum GroupStartQueueItemStatus {
    /**
     * The start is waiting to be executed.
     */
    PENDING,

    /**
     * The start was attempted and failed, see {@link GroupStartQueueItem#getFailureReason()}.
     */
    FAILED,

    /**
     * Not reported, or a value this API version doesn't know.
     */
    UNKNOWN;

    /**
     * Parses a status as returned by the controller, ignoring case.
     *
     * @param value the value to parse
     * @return the status, or {@link #UNKNOWN} if the value is null, blank or not recognized
     */
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
