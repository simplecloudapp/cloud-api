package app.simplecloud.api.model;

public interface ScalingConfig {
    int getAvailableSlots();
    int getMaxServers();
    int getMinServers();
    double getPlayerThreshold();
    ScaleDownConfig getScaleDown();
    String getScalingMode();
}