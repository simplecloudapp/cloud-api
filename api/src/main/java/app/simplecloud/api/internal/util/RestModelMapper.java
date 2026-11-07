package app.simplecloud.api.internal.util;

import app.simplecloud.api.blueprint.RuntimeConfig;
import app.simplecloud.api.group.DeploymentConfig;
import app.simplecloud.api.group.DeploymentHost;
import app.simplecloud.api.group.DeploymentStrategy;
import app.simplecloud.api.group.ScaleDownConfig;
import app.simplecloud.api.group.ScalingConfig;
import app.simplecloud.api.group.ScalingMode;
import app.simplecloud.api.group.SourceConfig;
import app.simplecloud.api.group.SourceType;
import app.simplecloud.api.group.WorkflowWhen;
import app.simplecloud.api.group.WorkflowsConfig;
import app.simplecloud.api.web.models.ModelsBlueprintSummary;
import app.simplecloud.api.web.models.ModelsCreateBlueprintResponse;
import app.simplecloud.api.web.models.ModelsCreatePersistentServerResponse;
import app.simplecloud.api.web.models.ModelsCreateServerGroupResponse;
import app.simplecloud.api.web.models.ModelsDeploymentConfig;
import app.simplecloud.api.web.models.ModelsDeploymentHost;
import app.simplecloud.api.web.models.ModelsPersistentServerSummary;
import app.simplecloud.api.web.models.ModelsRuntimeConfig;
import app.simplecloud.api.web.models.ModelsScaleDownConfig;
import app.simplecloud.api.web.models.ModelsScalingConfig;
import app.simplecloud.api.web.models.ModelsScalingMode;
import app.simplecloud.api.web.models.ModelsServerGroupSummary;
import app.simplecloud.api.web.models.ModelsSourceConfig;
import app.simplecloud.api.web.models.ModelsUpdateBlueprintResponse;
import app.simplecloud.api.web.models.ModelsUpdateServerGroupResponse;
import app.simplecloud.api.web.models.ModelsWorkflowWhen;
import app.simplecloud.api.web.models.ModelsWorkflowsConfig;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.Locale;

public final class RestModelMapper {

    private RestModelMapper() {}

    public static int orZero(@Nullable Integer value) {
        return value != null ? value : 0;
    }

    public static ModelsServerGroupSummary toSummary(ModelsCreateServerGroupResponse response) {
        ModelsServerGroupSummary summary = new ModelsServerGroupSummary();
        summary.setServerGroupId(response.getServerGroupId());
        summary.setName(response.getName());
        summary.setType(response.getType());
        summary.setMinMemory(response.getMinMemory());
        summary.setMaxMemory(response.getMaxMemory());
        summary.setMaxPlayers(response.getMaxPlayers());
        summary.setActive(response.getActive());
        summary.setPriority(response.getPriority());
        summary.setDeployment(response.getDeployment());
        summary.setScaling(response.getScaling());
        summary.setSource(response.getSource());
        summary.setWorkflows(response.getWorkflows());
        summary.setProperties(response.getProperties());
        summary.setTags(response.getTags());
        summary.setCreatedAt(response.getCreatedAt());
        summary.setUpdatedAt(response.getUpdatedAt());
        return summary;
    }

    public static ModelsServerGroupSummary toSummary(ModelsUpdateServerGroupResponse response) {
        ModelsServerGroupSummary summary = new ModelsServerGroupSummary();
        summary.setServerGroupId(response.getServerGroupId());
        summary.setName(response.getName());
        summary.setType(response.getType());
        summary.setMinMemory(response.getMinMemory());
        summary.setMaxMemory(response.getMaxMemory());
        summary.setMaxPlayers(response.getMaxPlayers());
        summary.setActive(response.getActive());
        summary.setPriority(response.getPriority());
        summary.setDeployment(response.getDeployment());
        summary.setScaling(response.getScaling());
        summary.setSource(response.getSource());
        summary.setWorkflows(response.getWorkflows());
        summary.setProperties(response.getProperties());
        summary.setTags(response.getTags());
        summary.setCreatedAt(response.getCreatedAt());
        summary.setUpdatedAt(response.getUpdatedAt());
        return summary;
    }

    public static ModelsPersistentServerSummary toSummary(ModelsCreatePersistentServerResponse response) {
        ModelsPersistentServerSummary summary = new ModelsPersistentServerSummary();
        summary.setPersistentServerId(response.getPersistentServerId());
        summary.setName(response.getName());
        summary.setType(response.getType());
        summary.setMinMemory(response.getMinMemory());
        summary.setMaxMemory(response.getMaxMemory());
        summary.setMaxPlayers(response.getMaxPlayers());
        summary.setActive(response.getActive());
        summary.setPriority(response.getPriority());
        summary.setPlayerCount(response.getPlayerCount());
        summary.setServerhostId(response.getServerhostId());
        summary.setSource(response.getSource());
        summary.setWorkflows(response.getWorkflows());
        summary.setProperties(response.getProperties());
        summary.setTags(response.getTags());
        summary.setCreatedAt(response.getCreatedAt());
        summary.setUpdatedAt(response.getUpdatedAt());
        return summary;
    }

    public static ModelsBlueprintSummary toSummary(ModelsCreateBlueprintResponse response) {
        ModelsBlueprintSummary summary = new ModelsBlueprintSummary();
        summary.setBlueprintId(response.getBlueprintId());
        summary.setName(response.getName());
        summary.setConfigurator(response.getConfigurator());
        summary.setMinecraftVersion(response.getMinecraftVersion());
        summary.setServerSoftware(response.getServerSoftware());
        summary.setServerUrl(response.getServerUrl());
        summary.setSoftwareVersion(response.getSoftwareVersion());
        summary.setRuntimeConfig(response.getRuntimeConfig());
        summary.setWorkflowSteps(response.getWorkflowSteps());
        summary.setCreatedAt(response.getCreatedAt());
        summary.setUpdatedAt(response.getUpdatedAt());
        return summary;
    }

    public static ModelsBlueprintSummary toSummary(ModelsUpdateBlueprintResponse response) {
        ModelsBlueprintSummary summary = new ModelsBlueprintSummary();
        summary.setBlueprintId(response.getBlueprintId());
        summary.setName(response.getName());
        summary.setConfigurator(response.getConfigurator());
        summary.setMinecraftVersion(response.getMinecraftVersion());
        summary.setServerSoftware(response.getServerSoftware());
        summary.setServerUrl(response.getServerUrl());
        summary.setSoftwareVersion(response.getSoftwareVersion());
        summary.setRuntimeConfig(response.getRuntimeConfig());
        summary.setWorkflowSteps(response.getWorkflowSteps());
        summary.setCreatedAt(response.getCreatedAt());
        summary.setUpdatedAt(response.getUpdatedAt());
        return summary;
    }

    public static ModelsScalingConfig toModel(ScalingConfig config) {
        ModelsScalingConfig result = new ModelsScalingConfig();
        result.setAvailableSlots(config.getAvailableSlots());
        result.setMaxServers(config.getMaxServers());
        result.setMinServers(config.getMinServers());
        result.setPlayerThreshold(BigDecimal.valueOf(config.getPlayerThreshold()));
        if (config.getScalingMode() != null) {
            result.setScalingMode(
                    ModelsScalingMode.fromValue(config.getScalingMode().name()));
        }

        if (config.getScaleDown() != null) {
            ModelsScaleDownConfig scaleDown = new ModelsScaleDownConfig();
            scaleDown.setIdleTime(config.getScaleDown().getIdleTime());
            scaleDown.setIgnorePlayers(config.getScaleDown().isIgnorePlayers());
            result.setScaleDown(scaleDown);
        }

        return result;
    }

    public static ModelsRuntimeConfig toModel(RuntimeConfig runtimeConfig) {
        ModelsRuntimeConfig result = new ModelsRuntimeConfig();
        if (runtimeConfig.getType() != null) {
            result.setType(runtimeConfig.getType().name().toLowerCase(Locale.ROOT));
        }
        result.setWith(runtimeConfig.getWith());
        return result;
    }

    public static ModelsDeploymentConfig toModel(DeploymentConfig config) {
        ModelsDeploymentConfig result = new ModelsDeploymentConfig();
        if (config.getStrategy() != null) {
            result.setStrategy(config.getStrategy().name().toLowerCase(Locale.ROOT));
        }
        if (config.getHosts() != null) {
            for (DeploymentHost host : config.getHosts()) {
                result.addHostsItem(toModel(host));
            }
        }
        return result;
    }

    public static ModelsDeploymentHost toModel(DeploymentHost host) {
        ModelsDeploymentHost result = new ModelsDeploymentHost();
        result.setName(host.getName());
        result.setPriority(host.getPriority());
        return result;
    }

    public static ModelsSourceConfig toModel(SourceConfig config) {
        ModelsSourceConfig result = new ModelsSourceConfig();
        if (config.getType() != null) {
            result.setType(ModelsSourceConfig.TypeEnum.valueOf(config.getType().name()));
        }
        result.setBlueprint(config.getBlueprint());
        result.setImage(config.getImage());
        return result;
    }

    public static ModelsWorkflowsConfig toModel(WorkflowsConfig config) {
        ModelsWorkflowsConfig result = new ModelsWorkflowsConfig();
        result.setManual(config.getManual());
        if (config.getWhen() != null) {
            ModelsWorkflowWhen when = new ModelsWorkflowWhen();
            when.setStart(config.getWhen().getStart());
            when.setStop(config.getWhen().getStop());
            result.setWhen(when);
        }
        return result;
    }

    public static DeploymentConfig fromModel(ModelsDeploymentConfig config) {
        DeploymentConfig result = new DeploymentConfig();
        if (config.getStrategy() != null) {
            try {
                result.setStrategy(
                        DeploymentStrategy.valueOf(config.getStrategy().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (config.getHosts() != null) {
            result.setHosts(config.getHosts().stream()
                    .map(model -> {
                        DeploymentHost host = new DeploymentHost();
                        host.setName(model.getName());
                        host.setPriority(orZero(model.getPriority()));
                        return host;
                    })
                    .toArray(DeploymentHost[]::new));
        }
        return result;
    }

    public static ScalingConfig fromModel(ModelsScalingConfig config) {
        ScalingConfig result = new ScalingConfig();
        result.setAvailableSlots(orZero(config.getAvailableSlots()));
        result.setMaxServers(orZero(config.getMaxServers()));
        result.setMinServers(orZero(config.getMinServers()));
        if (config.getPlayerThreshold() != null) {
            result.setPlayerThreshold(config.getPlayerThreshold().doubleValue());
        }
        if (config.getScalingMode() != null) {
            switch (config.getScalingMode().getValue()) {
                case "SLOTS" -> result.setScalingMode(ScalingMode.SLOTS);
                case "SERVERS" -> result.setScalingMode(ScalingMode.SERVERS);
                default -> {}
            }
        }
        if (config.getScaleDown() != null) {
            ScaleDownConfig scaleDown = new ScaleDownConfig();
            scaleDown.setIdleTime(config.getScaleDown().getIdleTime());
            if (config.getScaleDown().getIgnorePlayers() != null) {
                scaleDown.setIgnorePlayers(config.getScaleDown().getIgnorePlayers());
            }
            result.setScaleDown(scaleDown);
        }
        return result;
    }

    public static SourceConfig fromModel(ModelsSourceConfig config) {
        SourceConfig result = new SourceConfig();
        if (config.getType() != null && config.getType().getValue() != null) {
            try {
                result.setType(SourceType.valueOf(config.getType().getValue().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        result.setBlueprint(config.getBlueprint());
        result.setImage(config.getImage());
        return result;
    }

    public static WorkflowsConfig fromModel(ModelsWorkflowsConfig config) {
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
