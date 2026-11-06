package app.simplecloud.api.group;

import org.jetbrains.annotations.Nullable;

/**
 * Whether a rolling restart is running for a server group.
 *
 * @see GroupApi#getRollingRestartStatus(String)
 */
public final class RollingRestartStatus {
    private final String serverGroupId;
    private final boolean active;

    @Nullable
    private final String startedAt;

    public RollingRestartStatus(String serverGroupId, boolean active, @Nullable String startedAt) {
        this.serverGroupId = serverGroupId;
        this.active = active;
        this.startedAt = startedAt;
    }

    public String getServerGroupId() {
        return serverGroupId;
    }

    public boolean isActive() {
        return active;
    }

    /**
     * @return when the rolling restart started as an ISO-8601 timestamp, or null if unavailable
     */
    @Nullable
    public String getStartedAt() {
        return startedAt;
    }
}
