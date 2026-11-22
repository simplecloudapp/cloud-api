package app.simplecloud.api.group;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

public record ScalingConfig(
        @Nullable Integer availableSlots,
        @Nullable Integer maxServers,
        @Nullable Integer minServers,
        @Nullable Double playerThreshold,
        @Nullable ScaleDownConfig scaleDown,
        @Nullable ScalingMode scalingMode) {

    public boolean hasAvailableSlots() {
        return availableSlots != null;
    }

    public boolean hasMaxServers() {
        return maxServers != null;
    }

    public boolean hasMinServers() {
        return minServers != null;
    }

    public boolean hasPlayerThreshold() {
        return playerThreshold != null;
    }

    /**
     * @deprecated use {@link #availableSlots()}; will be removed in 1.0.0
     */
    @Deprecated(forRemoval = true)
    @ApiStatus.ScheduledForRemoval(inVersion = "1.0.0")
    public int getAvailableSlots() {
        return availableSlots != null ? availableSlots : 0;
    }

    /**
     * @deprecated use {@link #maxServers()}; will be removed in 1.0.0
     */
    @Deprecated(forRemoval = true)
    @ApiStatus.ScheduledForRemoval(inVersion = "1.0.0")
    public int getMaxServers() {
        return maxServers != null ? maxServers : 0;
    }

    /**
     * @deprecated use {@link #minServers()}; will be removed in 1.0.0
     */
    @Deprecated(forRemoval = true)
    @ApiStatus.ScheduledForRemoval(inVersion = "1.0.0")
    public int getMinServers() {
        return minServers != null ? minServers : 0;
    }

    /**
     * @deprecated use {@link #playerThreshold()}; will be removed in 1.0.0
     */
    @Deprecated(forRemoval = true)
    @ApiStatus.ScheduledForRemoval(inVersion = "1.0.0")
    public double getPlayerThreshold() {
        return playerThreshold != null ? playerThreshold : 0;
    }

    /**
     * @deprecated use {@link #scaleDown()}; will be removed in 1.0.0
     */
    @Deprecated(forRemoval = true)
    @ApiStatus.ScheduledForRemoval(inVersion = "1.0.0")
    @Nullable public ScaleDownConfig getScaleDown() {
        return scaleDown;
    }

    /**
     * @deprecated use {@link #scalingMode()}; will be removed in 1.0.0
     */
    @Deprecated(forRemoval = true)
    @ApiStatus.ScheduledForRemoval(inVersion = "1.0.0")
    @Nullable public ScalingMode getScalingMode() {
        return scalingMode;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Integer availableSlots;
        private Integer maxServers;
        private Integer minServers;
        private Double playerThreshold;
        private ScaleDownConfig scaleDown;
        private ScalingMode scalingMode;

        public Builder availableSlots(int availableSlots) {
            this.availableSlots = availableSlots;
            return this;
        }

        public Builder maxServers(int maxServers) {
            this.maxServers = maxServers;
            return this;
        }

        public Builder minServers(int minServers) {
            this.minServers = minServers;
            return this;
        }

        public Builder playerThreshold(double playerThreshold) {
            this.playerThreshold = playerThreshold;
            return this;
        }

        public Builder scaleDown(ScaleDownConfig scaleDown) {
            this.scaleDown = scaleDown;
            return this;
        }

        public Builder scalingMode(ScalingMode scalingMode) {
            this.scalingMode = scalingMode;
            return this;
        }

        public ScalingConfig build() {
            return new ScalingConfig(availableSlots, maxServers, minServers, playerThreshold, scaleDown, scalingMode);
        }
    }
}
