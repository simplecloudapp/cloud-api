package app.simplecloud.api.internal.persistentserver;

import app.simplecloud.api.group.GroupServerType;
import app.simplecloud.api.group.SourceConfig;
import app.simplecloud.api.group.SourceType;
import app.simplecloud.api.group.WorkflowWhen;
import app.simplecloud.api.group.WorkflowsConfig;
import app.simplecloud.api.persistentserver.PersistentServer;
import app.simplecloud.api.web.models.ModelsPersistentServerSummary;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public class PersistentServerImpl implements PersistentServer {
    private final ModelsPersistentServerSummary delegate;
    private SourceConfig source;
    private WorkflowsConfig workflows;

    public PersistentServerImpl(ModelsPersistentServerSummary delegate) {
        this.delegate = delegate;
    }

    @Override
    public String getPersistentServerId() {
        String id = delegate.getPersistentServerId();
        if (id == null) {
            throw new IllegalStateException("Persistent server ID is null");
        }
        return id;
    }

    @Override
    @Nullable
    public Boolean isActive() {
        return delegate.getActive();
    }

    @Override
    @Nullable
    public String getServerhostId() {
        return delegate.getServerhostId();
    }

    @Override
    public String getName() {
        String name = delegate.getName();
        if (name == null) {
            throw new IllegalStateException("Persistent server name is null");
        }
        return name;
    }

    @Override
    @Nullable
    public Integer getMinMemory() {
        return delegate.getMinMemory();
    }

    @Override
    @Nullable
    public Integer getMaxMemory() {
        return delegate.getMaxMemory();
    }

    @Override
    @Nullable
    public Integer getMaxPlayers() {
        return delegate.getMaxPlayers();
    }

    @Override
    @Nullable
    public SourceConfig getSource() {
        if (source == null && delegate.getSource() != null) {
            source = convertSourceConfig(delegate.getSource());
        }
        return source;
    }

    @Override
    @Nullable
    public WorkflowsConfig getWorkflows() {
        if (workflows == null && delegate.getWorkflows() != null) {
            workflows = convertWorkflowsConfig(delegate.getWorkflows());
        }
        return workflows;
    }

    @Override
    @Nullable
    public Map<String, Object> getProperties() {
        return delegate.getProperties();
    }

    @Override
    @Nullable
    public List<String> getTags() {
        return delegate.getTags();
    }

    @Override
    public GroupServerType getType() {
        String typeStr = delegate.getType();
        if (typeStr == null) {
            throw new IllegalStateException("Persistent server type is null");
        }
        try {
            return GroupServerType.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Invalid persistent server type: " + typeStr, e);
        }
    }

    @Override
    public String getCreatedAt() {
        String createdAt = delegate.getCreatedAt();
        if (createdAt == null) {
            throw new IllegalStateException("Created at timestamp is null");
        }
        return createdAt;
    }

    @Override
    public String getUpdatedAt() {
        String updatedAt = delegate.getUpdatedAt();
        if (updatedAt == null) {
            throw new IllegalStateException("Updated at timestamp is null");
        }
        return updatedAt;
    }

    private SourceConfig convertSourceConfig(app.simplecloud.api.web.models.ModelsSourceConfig config) {
        SourceConfig result = new SourceConfig();
        String typeStr = config.getType();
        if (typeStr != null) {
            try {
                result.setType(SourceType.valueOf(typeStr.toUpperCase()));
            } catch (IllegalArgumentException e) {
            }
        }
        result.setBlueprint(config.getBlueprint());
        result.setImage(config.getImage());
        return result;
    }

    private WorkflowsConfig convertWorkflowsConfig(app.simplecloud.api.web.models.ModelsWorkflowsConfig config) {
        WorkflowsConfig result = new WorkflowsConfig();
        result.setManual(config.getManual());
        if (config.getWhen() != null) {
            WorkflowWhen when = new WorkflowWhen();
            when.setStart(config.getWhen().getStart());
            when.setStop(config.getWhen().getStop());
            result.setWhen(when);
        }
        return result;
    }
}

