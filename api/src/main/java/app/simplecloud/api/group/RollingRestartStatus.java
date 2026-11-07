package app.simplecloud.api.group;

import org.jetbrains.annotations.Nullable;

/**
 * Whether a rolling restart is running for a server group.
 *
 * @see GroupApi#getRollingRestartStatus(String)
 */
public record RollingRestartStatus(String serverGroupId, boolean active, @Nullable String startedAt) { }
