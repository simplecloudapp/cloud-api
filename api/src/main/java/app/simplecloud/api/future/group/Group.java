package app.simplecloud.api.future.group;

import app.simplecloud.api.model.DeploymentConfig;
import app.simplecloud.api.model.ScalingConfig;
import app.simplecloud.api.model.SourceConfig;

import java.util.Map;


public interface Group {
    String getServerGroupId();
    String getName();

    int getMinMemory();
    int getMaxMemory();
    int getMaxPlayers();

    DeploymentConfig getDeployment();
    ScalingConfig getScaling();
    SourceConfig getSource();

    Map<String, Object> getProperties();
    String[] getTags();
    String getType();

    String getCreatedAt();
    String getUpdatedAt();
}
