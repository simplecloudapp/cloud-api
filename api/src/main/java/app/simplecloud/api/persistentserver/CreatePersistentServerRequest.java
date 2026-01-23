package app.simplecloud.api.persistentserver;

import app.simplecloud.api.group.SourceConfig;
import app.simplecloud.api.group.WorkflowsConfig;
import app.simplecloud.api.group.GroupServerType;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Request to create a new persistent server.
 */
public class CreatePersistentServerRequest {
    private final String name;
    private final Integer minMemory;
    private final Integer maxMemory;
    private final Integer maxPlayers;
    private final Boolean active;
    private final String serverhostId;
    private final GroupServerType type;
    private final SourceConfig source;
    private final WorkflowsConfig workflows;
    private final Map<String, Object> properties;
    private final List<String> tags;

    private CreatePersistentServerRequest(Builder builder) {
        this.name = builder.name;
        this.minMemory = builder.minMemory;
        this.maxMemory = builder.maxMemory;
        this.maxPlayers = builder.maxPlayers;
        this.active = builder.active;
        this.serverhostId = builder.serverhostId;
        this.type = builder.type;
        this.source = builder.source;
        this.workflows = builder.workflows;
        this.properties = builder.properties;
        this.tags = builder.tags;
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

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String name;
        private Integer minMemory;
        private Integer maxMemory;
        private Integer maxPlayers;
        private Boolean active;
        private String serverhostId;
        private GroupServerType type;
        private SourceConfig source;
        private WorkflowsConfig workflows;
        private Map<String, Object> properties;
        private List<String> tags;

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

        public CreatePersistentServerRequest build() {
            if (name == null) {
                throw new IllegalStateException("name is required");
            }
            return new CreatePersistentServerRequest(this);
        }
    }
}
