package app.simplecloud.api.internal.create;

import app.simplecloud.api.blueprint.CreateBlueprintRequest;
import app.simplecloud.api.blueprint.RuntimeConfig;
import app.simplecloud.api.blueprint.RuntimeType;
import app.simplecloud.api.group.DeploymentConfig;
import app.simplecloud.api.group.DeploymentHost;
import app.simplecloud.api.group.DeploymentStrategy;
import app.simplecloud.api.group.GroupServerType;
import app.simplecloud.api.group.ScaleDownConfig;
import app.simplecloud.api.group.ScalingConfig;
import app.simplecloud.api.group.ScalingMode;
import app.simplecloud.api.group.WorkflowWhen;
import app.simplecloud.api.group.WorkflowsConfig;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Applies dashboard-aligned defaults to create requests before converting them to controller payloads.
 */
public final class CreateRequestDefaults {
    private static final int DEFAULT_MAX_PLAYERS = 50;
    private static final double DEFAULT_PLAYER_THRESHOLD = 0.75d;
    private static final String DEFAULT_SCALE_DOWN_IDLE_TIME = "3m";
    private static final String DEFAULT_JAVA_VERSION = "21";
    private static final List<String> DEFAULT_WORKFLOW_START = List.of("internal/setup");
    private static final List<String> DEFAULT_WORKFLOW_STOP = List.of("internal/cleanup");
    private static final List<String> DEFAULT_MANUAL_WORKFLOWS = List.of("default/backup", "default/copy-to-template");
    private static final List<String> DEFAULT_JAVA_OPTIONS = List.of("-Dcom.mojang.eula.agree=true");
    private static final List<String> DEFAULT_JAVA_ARGS = List.of("nogui");

    public GroupServerType defaultType(@Nullable GroupServerType type) {
        return type != null ? type : GroupServerType.SERVER;
    }

    public int defaultMaxPlayers(@Nullable Integer maxPlayers) {
        return maxPlayers != null ? maxPlayers : DEFAULT_MAX_PLAYERS;
    }

    public boolean defaultActive(@Nullable Boolean active) {
        return active != null ? active : true;
    }

    public Map<String, Object> defaultProperties(@Nullable Map<String, Object> properties) {
        return properties != null ? properties : Map.of();
    }

    public List<String> defaultTags(String entityName, @Nullable List<String> tags) {
        if (tags != null && !tags.isEmpty()) {
            return List.copyOf(tags);
        }
        return List.of(entityName);
    }

    public WorkflowsConfig defaultWorkflows(@Nullable WorkflowsConfig workflows) {
        WorkflowWhen sourceWhen = workflows != null ? workflows.getWhen() : null;

        WorkflowWhen effectiveWhen = new WorkflowWhen();
        effectiveWhen.setStart(copyOrDefault(sourceWhen != null ? sourceWhen.getStart() : null, DEFAULT_WORKFLOW_START));
        effectiveWhen.setStop(copyOrDefault(sourceWhen != null ? sourceWhen.getStop() : null, DEFAULT_WORKFLOW_STOP));

        WorkflowsConfig effective = new WorkflowsConfig();
        effective.setWhen(effectiveWhen);
        effective.setManual(copyOrDefault(workflows != null ? workflows.getManual() : null, DEFAULT_MANUAL_WORKFLOWS));
        return effective;
    }

    public DeploymentConfig defaultDeployment(@Nullable DeploymentConfig deployment) {
        DeploymentConfig effective = new DeploymentConfig();
        effective.setStrategy(deployment != null && deployment.getStrategy() != null
                ? deployment.getStrategy()
                : DeploymentStrategy.BLACKLIST);
        effective.setHosts(copyHosts(deployment != null ? deployment.getHosts() : null));
        return effective;
    }

    public ScalingConfig defaultGroupScaling(@Nullable ScalingConfig scaling) {
        ScalingConfig effective = new ScalingConfig();
        if (scaling == null) {
            effective.setMinServers(0);
            effective.setMaxServers(0);
            effective.setPlayerThreshold(DEFAULT_PLAYER_THRESHOLD);
            effective.setScalingMode(ScalingMode.SLOTS);
            effective.setScaleDown(defaultScaleDown(null));
            return effective;
        }

        double playerThreshold = scaling.hasPlayerThreshold()
                ? scaling.getPlayerThreshold()
                : DEFAULT_PLAYER_THRESHOLD;
        if (scaling.hasPlayerThreshold() && (playerThreshold < 0 || playerThreshold > 1)) {
            throw new IllegalArgumentException("playerThreshold must be between 0 and 1");
        }

        effective.setAvailableSlots(scaling.getAvailableSlots());
        effective.setMinServers(scaling.getMinServers());
        effective.setMaxServers(scaling.getMaxServers());
        effective.setPlayerThreshold(playerThreshold);
        effective.setScalingMode(scaling.getScalingMode() != null ? scaling.getScalingMode() : ScalingMode.SLOTS);
        effective.setScaleDown(defaultScaleDown(scaling.getScaleDown()));
        return effective;
    }

    @Nullable
    public CreateBlueprintRequest defaultInlineBlueprint(@Nullable CreateBlueprintRequest createBlueprint,
                                                         WorkflowsConfig workflows) {
        if (createBlueprint == null) {
            return null;
        }

        CreateBlueprintRequest effective = new CreateBlueprintRequest();
        effective.setConfigurator(createBlueprint.getConfigurator());
        effective.setMinecraftVersion(createBlueprint.getMinecraftVersion());
        effective.setRuntimeConfig(defaultRuntimeConfig(createBlueprint.getRuntimeConfig()));
        effective.setServerSoftware(createBlueprint.getServerSoftware());
        effective.setServerUrl(createBlueprint.getServerUrl());
        effective.setSoftwareVersion(createBlueprint.getSoftwareVersion());
        effective.setWorkflowSteps(createBlueprint.getWorkflowSteps() != null
                ? List.copyOf(createBlueprint.getWorkflowSteps())
                : List.copyOf(workflows.getWhen().getStart()));
        return effective;
    }

    private ScaleDownConfig defaultScaleDown(@Nullable ScaleDownConfig scaleDown) {
        ScaleDownConfig effective = new ScaleDownConfig();
        effective.setIdleTime(scaleDown != null && scaleDown.getIdleTime() != null
                ? scaleDown.getIdleTime()
                : DEFAULT_SCALE_DOWN_IDLE_TIME);
        effective.setIgnorePlayers(scaleDown == null || !scaleDown.hasIgnorePlayers() || scaleDown.isIgnorePlayers());
        return effective;
    }

    private RuntimeConfig defaultRuntimeConfig(@Nullable RuntimeConfig runtimeConfig) {
        RuntimeConfig effective = new RuntimeConfig();
        RuntimeType effectiveType = runtimeConfig != null && runtimeConfig.getType() != null
                ? runtimeConfig.getType()
                : RuntimeType.JAVA;
        effective.setType(effectiveType);

        Map<String, Object> runtimeWith = new LinkedHashMap<>();
        if (effectiveType == RuntimeType.JAVA) {
            runtimeWith.put("version", DEFAULT_JAVA_VERSION);
            runtimeWith.put("options", DEFAULT_JAVA_OPTIONS);
            runtimeWith.put("args", DEFAULT_JAVA_ARGS);
        }

        boolean preserveExplicitWith = runtimeConfig != null && runtimeConfig.getWith() != null;
        if (preserveExplicitWith) {
            runtimeWith.putAll(runtimeConfig.getWith());
        }
        effective.setWith(!runtimeWith.isEmpty() || preserveExplicitWith ? runtimeWith : null);
        return effective;
    }

    private DeploymentHost[] copyHosts(@Nullable DeploymentHost[] hosts) {
        if (hosts == null || hosts.length == 0) {
            return new DeploymentHost[0];
        }

        DeploymentHost[] copy = new DeploymentHost[hosts.length];
        for (int i = 0; i < hosts.length; i++) {
            DeploymentHost source = hosts[i];
            DeploymentHost target = new DeploymentHost();
            if (source != null) {
                target.setName(source.getName());
                target.setPriority(source.getPriority());
            }
            copy[i] = target;
        }
        return copy;
    }

    private List<String> copyOrDefault(@Nullable List<String> values, List<String> fallback) {
        return values != null ? List.copyOf(values) : fallback;
    }
}
