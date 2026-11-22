package app.simplecloud.api.internal.util;

import app.simplecloud.api.group.DeploymentConfig;
import app.simplecloud.api.group.DeploymentHost;
import app.simplecloud.api.group.DeploymentStrategy;
import app.simplecloud.api.group.ScaleDownConfig;
import app.simplecloud.api.group.ScalingConfig;
import app.simplecloud.api.group.WorkflowWhen;
import app.simplecloud.api.group.WorkflowsConfig;
import java.util.List;
import org.jetbrains.annotations.Nullable;

public final class ConfigMergeUtil {

    private ConfigMergeUtil() {}

    public static @Nullable ScalingConfig merge(@Nullable ScalingConfig current, @Nullable ScalingConfig update) {
        if (update == null) {
            return null;
        }
        if (current == null) {
            return update;
        }
        return new ScalingConfig(
                update.availableSlots() != null ? update.availableSlots() : current.availableSlots(),
                update.maxServers() != null ? update.maxServers() : current.maxServers(),
                update.minServers() != null ? update.minServers() : current.minServers(),
                update.playerThreshold() != null ? update.playerThreshold() : current.playerThreshold(),
                merge(current.scaleDown(), update.scaleDown()),
                update.scalingMode() != null ? update.scalingMode() : current.scalingMode());
    }

    private static @Nullable ScaleDownConfig merge(
            @Nullable ScaleDownConfig current, @Nullable ScaleDownConfig update) {
        if (update == null) {
            return current;
        }
        if (current == null) {
            return update;
        }
        return new ScaleDownConfig(
                update.idleTime() != null ? update.idleTime() : current.idleTime(),
                update.ignorePlayers() != null ? update.ignorePlayers() : current.ignorePlayers());
    }

    public static @Nullable WorkflowsConfig merge(@Nullable WorkflowsConfig current, @Nullable WorkflowsConfig update) {
        if (update == null) {
            return null;
        }
        if (current == null) {
            return update;
        }
        List<String> manual = update.manual() != null ? update.manual() : current.manual();
        return new WorkflowsConfig(manual, merge(current.when(), update.when()));
    }

    private static @Nullable WorkflowWhen merge(@Nullable WorkflowWhen current, @Nullable WorkflowWhen update) {
        if (current == null) {
            return update;
        }
        if (update == null) {
            return current;
        }
        return new WorkflowWhen(
                update.start() != null ? update.start() : current.start(),
                update.stop() != null ? update.stop() : current.stop());
    }

    public static @Nullable DeploymentConfig merge(
            @Nullable DeploymentConfig current, @Nullable DeploymentConfig update) {
        if (update == null) {
            return null;
        }
        DeploymentStrategy strategy = update.strategy();
        List<DeploymentHost> hosts = update.hosts();
        if (current != null) {
            strategy = strategy != null ? strategy : current.strategy();
            hosts = hosts != null ? hosts : current.hosts();
        }
        return new DeploymentConfig(hosts, strategy != null ? strategy : DeploymentStrategy.BLACKLIST);
    }
}
