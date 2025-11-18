package app.simplecloud.api.group;

import org.jetbrains.annotations.Nullable;

public class ScalingConfig {
    private int availableSlots;
    private int maxServers;
    private int minServers;
    private double playerThreshold;
    @Nullable
    private ScaleDownConfig scaleDown;
    @Nullable
    private ScalingMode scalingMode;

    public ScalingConfig() {
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
}


