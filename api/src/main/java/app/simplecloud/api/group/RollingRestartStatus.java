package app.simplecloud.api.group;

import org.jetbrains.annotations.Nullable;

/**
 * State of a server group's rolling restart.
 *
 * @param serverGroupId the server group ID
 * @param active        whether a rolling restart is running
 * @param timestamp     the controller timestamp of the running rolling restart, or null if none is running
 */
public record RollingRestartStatus(String serverGroupId, boolean active, @Nullable String timestamp) {
}
