package app.simplecloud.api.group;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public class UpdateGroupRequest {
    private String name;
    @Nullable
    private GroupServerType type;
    private Integer minMemory;
    private Integer maxMemory;
    private Integer maxPlayers;
    private DeploymentConfig deployment;
    private ScalingConfig scaling;
    private SourceConfig source;
    private WorkflowsConfig workflows;
    private Map<String, Object> properties;
    private List<String> tags;

    public UpdateGroupRequest() {
    }

    @Nullable
    public String getName() {
        return name;
    }

    public UpdateGroupRequest setName(String name) {
        this.name = name;
        return this;
    }

    @Nullable
    public GroupServerType getType() {
        return type;
    }

    public UpdateGroupRequest setType(@Nullable GroupServerType type) {
        this.type = type;
        return this;
    }

    @Nullable
    public Integer getMinMemory() {
        return minMemory;
    }

    public UpdateGroupRequest setMinMemory(Integer minMemory) {
        this.minMemory = minMemory;
        return this;
    }

    @Nullable
    public Integer getMaxMemory() {
        return maxMemory;
    }

    public UpdateGroupRequest setMaxMemory(Integer maxMemory) {
        this.maxMemory = maxMemory;
        return this;
    }

    @Nullable
    public Integer getMaxPlayers() {
        return maxPlayers;
    }

    public UpdateGroupRequest setMaxPlayers(Integer maxPlayers) {
        this.maxPlayers = maxPlayers;
        return this;
    }

    @Nullable
    public DeploymentConfig getDeployment() {
        return deployment;
    }

    public UpdateGroupRequest setDeployment(DeploymentConfig deployment) {
        this.deployment = deployment;
        return this;
    }

    @Nullable
    public ScalingConfig getScaling() {
        return scaling;
    }

    public UpdateGroupRequest setScaling(ScalingConfig scaling) {
        this.scaling = scaling;
        return this;
    }

    @Nullable
    public SourceConfig getSource() {
        return source;
    }

    public UpdateGroupRequest setSource(SourceConfig source) {
        this.source = source;
        return this;
    }

    @Nullable
    public WorkflowsConfig getWorkflows() {
        return workflows;
    }

    public UpdateGroupRequest setWorkflows(WorkflowsConfig workflows) {
        this.workflows = workflows;
        return this;
    }

    @Nullable
    public Map<String, Object> getProperties() {
        return properties;
    }

    public UpdateGroupRequest setProperties(Map<String, Object> properties) {
        this.properties = properties;
        return this;
    }

    @Nullable
    public List<String> getTags() {
        return tags;
    }

    public UpdateGroupRequest setTags(List<String> tags) {
        this.tags = tags;
        return this;
    }
}


