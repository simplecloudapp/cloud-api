package app.simplecloud.api.internal.persistentserver;

import app.simplecloud.api.group.GroupServerType;
import app.simplecloud.api.group.SourceConfig;
import app.simplecloud.api.group.SourceType;
import app.simplecloud.api.group.WorkflowWhen;
import app.simplecloud.api.group.WorkflowsConfig;
import app.simplecloud.api.persistentserver.PersistentServer;
import app.simplecloud.api.web.models.ModelsPersistentServerInfo;
import app.simplecloud.api.web.models.ModelsPersistentServerSummary;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public class PersistentServerImpl implements PersistentServer {
    private final ModelsPersistentServerSummary summaryDelegate;
    private final ModelsPersistentServerInfo infoDelegate;
    private SourceConfig source;
    private WorkflowsConfig workflows;

    public PersistentServerImpl(ModelsPersistentServerSummary delegate) {
        this.summaryDelegate = delegate;
        this.infoDelegate = null;
    }

    public PersistentServerImpl(ModelsPersistentServerInfo delegate) {
        this.summaryDelegate = null;
        this.infoDelegate = delegate;
    }

    @Override
    public String getPersistentServerId() {
        if (summaryDelegate != null) {
            String id = summaryDelegate.getPersistentServerId();
            if (id == null) {
                throw new IllegalStateException("Persistent server ID is null");
            }
            return id;
        }
        if (infoDelegate != null) {
            String id = infoDelegate.getId();
            if (id == null) {
                throw new IllegalStateException("Persistent server ID is null");
            }
            return id;
        }
        throw new IllegalStateException("No delegate available");
    }

    @Override
    @Nullable
    public Boolean isActive() {
        if (summaryDelegate != null) {
            return summaryDelegate.getActive();
        }
        return infoDelegate != null ? infoDelegate.getActive() : null;
    }

    @Override
    @Nullable
    public String getServerhostId() {
        if (summaryDelegate != null) {
            return summaryDelegate.getServerhostId();
        }
        return infoDelegate != null ? infoDelegate.getServerhostId() : null;
    }

    @Override
    public String getName() {
        String name = null;
        if (summaryDelegate != null) {
            name = summaryDelegate.getName();
        } else if (infoDelegate != null) {
            name = infoDelegate.getName();
        }
        if (name == null) {
            throw new IllegalStateException("Persistent server name is null");
        }
        return name;
    }

    @Override
    @Nullable
    public Integer getMinMemory() {
        if (summaryDelegate != null) {
            return summaryDelegate.getMinMemory();
        }
        return infoDelegate != null ? infoDelegate.getMinMemory() : null;
    }

    @Override
    @Nullable
    public Integer getMaxMemory() {
        if (summaryDelegate != null) {
            return summaryDelegate.getMaxMemory();
        }
        return infoDelegate != null ? infoDelegate.getMaxMemory() : null;
    }

    @Override
    @Nullable
    public Integer getMaxPlayers() {
        if (summaryDelegate != null) {
            return summaryDelegate.getMaxPlayers();
        }
        return infoDelegate != null ? infoDelegate.getMaxPlayers() : null;
    }

    @Override
    @Nullable
    public SourceConfig getSource() {
        if (source == null) {
            app.simplecloud.api.web.models.ModelsSourceConfig config = null;
            if (summaryDelegate != null) {
                config = summaryDelegate.getSource();
            } else if (infoDelegate != null) {
                config = infoDelegate.getSource();
            }
            if (config != null) {
                source = convertSourceConfig(config);
            }
        }
        return source;
    }

    @Override
    @Nullable
    public WorkflowsConfig getWorkflows() {
        if (workflows == null) {
            app.simplecloud.api.web.models.ModelsWorkflowsConfig config = null;
            if (summaryDelegate != null) {
                config = summaryDelegate.getWorkflows();
            } else if (infoDelegate != null) {
                config = infoDelegate.getWorkflows();
            }
            if (config != null) {
                workflows = convertWorkflowsConfig(config);
            }
        }
        return workflows;
    }

    @Override
    @Nullable
    public Map<String, Object> getProperties() {
        if (summaryDelegate != null) {
            return summaryDelegate.getProperties();
        }
        return infoDelegate != null ? infoDelegate.getProperties() : null;
    }

    @Override
    @Nullable
    public List<String> getTags() {
        if (summaryDelegate != null) {
            return summaryDelegate.getTags();
        }
        return infoDelegate != null ? infoDelegate.getTags() : null;
    }

    @Override
    public GroupServerType getType() {
        String typeStr = null;
        if (summaryDelegate != null) {
            typeStr = summaryDelegate.getType();
        } else if (infoDelegate != null) {
            typeStr = infoDelegate.getType();
        }
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
        String createdAt = null;
        if (summaryDelegate != null) {
            createdAt = summaryDelegate.getCreatedAt();
        } else if (infoDelegate != null) {
            createdAt = infoDelegate.getCreatedAt();
        }
        if (createdAt == null) {
            throw new IllegalStateException("Created at timestamp is null");
        }
        return createdAt;
    }

    @Override
    public String getUpdatedAt() {
        String updatedAt = null;
        if (summaryDelegate != null) {
            updatedAt = summaryDelegate.getUpdatedAt();
        } else if (infoDelegate != null) {
            updatedAt = infoDelegate.getUpdatedAt();
        }
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
