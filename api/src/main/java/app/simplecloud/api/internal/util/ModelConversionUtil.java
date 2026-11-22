package app.simplecloud.api.internal.util;

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
import app.simplecloud.api.web.models.ModelsDeploymentConfig;
import app.simplecloud.api.web.models.ModelsDeploymentHost;
import app.simplecloud.api.web.models.ModelsExecuteManualWorkflowFailure;
import app.simplecloud.api.web.models.ModelsScaleDownConfig;
import app.simplecloud.api.web.models.ModelsScalingConfig;
import app.simplecloud.api.web.models.ModelsScalingMode;
import app.simplecloud.api.web.models.ModelsSourceConfig;
import app.simplecloud.api.web.models.ModelsWorkflowWhen;
import app.simplecloud.api.web.models.ModelsWorkflowsConfig;
import app.simplecloud.api.workflow.WorkflowExecutionResult;
import app.simplecloud.api.workflow.WorkflowFailure;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.jetbrains.annotations.Nullable;

/**
 * Converts shared group and persistent server types between the API and the generated REST models.
 */
public final class ModelConversionUtil {

    private ModelConversionUtil() {}

    public static @Nullable ModelsSourceConfig toModel(@Nullable SourceConfig config) {
        if (config == null) {
            return null;
        }
        ModelsSourceConfig result = new ModelsSourceConfig();
        if (config.type() != null) {
            result.setType(ModelsSourceConfig.TypeEnum.valueOf(config.type().name()));
        }
        result.setBlueprint(config.blueprint());
        result.setImage(config.image());
        return result;
    }

    public static @Nullable ModelsWorkflowsConfig toModel(@Nullable WorkflowsConfig config) {
        if (config == null) {
            return null;
        }
        ModelsWorkflowsConfig result = new ModelsWorkflowsConfig();
        result.setManual(config.manual());
        if (config.when() != null) {
            ModelsWorkflowWhen when = new ModelsWorkflowWhen();
            when.setStart(config.when().start());
            when.setStop(config.when().stop());
            result.setWhen(when);
        }
        return result;
    }

    public static @Nullable ModelsDeploymentConfig toModel(@Nullable DeploymentConfig config) {
        if (config == null) {
            return null;
        }
        ModelsDeploymentConfig result = new ModelsDeploymentConfig();
        if (config.strategy() != null) {
            result.setStrategy(toModel(config.strategy()));
        }
        if (config.hosts() != null) {
            for (DeploymentHost host : config.hosts()) {
                ModelsDeploymentHost modelHost = new ModelsDeploymentHost();
                modelHost.setName(host.name());
                modelHost.setPriority(host.priority());
                result.addHostsItem(modelHost);
            }
        }
        return result;
    }

    public static @Nullable ModelsScalingConfig toModel(@Nullable ScalingConfig config) {
        if (config == null) {
            return null;
        }
        ModelsScalingConfig result = new ModelsScalingConfig();
        result.setAvailableSlots(orZero(config.availableSlots()));
        result.setMaxServers(orZero(config.maxServers()));
        result.setMinServers(orZero(config.minServers()));
        result.setPlayerThreshold(BigDecimal.valueOf(config.playerThreshold() != null ? config.playerThreshold() : 0));
        if (config.scalingMode() != null) {
            result.setScalingMode(toModel(config.scalingMode()));
        }
        if (config.scaleDown() != null) {
            ModelsScaleDownConfig scaleDown = new ModelsScaleDownConfig();
            scaleDown.setIdleTime(config.scaleDown().idleTime());
            scaleDown.setIgnorePlayers(Boolean.TRUE.equals(config.scaleDown().ignorePlayers()));
            result.setScaleDown(scaleDown);
        }
        return result;
    }

    public static @Nullable SourceConfig fromModel(@Nullable ModelsSourceConfig config) {
        if (config == null) {
            return null;
        }
        SourceType type = config.getType() != null
                ? parseEnum(SourceType.class, config.getType().getValue())
                : null;
        return new SourceConfig(type, config.getBlueprint(), config.getImage());
    }

    public static @Nullable WorkflowsConfig fromModel(@Nullable ModelsWorkflowsConfig config) {
        if (config == null) {
            return null;
        }
        WorkflowWhen when = config.getWhen() != null
                ? new WorkflowWhen(config.getWhen().getStart(), config.getWhen().getStop())
                : null;
        return new WorkflowsConfig(config.getManual(), when);
    }

    public static @Nullable DeploymentConfig fromModel(@Nullable ModelsDeploymentConfig config) {
        if (config == null) {
            return null;
        }
        DeploymentStrategy strategy =
                config.getStrategy() != null ? parseEnum(DeploymentStrategy.class, config.getStrategy()) : null;
        List<DeploymentHost> hosts = config.getHosts() != null
                ? config.getHosts().stream()
                        .map(modelHost -> new DeploymentHost(modelHost.getName(), orZero(modelHost.getPriority())))
                        .toList()
                : null;
        return new DeploymentConfig(hosts, strategy);
    }

    public static @Nullable ScalingConfig fromModel(@Nullable ModelsScalingConfig config) {
        if (config == null) {
            return null;
        }
        ScaleDownConfig scaleDown = config.getScaleDown() != null
                ? new ScaleDownConfig(
                        config.getScaleDown().getIdleTime(),
                        config.getScaleDown().getIgnorePlayers())
                : null;
        return new ScalingConfig(
                orZero(config.getAvailableSlots()),
                orZero(config.getMaxServers()),
                orZero(config.getMinServers()),
                config.getPlayerThreshold() != null
                        ? config.getPlayerThreshold().doubleValue()
                        : null,
                scaleDown,
                config.getScalingMode() != null ? fromModel(config.getScalingMode()) : null);
    }

    private static String toModel(DeploymentStrategy strategy) {
        return switch (strategy) {
            case WHITELIST -> "whitelist";
            case BLACKLIST -> "blacklist";
            case RANDOM, PRIORITY, ROUND_ROBIN ->
                throw new IllegalArgumentException(
                        "DeploymentStrategy." + strategy.name()
                                + " is not supported by the controller REST API. Supported strategies are WHITELIST and BLACKLIST.");
        };
    }

    private static ModelsScalingMode toModel(ScalingMode scalingMode) {
        return switch (scalingMode) {
            case SLOTS -> ModelsScalingMode.fromValue("SLOTS");
            case PLAYERS -> ModelsScalingMode.fromValue("SERVERS");
            case MANUAL ->
                throw new IllegalArgumentException(
                        "ScalingMode.MANUAL is not supported by the controller REST API. Supported modes are SLOTS and PLAYERS.");
        };
    }

    private static @Nullable ScalingMode fromModel(ModelsScalingMode scalingMode) {
        return switch (scalingMode.getValue()) {
            case "SLOTS" -> ScalingMode.SLOTS;
            case "SERVERS" -> ScalingMode.PLAYERS;
            default -> null;
        };
    }

    private static <E extends Enum<E>> @Nullable E parseEnum(Class<E> type, String value) {
        try {
            return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static WorkflowExecutionResult toWorkflowExecutionResult(
            String requestedWorkflowId,
            @Nullable String workflowId,
            @Nullable Integer totalServers,
            @Nullable List<String> succeededServerIds,
            @Nullable List<String> skippedServerIds,
            @Nullable List<ModelsExecuteManualWorkflowFailure> failedServers) {
        List<WorkflowFailure> failures =
                Objects.requireNonNullElse(failedServers, List.<ModelsExecuteManualWorkflowFailure>of()).stream()
                        .map(failure -> new WorkflowFailure(
                                Objects.requireNonNullElse(failure.getServerId(), ""), failure.getErrorMessage()))
                        .toList();
        return new WorkflowExecutionResult(
                Objects.requireNonNullElse(workflowId, requestedWorkflowId),
                totalServers != null ? totalServers : 0,
                Objects.requireNonNullElse(succeededServerIds, List.of()),
                Objects.requireNonNullElse(skippedServerIds, List.of()),
                failures);
    }

    private static int orZero(@Nullable Integer value) {
        return value != null ? value : 0;
    }
}
