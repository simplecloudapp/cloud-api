package app.simplecloud.api.internal.util;

import app.simplecloud.api.blueprint.RuntimeConfig;
import app.simplecloud.api.group.ScalingConfig;
import app.simplecloud.api.web.models.ModelsBlueprintSummary;
import app.simplecloud.api.web.models.ModelsCreateBlueprintResponse;
import app.simplecloud.api.web.models.ModelsCreatePersistentServerResponse;
import app.simplecloud.api.web.models.ModelsCreateServerGroupResponse;
import app.simplecloud.api.web.models.ModelsPersistentServerSummary;
import app.simplecloud.api.web.models.ModelsRuntimeConfig;
import app.simplecloud.api.web.models.ModelsScaleDownConfig;
import app.simplecloud.api.web.models.ModelsScalingConfig;
import app.simplecloud.api.web.models.ModelsScalingMode;
import app.simplecloud.api.web.models.ModelsServerGroupSummary;
import app.simplecloud.api.web.models.ModelsUpdateBlueprintResponse;

import java.math.BigDecimal;
import java.util.Locale;

public final class RestModelMapper {

    private RestModelMapper() {}

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
            result.setScalingMode(ModelsScalingMode.fromValue(config.getScalingMode().name()));
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
}
