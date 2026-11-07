package app.simplecloud.api.internal.group;

import app.simplecloud.api.group.DeploymentConfig;
import app.simplecloud.api.group.Group;
import app.simplecloud.api.group.GroupServerType;
import app.simplecloud.api.group.GroupStats;
import app.simplecloud.api.group.ScalingConfig;
import app.simplecloud.api.group.SourceConfig;
import app.simplecloud.api.group.WorkflowsConfig;
import app.simplecloud.api.internal.util.RestModelMapper;
import app.simplecloud.api.server.ServerState;
import app.simplecloud.api.web.models.ModelsServerGroupInfo;
import app.simplecloud.api.web.models.ModelsServerGroupStats;
import app.simplecloud.api.web.models.ModelsServerGroupSummary;
import com.google.gson.Gson;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class GroupImpl implements Group {
    private static final Gson GSON = new Gson();
    private final ModelsServerGroupSummary summaryDelegate;
    private final ModelsServerGroupInfo infoDelegate;
    private DeploymentConfig deployment;
    private ScalingConfig scaling;
    private SourceConfig source;
    private WorkflowsConfig workflows;
    private GroupStats stats;

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
    public Boolean isActive() {
        if (summaryDelegate != null) {
            return summaryDelegate.getActive();
        }
        return null;
    }

    @Override
    @Nullable
    public Integer getPriority() {
        if (summaryDelegate != null) {
            return summaryDelegate.getPriority();
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
                if (deploymentConfig != null && !deploymentConfig.isEmpty()) {
                    config = mapToModel(deploymentConfig, app.simplecloud.api.web.models.ModelsDeploymentConfig.class);
                }
                if (config == null && strategy != null) {
                    config = new app.simplecloud.api.web.models.ModelsDeploymentConfig();
                }
                if (config != null && config.getStrategy() == null && strategy != null) {
                    config.setStrategy(strategy);
                }
            }
            if (config != null) {
                deployment = RestModelMapper.fromModel(config);
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
                if (scalingConfig != null && !scalingConfig.isEmpty()) {
                    config = mapToModel(scalingConfig, app.simplecloud.api.web.models.ModelsScalingConfig.class);
                }
            }
            if (config != null) {
                scaling = RestModelMapper.fromModel(config);
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
                    config.setType(
                            app.simplecloud.api.web.models.ModelsSourceConfig.TypeEnum.fromValue(sourceInfo.getType()));
                    app.simplecloud.api.web.models.ModelsBlueprintInfo blueprintInfo = sourceInfo.getBlueprint();
                    if (blueprintInfo != null) {
                        config.setBlueprint(blueprintInfo.getId());
                    }
                    config.setImage(sourceInfo.getImage());
                }
            }
            if (config != null) {
                source = RestModelMapper.fromModel(config);
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
                if (workflowsConfig != null && !workflowsConfig.isEmpty()) {
                    config = mapToModel(workflowsConfig, app.simplecloud.api.web.models.ModelsWorkflowsConfig.class);
                }
            }
            if (config != null) {
                workflows = RestModelMapper.fromModel(config);
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

    @Override
    @Nullable
    public GroupStats getStats() {
        if (stats == null && summaryDelegate != null && summaryDelegate.getStats() != null) {
            ModelsServerGroupStats model = summaryDelegate.getStats();
            Map<ServerState, Integer> byState = new EnumMap<>(ServerState.class);
            if (model.getServersByState() != null) {
                model.getServersByState().forEach((state, count) -> {
                    if (state == null || count == null) {
                        return;
                    }
                    try {
                        byState.merge(ServerState.parse(state), count, Integer::sum);
                    } catch (IllegalArgumentException ignored) {
                    }
                });
            }
            stats = new GroupStats(
                    RestModelMapper.orZero(model.getTotalServers()),
                    byState,
                    RestModelMapper.orZero(model.getOnlinePlayers()));
        }
        return stats;
    }

    private <T> T mapToModel(Map<String, Object> source, Class<T> type) {
        return GSON.fromJson(GSON.toJson(source), type);
    }
}
