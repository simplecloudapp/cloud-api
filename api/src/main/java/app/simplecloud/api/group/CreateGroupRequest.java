package app.simplecloud.api.group;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Request object for creating a new server group.
 */
public class CreateGroupRequest {
    private final String name;
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

    public CreateGroupRequest(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    @Nullable
    public GroupServerType getType() {
        return type;
    }

    public CreateGroupRequest setType(@Nullable GroupServerType type) {
        this.type = type;
        return this;
    }

    @Nullable
    public Integer getMinMemory() {
        return minMemory;
    }

    public CreateGroupRequest setMinMemory(Integer minMemory) {
        this.minMemory = minMemory;
        return this;
    }

    @Nullable
    public Integer getMaxMemory() {
        return maxMemory;
    }

    public CreateGroupRequest setMaxMemory(Integer maxMemory) {
        this.maxMemory = maxMemory;
        return this;
    }

    @Nullable
    public Integer getMaxPlayers() {
        return maxPlayers;
    }

    public CreateGroupRequest setMaxPlayers(Integer maxPlayers) {
        this.maxPlayers = maxPlayers;
        return this;
    }

    @Nullable
    public DeploymentConfig getDeployment() {
        return deployment;
    }

    public CreateGroupRequest setDeployment(DeploymentConfig deployment) {
        this.deployment = deployment;
        return this;
    }

    @Nullable
    public ScalingConfig getScaling() {
        return scaling;
    }

    public CreateGroupRequest setScaling(ScalingConfig scaling) {
        this.scaling = scaling;
        return this;
    }

    @Nullable
    public SourceConfig getSource() {
        return source;
    }

    public CreateGroupRequest setSource(SourceConfig source) {
        this.source = source;
        return this;
    }

    @Nullable
    public WorkflowsConfig getWorkflows() {
        return workflows;
    }

    public CreateGroupRequest setWorkflows(WorkflowsConfig workflows) {
        this.workflows = workflows;
        return this;
    }

    @Nullable
    public Map<String, Object> getProperties() {
        return properties;
    }

    public CreateGroupRequest setProperties(Map<String, Object> properties) {
        this.properties = properties;
        return this;
    }

    @Nullable
    public List<String> getTags() {
        return tags;
    }

    public CreateGroupRequest setTags(List<String> tags) {
        this.tags = tags;
        return this;
    }
}

