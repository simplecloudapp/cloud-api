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
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Converts shared group and persistent server types between the API and the generated REST models.
 */
public final class ModelConversionUtil {

    private ModelConversionUtil() {
    }

    public static @Nullable ModelsSourceConfig toModel(@Nullable SourceConfig config) {
        if (config == null) {
            return null;
        }
        ModelsSourceConfig result = new ModelsSourceConfig();
        if (config.getType() != null) {
            result.setType(ModelsSourceConfig.TypeEnum.valueOf(config.getType().name()));
        }
        result.setBlueprint(config.getBlueprint());
        result.setImage(config.getImage());
        return result;
    }

    public static @Nullable ModelsWorkflowsConfig toModel(@Nullable WorkflowsConfig config) {
        if (config == null) {
            return null;
        }
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

    public static @Nullable ModelsDeploymentConfig toModel(@Nullable DeploymentConfig config) {
        if (config == null) {
            return null;
        }
        ModelsDeploymentConfig result = new ModelsDeploymentConfig();
        if (config.getStrategy() != null) {
            result.setStrategy(toModel(config.getStrategy()));
        }
        if (config.getHosts() != null) {
            for (DeploymentHost host : config.getHosts()) {
                ModelsDeploymentHost modelHost = new ModelsDeploymentHost();
                modelHost.setName(host.getName());
                modelHost.setPriority(host.getPriority());
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
        result.setAvailableSlots(config.getAvailableSlots());
        result.setMaxServers(config.getMaxServers());
        result.setMinServers(config.getMinServers());
        result.setPlayerThreshold(BigDecimal.valueOf(config.getPlayerThreshold()));
        if (config.getScalingMode() != null) {
            result.setScalingMode(toModel(config.getScalingMode()));
        }
        if (config.getScaleDown() != null) {
            ModelsScaleDownConfig scaleDown = new ModelsScaleDownConfig();
            scaleDown.setIdleTime(config.getScaleDown().getIdleTime());
            scaleDown.setIgnorePlayers(config.getScaleDown().isIgnorePlayers());
            result.setScaleDown(scaleDown);
        }
        return result;
    }

    public static @Nullable SourceConfig fromModel(@Nullable ModelsSourceConfig config) {
        if (config == null) {
            return null;
        }
        SourceConfig result = new SourceConfig();
        if (config.getType() != null) {
            result.setType(parseEnum(SourceType.class, config.getType().getValue()));
        }
        result.setBlueprint(config.getBlueprint());
        result.setImage(config.getImage());
        return result;
    }

    public static @Nullable WorkflowsConfig fromModel(@Nullable ModelsWorkflowsConfig config) {
        if (config == null) {
            return null;
        }
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

    public static @Nullable DeploymentConfig fromModel(@Nullable ModelsDeploymentConfig config) {
        if (config == null) {
            return null;
        }
        DeploymentConfig result = new DeploymentConfig();
        if (config.getStrategy() != null) {
            result.setStrategy(parseEnum(DeploymentStrategy.class, config.getStrategy()));
        }
        if (config.getHosts() != null) {
            result.setHosts(config.getHosts().stream()
                    .map(modelHost -> {
                        DeploymentHost host = new DeploymentHost();
                        host.setName(modelHost.getName());
                        host.setPriority(modelHost.getPriority() != null ? modelHost.getPriority() : 0);
                        return host;
                    })
                    .toArray(DeploymentHost[]::new));
        }
        return result;
    }

    public static @Nullable ScalingConfig fromModel(@Nullable ModelsScalingConfig config) {
        if (config == null) {
            return null;
        }
        ScalingConfig result = new ScalingConfig();
        result.setAvailableSlots(orZero(config.getAvailableSlots()));
        result.setMaxServers(orZero(config.getMaxServers()));
        result.setMinServers(orZero(config.getMinServers()));
        if (config.getPlayerThreshold() != null) {
            result.setPlayerThreshold(config.getPlayerThreshold().doubleValue());
        }
        if (config.getScalingMode() != null) {
            ScalingMode scalingMode = fromModel(config.getScalingMode());
            if (scalingMode != null) {
                result.setScalingMode(scalingMode);
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

    private static String toModel(DeploymentStrategy strategy) {
        return switch (strategy) {
            case WHITELIST -> "whitelist";
            case BLACKLIST -> "blacklist";
            case RANDOM, PRIORITY, ROUND_ROBIN -> throw new IllegalArgumentException(
                    "DeploymentStrategy." + strategy.name() + " is not supported by the controller REST API. Supported strategies are WHITELIST and BLACKLIST."
            );
        };
    }

    private static ModelsScalingMode toModel(ScalingMode scalingMode) {
        return switch (scalingMode) {
            case SLOTS -> ModelsScalingMode.fromValue("SLOTS");
            case PLAYERS -> ModelsScalingMode.fromValue("SERVERS");
            case MANUAL -> throw new IllegalArgumentException(
                    "ScalingMode.MANUAL is not supported by the controller REST API. Supported modes are SLOTS and PLAYERS."
            );
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

    public static WorkflowExecutionResult toWorkflowExecutionResult(String requestedWorkflowId,
                                                                     @Nullable String workflowId,
                                                                     @Nullable Integer totalServers,
                                                                     @Nullable List<String> succeededServerIds,
                                                                     @Nullable List<String> skippedServerIds,
                                                                     @Nullable List<ModelsExecuteManualWorkflowFailure> failedServers) {
        List<WorkflowFailure> failures = Objects.requireNonNullElse(failedServers, List.<ModelsExecuteManualWorkflowFailure>of())
                .stream()
                .map(failure -> new WorkflowFailure(Objects.requireNonNullElse(failure.getServerId(), ""), failure.getErrorMessage()))
                .toList();
        return new WorkflowExecutionResult(
                Objects.requireNonNullElse(workflowId, requestedWorkflowId),
                totalServers != null ? totalServers : 0,
                Objects.requireNonNullElse(succeededServerIds, List.of()),
                Objects.requireNonNullElse(skippedServerIds, List.of()),
                failures
        );
    }

    private static int orZero(@Nullable Integer value) {
        return value != null ? value : 0;
    }
}
