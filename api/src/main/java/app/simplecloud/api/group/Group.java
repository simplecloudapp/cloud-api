package app.simplecloud.api.group;

import app.simplecloud.api.base.ServerBase;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a server group.
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
    @Nullable
    DeploymentConfig getDeployment();

    /**
     * Returns whether this group is active.
     *
     * @return active flag, or null if not available
     */
    @Nullable
    Boolean isActive();

    /**
     * Returns the priority for this group.
     *
     * @return priority, or null if not available
     */
    @Nullable
    Integer getPriority();

    /**
     * Returns the scaling configuration for this group.
     *
     * <p>Defines auto-scaling behavior including min/max servers and thresholds.
     *
     * @return the scaling config, or null if not set
     */
    @Nullable
    ScalingConfig getScaling();

    /**
     * Returns the server and player counts of this group.
     *
     * <p>Groups returned by {@link GroupApi#createGroup} and {@link GroupApi#updateGroup} and groups
     * attached to a {@link app.simplecloud.api.server.Server} carry no stats.
     *
     * @return the stats, or null if not available
     */
    @Nullable
    GroupStats getStats();
}
