package app.simplecloud.api.group;

import app.simplecloud.api.blueprint.CreateBlueprintRequest;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Request object for creating a new server group.
 *
 * <p>Fields that aren't set get these defaults: type {@link GroupServerType#SERVER}, 50 max players,
 * active, tags {@code [name]}, deployment {@link DeploymentStrategy#BLACKLIST} with no hosts, scaling
 * {@link ScalingMode#SLOTS} with 0 to 1 servers and a player threshold of 0.75, scale down after 3 minutes idle,
 * start workflow {@code internal/setup}, stop workflow {@code internal/cleanup} and manual workflows
 * {@code default/backup} and {@code default/copy-to-template}.
 *
 * <p>A blueprint created inline with {@code createBlueprint} gets the name of the group. Blueprint names are
 * unique per network, so creating the group fails if a blueprint with that name already exists.
 */
public class CreateGroupRequest {
    private final String name;

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
    private CreateBlueprintRequest createBlueprint;
    private WorkflowsConfig workflows;
    private Map<String, Object> properties;
    private List<String> tags;

    public CreateGroupRequest(String name) {
        this.name = name;
    }

    public static Builder builder() {
        return new Builder();
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
    public Boolean getActive() {
        return active;
    }

    public CreateGroupRequest setActive(@Nullable Boolean active) {
        this.active = active;
        return this;
    }

    @Nullable
    public Integer getPriority() {
        return priority;
    }

    public CreateGroupRequest setPriority(@Nullable Integer priority) {
        this.priority = priority;
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
    public CreateBlueprintRequest getCreateBlueprint() {
        return createBlueprint;
    }

    public CreateGroupRequest setCreateBlueprint(@Nullable CreateBlueprintRequest createBlueprint) {
        this.createBlueprint = createBlueprint;
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
        private CreateBlueprintRequest createBlueprint;
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

        public Builder createBlueprint(CreateBlueprintRequest createBlueprint) {
            this.createBlueprint = createBlueprint;
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

        public CreateGroupRequest build() {
            if (name == null) {
                throw new IllegalStateException("name is required");
            }

            return new CreateGroupRequest(name)
                    .setType(type)
                    .setMinMemory(minMemory)
                    .setMaxMemory(maxMemory)
                    .setMaxPlayers(maxPlayers)
                    .setActive(active)
                    .setPriority(priority)
                    .setDeployment(deployment)
                    .setScaling(scaling)
                    .setSource(source)
                    .setCreateBlueprint(createBlueprint)
                    .setWorkflows(workflows)
                    .setProperties(properties)
                    .setTags(tags);
        }
    }
}
