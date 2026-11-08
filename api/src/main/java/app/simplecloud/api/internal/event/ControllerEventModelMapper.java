package app.simplecloud.api.internal.event;

import app.simplecloud.api.blueprint.Blueprint;
import app.simplecloud.api.group.Group;
import app.simplecloud.api.internal.blueprint.BlueprintImpl;
import app.simplecloud.api.internal.group.GroupImpl;
import app.simplecloud.api.internal.group.GroupResources;
import app.simplecloud.api.internal.persistentserver.PersistentServerImpl;
import app.simplecloud.api.internal.server.ServerImpl;
import app.simplecloud.api.internal.util.ProtoConversionUtil;
import app.simplecloud.api.persistentserver.PersistentServer;
import app.simplecloud.api.server.Server;
import app.simplecloud.api.web.models.ModelsBlueprintInfo;
import app.simplecloud.api.web.models.ModelsPersistentServerInfo;
import app.simplecloud.api.web.models.ModelsServerGroupInfo;
import app.simplecloud.api.web.models.ModelsServerSummary;
import app.simplecloud.api.web.models.ModelsSourceConfig;
import app.simplecloud.api.web.models.ModelsSourceInfo;
import app.simplecloud.api.web.models.ModelsWorkflowWhen;
import app.simplecloud.api.web.models.ModelsWorkflowsConfig;
import build.buf.gen.simplecloud.controller.v2.BaseServerConfig;
import build.buf.gen.simplecloud.controller.v2.BlueprintConfig;
import build.buf.gen.simplecloud.controller.v2.PersistentServerConfig;
import build.buf.gen.simplecloud.controller.v2.ServerGroupConfig;
import build.buf.gen.simplecloud.controller.v2.ServerRuntimeInfo;
import build.buf.gen.simplecloud.controller.v2.ServerState;
import com.google.gson.Gson;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ControllerEventModelMapper {
    private static final Gson GSON = new Gson();

    private ControllerEventModelMapper() {}

    public static Group mapGroup(String serverGroupId, ServerGroupConfig config, long timestamp) {
        return new GroupImpl(toGroupInfo(serverGroupId, config, timestamp), toGroupResources(config.getBaseConfig()));
    }

    public static PersistentServer mapPersistentServer(
            String persistentServerId,
            PersistentServerConfig config,
            @Nullable ServerRuntimeInfo runtimeInfo,
            long timestamp) {
        return new PersistentServerImpl(toPersistentServerInfo(persistentServerId, config, runtimeInfo, timestamp));
    }

    public static Blueprint mapBlueprint(BlueprintConfig config, long timestamp) {
        ModelsBlueprintInfo info = toBlueprintInfo(config);
        String eventTimestamp = ProtoConversionUtil.epochSecondsToIso(timestamp);
        info.setCreatedAt(eventTimestamp);
        info.setUpdatedAt(eventTimestamp);
        return BlueprintImpl.fromInfo(info);
    }

    public static Server mapServer(
            String networkId,
            String serverId,
            String serverGroupId,
            String persistentServerId,
            @Nullable BaseServerConfig config,
            @Nullable ServerRuntimeInfo runtimeInfo,
            @Nullable ServerState state,
            @Nullable ServerGroupConfig groupConfig,
            @Nullable PersistentServerConfig persistentServerConfig,
            long timestamp) {
        ModelsServerSummary summary = new ModelsServerSummary();
        summary.setNetworkId(networkId);
        summary.setServerId(serverId);
        String eventTimestamp = ProtoConversionUtil.epochSecondsToIso(timestamp);
        summary.setCreatedAt(eventTimestamp);
        summary.setUpdatedAt(eventTimestamp);
        if (serverGroupId != null && !serverGroupId.isBlank()) {
            summary.setServerGroupId(serverGroupId);
        }
        if (persistentServerId != null && !persistentServerId.isBlank()) {
            summary.setPersistentServerId(persistentServerId);
        }

        applyBaseConfig(summary, config, runtimeInfo);
        applyRuntime(summary, runtimeInfo, state, config);

        ServerImpl server = new ServerImpl(summary);

        if (groupConfig != null && groupConfig.hasBaseConfig()) {
            ModelsServerGroupInfo group = toGroupInfo(serverGroupId, groupConfig, timestamp);
            summary.setServerGroup(group);
            server.setGroup(new GroupImpl(group, toGroupResources(groupConfig.getBaseConfig())));
        }
        if (persistentServerConfig != null && persistentServerConfig.hasBaseConfig()) {
            ModelsPersistentServerInfo persistentServer =
                    toPersistentServerInfo(persistentServerId, persistentServerConfig, runtimeInfo, timestamp);
            summary.setPersistentServer(persistentServer);
            summary.setPersistentServerId(persistentServerId);
            server.setPersistentServer(new PersistentServerImpl(persistentServer));
        }

        return server;
    }

    private static void applyBaseConfig(
            ModelsServerSummary summary, @Nullable BaseServerConfig config, @Nullable ServerRuntimeInfo runtimeInfo) {
        if (config == null) {
            return;
        }
        summary.setMinMemory(config.getMinMemory());
        summary.setMaxMemory(config.getMaxMemory());
        summary.setMaxPlayers(getRuntimeMaxPlayers(runtimeInfo, config));
        if (config.getPropertiesCount() > 0) {
            summary.setProperties(convertProperties(config.getPropertiesMap()));
        }
    }

    private static void applyRuntime(
            ModelsServerSummary summary,
            @Nullable ServerRuntimeInfo runtimeInfo,
            @Nullable ServerState state,
            @Nullable BaseServerConfig config) {
        if (runtimeInfo != null) {
            summary.setNumericalId(runtimeInfo.getNumericalId());
            summary.setIp(runtimeInfo.getIp());
            summary.setPort(runtimeInfo.getPort());
            if (!runtimeInfo.getServerhostId().isBlank()) {
                summary.setServerhostId(runtimeInfo.getServerhostId());
            }
            summary.setPlayerCount(runtimeInfo.getPlayerCount());
        } else {
            summary.setPlayerCount(0);
            if (config != null) {
                summary.setMaxPlayers(config.getMaxPlayers());
            }
        }

        ServerState effectiveState = state;
        if (effectiveState == null && runtimeInfo != null) {
            effectiveState = runtimeInfo.getState();
        }
        if (effectiveState != null) {
            String stateString = ProtoConversionUtil.convertServerStateToString(effectiveState);
            if (stateString != null) {
                summary.setState(ModelsServerSummary.StateEnum.valueOf(stateString));
            }
        }
    }

    private static ModelsServerGroupInfo toGroupInfo(String serverGroupId, ServerGroupConfig config, long timestamp) {
        ModelsServerGroupInfo group = new ModelsServerGroupInfo();
        BaseServerConfig base = config.getBaseConfig();

        group.setId(serverGroupId);
        group.setName(base.getName());
        group.setType(ProtoConversionUtil.convertServerTypeToString(base.getType()));
        group.setProperties(convertProperties(base.getPropertiesMap()));
        group.setTags(new ArrayList<>(base.getTagsList()));
        group.setSource(toSourceInfo(base.getSourceConfig()));
        group.setDeploymentStrategy(toDeploymentStrategy(config));
        group.setDeploymentConfig(toDeploymentConfig(config));
        group.setScalingConfig(toScalingConfig(config));
        group.setWorkflowsConfig(toWorkflowsMap(base.getWorkflows()));
        group.setCreatedAt(ProtoConversionUtil.epochSecondsToIso(timestamp));
        group.setUpdatedAt(ProtoConversionUtil.epochSecondsToIso(timestamp));

        return group;
    }

    private static GroupResources toGroupResources(BaseServerConfig base) {
        return new GroupResources(
                base.getMinMemory(),
                base.getMaxMemory(),
                base.getMaxPlayers(),
                base.getActive(),
                base.hasPriority() ? base.getPriority() : null);
    }

    private static ModelsPersistentServerInfo toPersistentServerInfo(
            String persistentServerId,
            PersistentServerConfig config,
            @Nullable ServerRuntimeInfo runtimeInfo,
            long timestamp) {
        ModelsPersistentServerInfo persistent = new ModelsPersistentServerInfo();
        BaseServerConfig base = config.getBaseConfig();

        persistent.setId(persistentServerId);
        persistent.setName(base.getName());
        persistent.setType(ProtoConversionUtil.convertServerTypeToString(base.getType()));
        persistent.setMinMemory(base.getMinMemory());
        persistent.setMaxMemory(base.getMaxMemory());
        persistent.setMaxPlayers(getRuntimeMaxPlayers(runtimeInfo, base));
        persistent.setActive(base.getActive());
        persistent.setProperties(convertProperties(base.getPropertiesMap()));
        persistent.setTags(new ArrayList<>(base.getTagsList()));
        persistent.setSource(toSourceConfig(base.getSourceConfig()));
        persistent.setWorkflows(toWorkflowsConfig(base.getWorkflows()));

        String serverhostId = config.getDeploymentHost();
        if ((serverhostId == null || serverhostId.isBlank()) && runtimeInfo != null) {
            serverhostId = runtimeInfo.getServerhostId();
        }
        if (serverhostId != null && !serverhostId.isBlank()) {
            persistent.setServerhostId(serverhostId);
        }

        persistent.setCreatedAt(ProtoConversionUtil.epochSecondsToIso(timestamp));
        persistent.setUpdatedAt(ProtoConversionUtil.epochSecondsToIso(timestamp));
        return persistent;
    }

    private static int getRuntimeMaxPlayers(
            @Nullable ServerRuntimeInfo runtimeInfo, @Nullable BaseServerConfig config) {
        if (runtimeInfo != null && runtimeInfo.getMaxPlayers() > 0) {
            return runtimeInfo.getMaxPlayers();
        }
        return config != null ? config.getMaxPlayers() : 0;
    }

    private static Map<String, Object> convertProperties(Map<String, String> properties) {
        Map<String, Object> converted = new HashMap<>();
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            converted.put(entry.getKey(), decodePropertyValue(entry.getValue()));
        }
        return converted;
    }

    private static Object decodePropertyValue(String value) {
        try {
            return GSON.fromJson(value, Object.class);
        } catch (Exception ignored) {
            return value;
        }
    }

    private static String toDeploymentStrategy(ServerGroupConfig config) {
        if (!config.hasDeployment()) {
            return null;
        }
        return ProtoConversionUtil.enumName(config.getDeployment().getStrategy(), "DEPLOYMENT_STRATEGY_");
    }

    private static Map<String, Object> toDeploymentConfig(ServerGroupConfig config) {
        Map<String, Object> deployment = new LinkedHashMap<>();
        if (!config.hasDeployment()) {
            return deployment;
        }

        String strategy = toDeploymentStrategy(config);
        if (strategy != null) {
            deployment.put("strategy", strategy);
        }

        List<Map<String, Object>> hosts = new ArrayList<>();
        for (var host : config.getDeployment().getHostsList()) {
            Map<String, Object> hostMap = new LinkedHashMap<>();
            hostMap.put("name", host.getName());
            hostMap.put("priority", host.getPriority());
            hosts.add(hostMap);
        }
        if (!hosts.isEmpty()) {
            deployment.put("hosts", hosts);
        }
        return deployment;
    }

    private static Map<String, Object> toScalingConfig(ServerGroupConfig config) {
        Map<String, Object> scaling = new LinkedHashMap<>();
        if (!config.hasScaling()) {
            return scaling;
        }

        var protoScaling = config.getScaling();
        scaling.put("min_servers", protoScaling.getMinServers());
        scaling.put("max_servers", protoScaling.getMaxServers());
        scaling.put("available_slots", protoScaling.getAvailableSlots());
        scaling.put("player_threshold", protoScaling.getPlayerThreshold());
        String scalingMode = toScalingMode(protoScaling.getScalingMode());
        if (scalingMode != null) {
            scaling.put("scaling_mode", scalingMode);
        }

        if (protoScaling.hasScaleDown()) {
            Map<String, Object> scaleDown = new LinkedHashMap<>();
            scaleDown.put("idle_time", protoScaling.getScaleDown().getIdleTime());
            scaleDown.put("ignore_players", protoScaling.getScaleDown().getIgnorePlayers());
            scaling.put("scale_down", scaleDown);
        }

        return scaling;
    }

    @Nullable
    private static String toScalingMode(build.buf.gen.simplecloud.controller.v2.ScalingMode mode) {
        String name = ProtoConversionUtil.enumName(mode, "SCALING_MODE_");
        return "SERVER".equals(name) ? "SERVERS" : name;
    }

    private static ModelsSourceConfig toSourceConfig(
            build.buf.gen.simplecloud.controller.v2.SourceConfig sourceConfig) {
        if (sourceConfig == null) {
            return null;
        }
        if (sourceConfig.getSourceType() == build.buf.gen.simplecloud.controller.v2.SourceType.SOURCE_TYPE_UNSPECIFIED
                && !sourceConfig.hasBlueprint()
                && !sourceConfig.hasImage()) {
            return null;
        }

        ModelsSourceConfig result = new ModelsSourceConfig();
        result.setType(ModelsSourceConfig.TypeEnum.fromValue(toSourceType(sourceConfig.getSourceType())));
        if (sourceConfig.hasBlueprint()) {
            result.setBlueprint(sourceConfig.getBlueprint().getBlueprintId());
        }
        if (sourceConfig.hasImage()) {
            result.setImage(sourceConfig.getImage().getImage());
        }
        return result;
    }

    private static ModelsSourceInfo toSourceInfo(build.buf.gen.simplecloud.controller.v2.SourceConfig sourceConfig) {
        if (sourceConfig == null) {
            return null;
        }
        if (sourceConfig.getSourceType() == build.buf.gen.simplecloud.controller.v2.SourceType.SOURCE_TYPE_UNSPECIFIED
                && !sourceConfig.hasBlueprint()
                && !sourceConfig.hasImage()) {
            return null;
        }

        ModelsSourceInfo result = new ModelsSourceInfo();
        result.setType(toSourceType(sourceConfig.getSourceType()));
        if (sourceConfig.hasBlueprint()) {
            result.setBlueprint(toBlueprintInfo(sourceConfig.getBlueprint()));
        }
        if (sourceConfig.hasImage()) {
            result.setImage(sourceConfig.getImage().getImage());
        }
        return result;
    }

    private static String toSourceType(build.buf.gen.simplecloud.controller.v2.SourceType sourceType) {
        String name = sourceType.name();
        if (name.startsWith("SOURCE_TYPE_")) {
            name = name.substring("SOURCE_TYPE_".length());
        }
        return name.toLowerCase(Locale.ROOT);
    }

    private static ModelsBlueprintInfo toBlueprintInfo(
            build.buf.gen.simplecloud.controller.v2.BlueprintConfig blueprint) {
        ModelsBlueprintInfo result = new ModelsBlueprintInfo();
        result.setId(blueprint.getBlueprintId());
        result.setName(blueprint.getName());
        result.setConfigurator(ProtoConversionUtil.emptyToNull(blueprint.getConfigurator()));
        result.setMinecraftVersion(ProtoConversionUtil.emptyToNull(blueprint.getMinecraftVersion()));
        result.setServerSoftware(ProtoConversionUtil.emptyToNull(blueprint.getServerSoftware()));
        result.setServerUrl(ProtoConversionUtil.emptyToNull(blueprint.getServerUrl()));
        result.setSoftwareVersion(ProtoConversionUtil.emptyToNull(blueprint.getSoftwareVersion()));
        result.setWorkflowSteps(new ArrayList<>(blueprint.getWorkflowStepsList()));
        if (blueprint.hasRuntime()) {
            Map<String, Object> runtime = new LinkedHashMap<>();
            String type = ProtoConversionUtil.enumName(blueprint.getRuntime().getType(), "RUNTIME_TYPE_");
            if (type != null) {
                runtime.put("type", type.toLowerCase(Locale.ROOT));
            }
            runtime.put("with", convertProperties(blueprint.getRuntime().getWithMap()));
            result.setRuntimeConfig(runtime);
        }
        return result;
    }

    private static ModelsWorkflowsConfig toWorkflowsConfig(
            build.buf.gen.simplecloud.controller.v2.WorkflowConfig workflowConfig) {
        if (workflowConfig == null) {
            return null;
        }
        if (workflowConfig.getManualCount() == 0 && !workflowConfig.hasWhen()) {
            return null;
        }

        ModelsWorkflowsConfig workflows = new ModelsWorkflowsConfig();
        workflows.setManual(new ArrayList<>(workflowConfig.getManualList()));
        if (workflowConfig.hasWhen()) {
            ModelsWorkflowWhen when = new ModelsWorkflowWhen();
            when.setStart(new ArrayList<>(workflowConfig.getWhen().getStartList()));
            when.setStop(new ArrayList<>(workflowConfig.getWhen().getStopList()));
            workflows.setWhen(when);
        }

        return workflows;
    }

    private static Map<String, Object> toWorkflowsMap(
            build.buf.gen.simplecloud.controller.v2.WorkflowConfig workflowConfig) {
        Map<String, Object> workflows = new LinkedHashMap<>();
        if (workflowConfig == null) {
            return workflows;
        }

        workflows.put("manual", new ArrayList<>(workflowConfig.getManualList()));
        if (workflowConfig.hasWhen()) {
            Map<String, Object> when = new LinkedHashMap<>();
            when.put("start", new ArrayList<>(workflowConfig.getWhen().getStartList()));
            when.put("stop", new ArrayList<>(workflowConfig.getWhen().getStopList()));
            workflows.put("when", when);
        }
        return workflows;
    }
}
