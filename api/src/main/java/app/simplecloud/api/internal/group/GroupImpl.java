package app.simplecloud.api.internal.group;

import app.simplecloud.api.group.*;
import app.simplecloud.api.web.models.ModelsServerGroupInfo;
import app.simplecloud.api.web.models.ModelsServerGroupSummary;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public class GroupImpl implements app.simplecloud.api.group.Group {
    private final ModelsServerGroupSummary summaryDelegate;
    private final ModelsServerGroupInfo infoDelegate;
    private DeploymentConfig deployment;
    private ScalingConfig scaling;
    private SourceConfig source;
    private WorkflowsConfig workflows;

    public GroupImpl(ModelsServerGroupSummary delegate) {
        this.summaryDelegate = delegate;
        this.infoDelegate = null;
    }

    public GroupImpl(ModelsServerGroupInfo delegate) {
        this.summaryDelegate = null;
        this.infoDelegate = delegate;
    }

    @Override
    public String getServerGroupId() {
        if (summaryDelegate != null) {
            String id = summaryDelegate.getServerGroupId();
            if (id == null) {
                throw new IllegalStateException("Server group ID is null");
            }
            return id;
        }
        if (infoDelegate != null) {
            String id = infoDelegate.getId();
            if (id == null) {
                throw new IllegalStateException("Server group ID is null");
            }
            return id;
        }
        throw new IllegalStateException("No delegate available");
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
            throw new IllegalStateException("Group name is null");
        }
        return name;
    }

    @Override
    @Nullable
    public Integer getMinMemory() {
        if (summaryDelegate != null) {
            return summaryDelegate.getMinMemory();
        }
        return null;
    }

    @Override
    @Nullable
    public Integer getMaxMemory() {
        if (summaryDelegate != null) {
            return summaryDelegate.getMaxMemory();
        }
        return null;
    }

    @Override
    @Nullable
    public Integer getMaxPlayers() {
        if (summaryDelegate != null) {
            return summaryDelegate.getMaxPlayers();
        }
        return null;
    }

    @Override
    @Nullable
    public DeploymentConfig getDeployment() {
        if (deployment == null) {
            app.simplecloud.api.web.models.ModelsDeploymentConfig config = null;
            if (summaryDelegate != null) {
                config = summaryDelegate.getDeployment();
            } else if (infoDelegate != null) {
                Map<String, Object> deploymentConfig = infoDelegate.getDeploymentConfig();
                String strategy = infoDelegate.getDeploymentStrategy();
                if (deploymentConfig != null || strategy != null) {
                    config = new app.simplecloud.api.web.models.ModelsDeploymentConfig();
                    config.setStrategy(strategy);
                }
            }
            if (config != null) {
                deployment = convertDeploymentConfig(config);
            }
        }
        return deployment;
    }

    @Override
    @Nullable
    public ScalingConfig getScaling() {
        if (scaling == null) {
            app.simplecloud.api.web.models.ModelsScalingConfig config = null;
            if (summaryDelegate != null) {
                config = summaryDelegate.getScaling();
            } else if (infoDelegate != null) {
                Map<String, Object> scalingConfig = infoDelegate.getScalingConfig();
                if (scalingConfig != null) {
                    config = new app.simplecloud.api.web.models.ModelsScalingConfig();
                }
            }
            if (config != null) {
                scaling = convertScalingConfig(config);
            }
        }
        return scaling;
    }

    @Override
    @Nullable
    public SourceConfig getSource() {
        if (source == null) {
            app.simplecloud.api.web.models.ModelsSourceConfig config = null;
            if (summaryDelegate != null) {
                config = summaryDelegate.getSource();
            } else if (infoDelegate != null) {
                app.simplecloud.api.web.models.ModelsSourceInfo sourceInfo = infoDelegate.getSource();
                if (sourceInfo != null) {
                    config = new app.simplecloud.api.web.models.ModelsSourceConfig();
                    config.setType(sourceInfo.getType());
                    app.simplecloud.api.web.models.ModelsBlueprintInfo blueprintInfo = sourceInfo.getBlueprint();
                    if (blueprintInfo != null) {
                        config.setBlueprint(blueprintInfo.getId());
                    }
                    config.setImage(sourceInfo.getImage());
                }
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
                Map<String, Object> workflowsConfig = infoDelegate.getWorkflowsConfig();
                if (workflowsConfig != null) {
                    config = new app.simplecloud.api.web.models.ModelsWorkflowsConfig();
                }
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
            throw new IllegalStateException("Group type is null");
        }
        try {
            return GroupServerType.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Invalid group type: " + typeStr, e);
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

    private DeploymentConfig convertDeploymentConfig(app.simplecloud.api.web.models.ModelsDeploymentConfig config) {
        DeploymentConfig result = new DeploymentConfig();
        String strategyStr = config.getStrategy();
        if (strategyStr != null) {
            try {
                result.setStrategy(DeploymentStrategy.valueOf(strategyStr));
            } catch (IllegalArgumentException e) {
            }
        }
        if (config.getHosts() != null) {
            DeploymentHost[] hosts = config.getHosts().stream()
                    .map(h -> {
                        DeploymentHost host = new DeploymentHost();
                        host.setName(h.getName());
                        host.setPriority(h.getPriority() != null ? h.getPriority() : 0);
                        return host;
                    })
                    .toArray(DeploymentHost[]::new);
            result.setHosts(hosts);
        }
        return result;
    }

    private ScalingConfig convertScalingConfig(app.simplecloud.api.web.models.ModelsScalingConfig config) {
        ScalingConfig result = new ScalingConfig();
        result.setAvailableSlots(config.getAvailableSlots() != null ? config.getAvailableSlots() : 0);
        result.setMaxServers(config.getMaxServers() != null ? config.getMaxServers() : 0);
        result.setMinServers(config.getMinServers() != null ? config.getMinServers() : 0);
        result.setPlayerThreshold(config.getPlayerThreshold() != null ? config.getPlayerThreshold().doubleValue() : 0.0);
        String scalingModeStr = config.getScalingMode();
        if (scalingModeStr != null) {
            try {
                result.setScalingMode(ScalingMode.valueOf(scalingModeStr));
            } catch (IllegalArgumentException e) {
            }
        }
        if (config.getScaleDown() != null) {
            ScaleDownConfig scaleDown = new ScaleDownConfig();
            scaleDown.setIdleTime(config.getScaleDown().getIdleTime());
            scaleDown.setIgnorePlayers(config.getScaleDown().getIgnorePlayers() != null && config.getScaleDown().getIgnorePlayers());
            result.setScaleDown(scaleDown);
        }
        return result;
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

