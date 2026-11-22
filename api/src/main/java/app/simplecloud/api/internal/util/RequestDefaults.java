package app.simplecloud.api.internal.util;

import app.simplecloud.api.blueprint.CreateBlueprintRequest;
import app.simplecloud.api.blueprint.RuntimeConfig;
import app.simplecloud.api.blueprint.RuntimeType;
import app.simplecloud.api.group.DeploymentConfig;
import app.simplecloud.api.group.DeploymentStrategy;
import app.simplecloud.api.group.GroupServerType;
import app.simplecloud.api.group.ScaleDownConfig;
import app.simplecloud.api.group.ScalingConfig;
import app.simplecloud.api.group.ScalingMode;
import app.simplecloud.api.group.WorkflowWhen;
import app.simplecloud.api.group.WorkflowsConfig;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jetbrains.annotations.Nullable;

/**
 * Applies dashboard defaults to create requests.
 */
public final class RequestDefaults {
    private static final int DEFAULT_MAX_PLAYERS = 50;
    private static final int DEFAULT_MAX_SERVERS = 1;
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
        WorkflowWhen sourceWhen = workflows != null ? workflows.when() : null;

        WorkflowWhen effectiveWhen = WorkflowWhen.builder()
                .start(copyOrDefault(sourceWhen != null ? sourceWhen.start() : null, DEFAULT_WORKFLOW_START))
                .stop(copyOrDefault(sourceWhen != null ? sourceWhen.stop() : null, DEFAULT_WORKFLOW_STOP))
                .build();

        return WorkflowsConfig.builder()
                .when(effectiveWhen)
                .manual(copyOrDefault(workflows != null ? workflows.manual() : null, DEFAULT_MANUAL_WORKFLOWS))
                .build();
    }

    public DeploymentConfig defaultDeployment(@Nullable DeploymentConfig deployment) {
        return DeploymentConfig.builder()
                .strategy(
                        deployment != null && deployment.strategy() != null
                                ? deployment.strategy()
                                : DeploymentStrategy.BLACKLIST)
                .hosts(deployment != null && deployment.hosts() != null ? deployment.hosts() : List.of())
                .build();
    }

    public ScalingConfig defaultGroupScaling(@Nullable ScalingConfig scaling) {
        if (scaling == null) {
            return ScalingConfig.builder()
                    .minServers(0)
                    .maxServers(DEFAULT_MAX_SERVERS)
                    .playerThreshold(DEFAULT_PLAYER_THRESHOLD)
                    .scalingMode(ScalingMode.SLOTS)
                    .scaleDown(defaultScaleDown(null))
                    .build();
        }

        double playerThreshold = scaling.hasPlayerThreshold() ? scaling.playerThreshold() : DEFAULT_PLAYER_THRESHOLD;
        if (scaling.hasPlayerThreshold() && (playerThreshold <= 0 || playerThreshold > 1)) {
            throw new IllegalArgumentException("playerThreshold must be greater than 0 and at most 1");
        }

        return ScalingConfig.builder()
                .availableSlots(orZero(scaling.availableSlots()))
                .minServers(orZero(scaling.minServers()))
                .maxServers(orZero(scaling.maxServers()))
                .playerThreshold(playerThreshold)
                .scalingMode(scaling.scalingMode() != null ? scaling.scalingMode() : ScalingMode.SLOTS)
                .scaleDown(defaultScaleDown(scaling.scaleDown()))
                .build();
    }

    @Nullable public CreateBlueprintRequest defaultInlineBlueprint(
            @Nullable CreateBlueprintRequest createBlueprint, WorkflowsConfig workflows) {
        if (createBlueprint == null) {
            return null;
        }

        return CreateBlueprintRequest.builder()
                .name(createBlueprint.getName())
                .configurator(createBlueprint.getConfigurator())
                .minecraftVersion(createBlueprint.getMinecraftVersion())
                .runtimeConfig(defaultRuntimeConfig(createBlueprint.getRuntimeConfig()))
                .serverSoftware(createBlueprint.getServerSoftware())
                .serverUrl(createBlueprint.getServerUrl())
                .softwareVersion(createBlueprint.getSoftwareVersion())
                .workflowSteps(
                        createBlueprint.getWorkflowSteps() != null
                                ? List.copyOf(createBlueprint.getWorkflowSteps())
                                : List.copyOf(Objects.requireNonNull(
                                        Objects.requireNonNull(workflows.when()).start())))
                .build();
    }

    public CreateBlueprintRequest defaultBlueprint(CreateBlueprintRequest blueprint) {
        return CreateBlueprintRequest.builder()
                .name(blueprint.getName())
                .configurator(blueprint.getConfigurator())
                .minecraftVersion(blueprint.getMinecraftVersion())
                .runtimeConfig(defaultRuntimeConfig(blueprint.getRuntimeConfig()))
                .serverSoftware(blueprint.getServerSoftware())
                .serverUrl(blueprint.getServerUrl())
                .softwareVersion(blueprint.getSoftwareVersion())
                .workflowSteps(
                        blueprint.getWorkflowSteps() != null
                                ? List.copyOf(blueprint.getWorkflowSteps())
                                : DEFAULT_WORKFLOW_START)
                .build();
    }

    private ScaleDownConfig defaultScaleDown(@Nullable ScaleDownConfig scaleDown) {
        return ScaleDownConfig.builder()
                .idleTime(
                        scaleDown != null && scaleDown.idleTime() != null
                                ? scaleDown.idleTime()
                                : DEFAULT_SCALE_DOWN_IDLE_TIME)
                .ignorePlayers(scaleDown == null || !Boolean.FALSE.equals(scaleDown.ignorePlayers()))
                .build();
    }

    private RuntimeConfig defaultRuntimeConfig(@Nullable RuntimeConfig runtimeConfig) {
        RuntimeType effectiveType =
                runtimeConfig != null && runtimeConfig.type() != null ? runtimeConfig.type() : RuntimeType.JAVA;

        Map<String, Object> runtimeWith = new LinkedHashMap<>();
        if (effectiveType == RuntimeType.JAVA) {
            runtimeWith.put("version", DEFAULT_JAVA_VERSION);
            runtimeWith.put("options", DEFAULT_JAVA_OPTIONS);
            runtimeWith.put("args", DEFAULT_JAVA_ARGS);
        }

        boolean preserveExplicitWith = runtimeConfig != null && runtimeConfig.with() != null;
        if (preserveExplicitWith) {
            runtimeWith.putAll(runtimeConfig.with());
        }
        return RuntimeConfig.builder()
                .type(effectiveType)
                .with(!runtimeWith.isEmpty() || preserveExplicitWith ? runtimeWith : null)
                .build();
    }

    private static int orZero(@Nullable Integer value) {
        return value != null ? value : 0;
    }

    private List<String> copyOrDefault(@Nullable List<String> values, List<String> fallback) {
        return values != null ? List.copyOf(values) : fallback;
    }
}
