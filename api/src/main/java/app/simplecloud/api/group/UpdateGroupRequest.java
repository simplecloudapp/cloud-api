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
    private Boolean active;
    private Integer priority;
    private DeploymentConfig deployment;
    private ScalingConfig scaling;
    private SourceConfig source;
    private WorkflowsConfig workflows;
    private Map<String, Object> properties;
    private List<String> tags;

    public UpdateGroupRequest() {
    }

    public static Builder builder() {
        return new Builder();
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
    public Boolean getActive() {
        return active;
    }

    public UpdateGroupRequest setActive(@Nullable Boolean active) {
        this.active = active;
        return this;
    }

    @Nullable
    public Integer getPriority() {
        return priority;
    }

    public UpdateGroupRequest setPriority(@Nullable Integer priority) {
        this.priority = priority;
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

    public static class Builder {
        private String name;
        private GroupServerType type;
        private Integer minMemory;
        private Integer maxMemory;
        private Integer maxPlayers;
        private Boolean active;
        private Integer priority;
        private DeploymentConfig deployment;
        private ScalingConfig scaling;
        private SourceConfig source;
        private WorkflowsConfig workflows;
        private Map<String, Object> properties;
        private List<String> tags;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder type(GroupServerType type) {
            this.type = type;
            return this;
        }

        public Builder minMemory(Integer minMemory) {
            this.minMemory = minMemory;
            return this;
        }

        public Builder maxMemory(Integer maxMemory) {
            this.maxMemory = maxMemory;
            return this;
        }

        public Builder maxPlayers(Integer maxPlayers) {
            this.maxPlayers = maxPlayers;
            return this;
        }

        public Builder active(Boolean active) {
            this.active = active;
            return this;
        }

        public Builder priority(Integer priority) {
            this.priority = priority;
            return this;
        }

        public Builder deployment(DeploymentConfig deployment) {
            this.deployment = deployment;
            return this;
        }

        public Builder scaling(ScalingConfig scaling) {
            this.scaling = scaling;
            return this;
        }

        public Builder source(SourceConfig source) {
            this.source = source;
            return this;
        }

        public Builder workflows(WorkflowsConfig workflows) {
            this.workflows = workflows;
            return this;
        }

        public Builder properties(Map<String, Object> properties) {
            this.properties = properties;
            return this;
        }

        public Builder tags(List<String> tags) {
            this.tags = tags;
            return this;
        }

        public UpdateGroupRequest build() {
            return new UpdateGroupRequest()
                    .setName(name)
                    .setType(type)
                    .setMinMemory(minMemory)
                    .setMaxMemory(maxMemory)
                    .setMaxPlayers(maxPlayers)
                    .setActive(active)
                    .setPriority(priority)
                    .setDeployment(deployment)
                    .setScaling(scaling)
                    .setSource(source)
                    .setWorkflows(workflows)
                    .setProperties(properties)
                    .setTags(tags);
        }
    }
}
