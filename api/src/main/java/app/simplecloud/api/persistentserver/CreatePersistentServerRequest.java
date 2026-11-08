package app.simplecloud.api.persistentserver;

import app.simplecloud.api.blueprint.CreateBlueprintRequest;
import app.simplecloud.api.group.GroupServerType;
import app.simplecloud.api.group.SourceConfig;
import app.simplecloud.api.group.WorkflowsConfig;
import app.simplecloud.api.plugin.PluginReference;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Request to create a new persistent server.
 *
 * <p>Fields that aren't set get the same defaults as {@link app.simplecloud.api.group.CreateGroupRequest}
 * for type, max players, active state, tags and workflows. An inline blueprint set with
 * {@code createBlueprint} works the same way as for groups and gets the name of the persistent server.
 *
 * <p>{@code minMemory}, {@code maxMemory} and either {@code source} or {@code createBlueprint} are required.
 * The controller rejects the request without them.
 *
 * <p>Plugins and plugin collections set here are installed on the persistent server when it is created.
 */
public class CreatePersistentServerRequest {
    private final String name;
    private final Integer minMemory;
    private final Integer maxMemory;
    private final Integer maxPlayers;
    private final Boolean active;
    private final Integer priority;
    private final String serverhostId;
    private final GroupServerType type;
    private final SourceConfig source;
    private final CreateBlueprintRequest createBlueprint;
    private final WorkflowsConfig workflows;
    private final Map<String, Object> properties;
    private final List<String> tags;
    private final List<PluginReference> plugins;
    private final List<String> pluginCollectionIds;

    private CreatePersistentServerRequest(Builder builder) {
        this.name = builder.name;
        this.minMemory = builder.minMemory;
        this.maxMemory = builder.maxMemory;
        this.maxPlayers = builder.maxPlayers;
        this.active = builder.active;
        this.priority = builder.priority;
        this.serverhostId = builder.serverhostId;
        this.type = builder.type;
        this.source = builder.source;
        this.createBlueprint = builder.createBlueprint;
        this.workflows = builder.workflows;
        this.properties = builder.properties;
        this.tags = builder.tags;
        this.plugins = builder.plugins;
        this.pluginCollectionIds = builder.pluginCollectionIds;
    }

    public String getName() {
        return name;
    }

    @Nullable
    public Integer getMinMemory() {
        return minMemory;
    }

    @Nullable
    public Integer getMaxMemory() {
        return maxMemory;
    }

    @Nullable
    public Integer getMaxPlayers() {
        return maxPlayers;
    }

    @Nullable
    public Boolean getActive() {
        return active;
    }

    @Nullable
    public Integer getPriority() {
        return priority;
    }

    @Nullable
    public String getServerhostId() {
        return serverhostId;
    }

    @Nullable
    public GroupServerType getType() {
        return type;
    }

    @Nullable
    public SourceConfig getSource() {
        return source;
    }

    @Nullable
    public CreateBlueprintRequest getCreateBlueprint() {
        return createBlueprint;
    }

    @Nullable
    public WorkflowsConfig getWorkflows() {
        return workflows;
    }

    @Nullable
    public Map<String, Object> getProperties() {
        return properties;
    }

    @Nullable
    public List<String> getTags() {
        return tags;
    }

    @Nullable
    public List<PluginReference> getPlugins() {
        return plugins;
    }

    @Nullable
    public List<String> getPluginCollectionIds() {
        return pluginCollectionIds;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String name;
        private Integer minMemory;
        private Integer maxMemory;
        private Integer maxPlayers;
        private Boolean active;
        private Integer priority;
        private String serverhostId;
        private GroupServerType type;
        private SourceConfig source;
        private CreateBlueprintRequest createBlueprint;
        private WorkflowsConfig workflows;
        private Map<String, Object> properties;
        private List<String> tags;
        private List<PluginReference> plugins;
        private List<String> pluginCollectionIds;

        public Builder name(String name) {
            this.name = name;
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

        public Builder serverhostId(String serverhostId) {
            this.serverhostId = serverhostId;
            return this;
        }

        public Builder type(GroupServerType type) {
            this.type = type;
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

        public Builder plugins(List<PluginReference> plugins) {
            this.plugins = plugins;
            return this;
        }

        public Builder pluginCollectionIds(List<String> pluginCollectionIds) {
            this.pluginCollectionIds = pluginCollectionIds;
            return this;
        }

        public CreatePersistentServerRequest build() {
            if (name == null) {
                throw new IllegalStateException("name is required");
            }
            return new CreatePersistentServerRequest(this);
        }
    }
}
