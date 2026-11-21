package app.simplecloud.api.testing.internal.group;

import app.simplecloud.api.group.DeploymentConfig;
import app.simplecloud.api.group.Group;
import app.simplecloud.api.group.GroupServerType;
import app.simplecloud.api.group.ScalingConfig;
import app.simplecloud.api.group.SourceConfig;
import app.simplecloud.api.group.WorkflowsConfig;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class GroupImpl implements Group {

    protected final GroupData data;

    public GroupImpl(GroupData data) {
        this.data = data;
    }

    @Override
    public String getServerGroupId() {
        return data.id;
    }

    @Override
    public String getName() {
        return data.name;
    }

    @Override
    public @Nullable Integer getMinMemory() {
        return data.minMemory;
    }

    @Override
    public @Nullable Integer getMaxMemory() {
        return data.maxMemory;
    }

    @Override
    public @Nullable Integer getMaxPlayers() {
        return data.maxPlayers;
    }

    @Override
    public @Nullable SourceConfig getSource() {
        return data.source;
    }

    @Override
    public @Nullable WorkflowsConfig getWorkflows() {
        return data.workflows;
    }

    @Override
    public Map<String, Object> getProperties() {
        return new LinkedHashMap<>(data.properties);
    }

    @Override
    public List<String> getTags() {
        return List.copyOf(data.tags);
    }

    @Override
    public GroupServerType getType() {
        return data.type;
    }

    @Override
    public String getCreatedAt() {
        return data.createdAt;
    }

    @Override
    public String getUpdatedAt() {
        return data.updatedAt;
    }

    @Override
    public @Nullable DeploymentConfig getDeployment() {
        return data.deployment;
    }

    @Override
    public @Nullable Boolean isActive() {
        return data.active;
    }

    @Override
    public @Nullable Integer getPriority() {
        return data.priority;
    }

    @Override
    public @Nullable ScalingConfig getScaling() {
        return data.scaling;
    }

    @Override
    public String toString() {
        return "Group[" + data.name + ", id=" + data.id + "]";
    }
}
