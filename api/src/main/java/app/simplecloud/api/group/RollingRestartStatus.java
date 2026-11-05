package app.simplecloud.api.group;

import org.jetbrains.annotations.Nullable;

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

    @Nullable
    public String getStartedAt() {
        return startedAt;
    }
}
