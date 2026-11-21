package app.simplecloud.api.internal.update;

import app.simplecloud.api.group.DeploymentConfig;
import app.simplecloud.api.group.DeploymentStrategy;
import app.simplecloud.api.group.ScaleDownConfig;
import app.simplecloud.api.group.ScalingConfig;
import app.simplecloud.api.group.WorkflowWhen;
import app.simplecloud.api.group.WorkflowsConfig;
import org.jetbrains.annotations.Nullable;

public final class ConfigMerger {

    private ConfigMerger() {
    }

    public static @Nullable ScalingConfig merge(@Nullable ScalingConfig current, @Nullable ScalingConfig update) {
        if (update == null) {
            return null;
        }
        ScalingConfig base = current != null ? current : new ScalingConfig();
        ScalingConfig result = new ScalingConfig();
        if (update.hasAvailableSlots() || base.hasAvailableSlots()) {
            result.setAvailableSlots(update.hasAvailableSlots() ? update.getAvailableSlots() : base.getAvailableSlots());
        }
        if (update.hasMaxServers() || base.hasMaxServers()) {
            result.setMaxServers(update.hasMaxServers() ? update.getMaxServers() : base.getMaxServers());
        }
        if (update.hasMinServers() || base.hasMinServers()) {
            result.setMinServers(update.hasMinServers() ? update.getMinServers() : base.getMinServers());
        }
        if (update.hasPlayerThreshold() || base.hasPlayerThreshold()) {
            result.setPlayerThreshold(update.hasPlayerThreshold() ? update.getPlayerThreshold() : base.getPlayerThreshold());
        }
        result.setScalingMode(update.getScalingMode() != null ? update.getScalingMode() : base.getScalingMode());
        result.setScaleDown(merge(base.getScaleDown(), update.getScaleDown()));
        return result;
    }

    private static @Nullable ScaleDownConfig merge(@Nullable ScaleDownConfig current, @Nullable ScaleDownConfig update) {
        if (update == null) {
            return current;
        }
        if (current == null) {
            return update;
        }
        ScaleDownConfig result = new ScaleDownConfig();
        result.setIdleTime(update.getIdleTime() != null ? update.getIdleTime() : current.getIdleTime());
        if (update.hasIgnorePlayers()) {
            result.setIgnorePlayers(update.isIgnorePlayers());
        } else if (current.hasIgnorePlayers()) {
            result.setIgnorePlayers(current.isIgnorePlayers());
        }
        return result;
    }

    public static @Nullable WorkflowsConfig merge(@Nullable WorkflowsConfig current, @Nullable WorkflowsConfig update) {
        if (update == null) {
            return null;
        }
        if (current == null) {
            return update;
        }
        WorkflowWhen currentWhen = current.getWhen();
        WorkflowWhen updateWhen = update.getWhen();
        WorkflowWhen when = null;
        if (currentWhen != null || updateWhen != null) {
            when = new WorkflowWhen();
            when.setStart(updateWhen != null && updateWhen.getStart() != null
                    ? updateWhen.getStart()
                    : currentWhen != null ? currentWhen.getStart() : null);
            when.setStop(updateWhen != null && updateWhen.getStop() != null
                    ? updateWhen.getStop()
                    : currentWhen != null ? currentWhen.getStop() : null);
        }

        WorkflowsConfig result = new WorkflowsConfig();
        result.setManual(update.getManual() != null ? update.getManual() : current.getManual());
        result.setWhen(when);
        return result;
    }

    public static @Nullable DeploymentConfig merge(@Nullable DeploymentConfig current, @Nullable DeploymentConfig update) {
        if (update == null) {
            return null;
        }
        DeploymentConfig result = new DeploymentConfig();
        DeploymentStrategy strategy = update.getStrategy();
        if (strategy == null && current != null) {
            strategy = current.getStrategy();
        }
        result.setStrategy(strategy != null ? strategy : DeploymentStrategy.BLACKLIST);
        if (update.hasHosts()) {
            result.setHosts(update.getHosts());
        } else if (current != null) {
            result.setHosts(current.getHosts());
        }
        return result;
    }
}
