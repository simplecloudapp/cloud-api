package app.simplecloud.api.group;

import org.jetbrains.annotations.Nullable;

public class ScalingConfig {
    private int availableSlots;
    private int maxServers;
    private int minServers;
    private double playerThreshold;
    private boolean playerThresholdSet;
    @Nullable
    private ScaleDownConfig scaleDown;
    @Nullable
    private ScalingMode scalingMode;

    public ScalingConfig() {
    }

    public static Builder builder() {
        return new Builder();
    }

    public int getAvailableSlots() {
        return availableSlots;
    }

    public void setAvailableSlots(int availableSlots) {
        this.availableSlots = availableSlots;
    }

    public int getMaxServers() {
        return maxServers;
    }

    public void setMaxServers(int maxServers) {
        this.maxServers = maxServers;
    }

    public int getMinServers() {
        return minServers;
    }

    public void setMinServers(int minServers) {
        this.minServers = minServers;
    }

    public double getPlayerThreshold() {
        return playerThreshold;
    }

    public void setPlayerThreshold(double playerThreshold) {
        this.playerThreshold = playerThreshold;
        this.playerThresholdSet = true;
    }

    public boolean hasPlayerThreshold() {
        return playerThresholdSet;
    }

    @Nullable
    public ScaleDownConfig getScaleDown() {
        return scaleDown;
    }

    public void setScaleDown(@Nullable ScaleDownConfig scaleDown) {
        this.scaleDown = scaleDown;
    }

    @Nullable
    public ScalingMode getScalingMode() {
        return scalingMode;
    }

    public void setScalingMode(@Nullable ScalingMode scalingMode) {
        this.scalingMode = scalingMode;
    }

    public static class Builder {
        private int availableSlots;
        private int maxServers;
        private int minServers;
        private double playerThreshold;
        private boolean playerThresholdSet;
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
            this.playerThresholdSet = true;
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
            ScalingConfig config = new ScalingConfig();
            config.setAvailableSlots(availableSlots);
            config.setMaxServers(maxServers);
            config.setMinServers(minServers);
            if (playerThresholdSet) {
                config.setPlayerThreshold(playerThreshold);
            }
            config.setScaleDown(scaleDown);
            config.setScalingMode(scalingMode);
            return config;
        }
    }
}
