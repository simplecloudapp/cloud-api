package app.simplecloud.api.group;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

public record ScaleDownConfig(
        @Nullable String idleTime, @Nullable Boolean ignorePlayers) {

    public boolean hasIgnorePlayers() {
        return ignorePlayers != null;
    }

    /**
     * @deprecated use {@link #idleTime()}; will be removed in 1.0.0
     */
    @Deprecated(forRemoval = true)
    @ApiStatus.ScheduledForRemoval(inVersion = "1.0.0")
    @Nullable public String getIdleTime() {
        return idleTime;
    }

    /**
     * @deprecated use {@link #ignorePlayers()}; will be removed in 1.0.0
     */
    @Deprecated(forRemoval = true)
    @ApiStatus.ScheduledForRemoval(inVersion = "1.0.0")
    public boolean isIgnorePlayers() {
        return Boolean.TRUE.equals(ignorePlayers);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String idleTime;
        private Boolean ignorePlayers;

        public Builder idleTime(String idleTime) {
            this.idleTime = idleTime;
            return this;
        }

        public Builder ignorePlayers(boolean ignorePlayers) {
            this.ignorePlayers = ignorePlayers;
            return this;
        }

        public ScaleDownConfig build() {
            return new ScaleDownConfig(idleTime, ignorePlayers);
        }
    }
}
