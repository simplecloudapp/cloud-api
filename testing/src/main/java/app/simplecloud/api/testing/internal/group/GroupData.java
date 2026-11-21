package app.simplecloud.api.testing.internal.group;

import app.simplecloud.api.group.DeploymentConfig;
import app.simplecloud.api.group.Group;
import app.simplecloud.api.group.GroupServerType;
import app.simplecloud.api.group.ScalingConfig;
import app.simplecloud.api.group.SourceConfig;
import app.simplecloud.api.group.WorkflowsConfig;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GroupData {

    public final String id;
    public final String createdAt;
    public String name;
    public String updatedAt;
    public GroupServerType type = GroupServerType.SERVER;
    public @Nullable Integer minMemory;
    public @Nullable Integer maxMemory;
    public @Nullable Integer maxPlayers;
    public @Nullable Boolean active = Boolean.TRUE;
    public @Nullable Integer priority;
    public @Nullable DeploymentConfig deployment;
    public @Nullable ScalingConfig scaling;
    public @Nullable SourceConfig source;
    public @Nullable WorkflowsConfig workflows;
    public final Map<String, Object> properties = new LinkedHashMap<>();
    public final List<String> tags = new ArrayList<>();
    public boolean deleted;
    public @Nullable TestGroupImpl handle;

    public GroupData(String id, String name, String createdAt) {
        this.id = id;
        this.name = name;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public GroupData copy() {
        GroupData copy = new GroupData(id, name, createdAt);
        copy.updatedAt = updatedAt;
        copy.type = type;
        copy.minMemory = minMemory;
        copy.maxMemory = maxMemory;
        copy.maxPlayers = maxPlayers;
        copy.active = active;
        copy.priority = priority;
        copy.deployment = deployment;
        copy.scaling = scaling;
        copy.source = source;
        copy.workflows = workflows;
        copy.properties.putAll(properties);
        copy.tags.addAll(tags);
        copy.deleted = deleted;
        return copy;
    }

    public Group snapshot() {
        return new GroupImpl(copy());
    }
}
