package app.simplecloud.api.group;

import app.simplecloud.api.base.ServerBase;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a server group configuration.
 *
 * <p>Server groups are templates that define how server instances should be
 * created, deployed, and scaled. Each group specifies memory limits, player capacity,
 * deployment strategy, scaling behavior, and source configuration.
 *
 * <p>This interface extends {@link ServerBase} to provide common configuration access
 * alongside group-specific properties like deployment and scaling configuration.
 */
public interface Group extends ServerBase {

    /**
     * Returns the unique identifier of this server group.
     *
     * @return the group ID
     */
    String getServerGroupId();

    /**
     * Returns the deployment configuration for this group.
     *
     * <p>Defines which hosts can run servers and the deployment strategy.
     *
     * @return the deployment config, or null if not set
     */
    @Nullable DeploymentConfig getDeployment();

    /**
     * Returns the scaling configuration for this group.
     *
     * <p>Defines auto-scaling behavior including min/max servers and thresholds.
     *
     * @return the scaling config, or null if not set
     */
    @Nullable ScalingConfig getScaling();
}
