package app.simplecloud.api.group;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Represents a server group configuration.
 *
 * <p>Server groups are templates that define how server instances should be
 * created, deployed, and scaled. Each group specifies memory limits, player capacity,
 * deployment strategy, scaling behavior, and source configuration.
 */
public interface Group {

    /**
     * Returns the unique identifier of this server group.
     *
     * @return the group ID
     */
    String getServerGroupId();

    /**
     * Returns the human-readable name of this server group.
     *
     * @return the group name
     */
    String getName();

    /**
     * Returns the minimum memory allocation for servers in this group (in MB).
     *
     * @return the minimum memory, or null if not set
     */
    @Nullable Integer getMinMemory();

    /**
     * Returns the maximum memory allocation for servers in this group (in MB).
     *
     * @return the maximum memory, or null if not set
     */
    @Nullable Integer getMaxMemory();

    /**
     * Returns the maximum number of players per server in this group.
     *
     * @return the max players, or null if not set
     */
    @Nullable Integer getMaxPlayers();

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

    /**
     * Returns the source configuration for this group.
     *
     * <p>Defines whether servers use a blueprint or container image.
     *
     * @return the source config, or null if not set
     */
    @Nullable SourceConfig getSource();

    /**
     * Returns the workflows configuration for this group.
     *
     * <p>Defines automated workflows to run on server lifecycle events.
     *
     * @return the workflows config, or null if not set
     */
    @Nullable WorkflowsConfig getWorkflows();

    /**
     * Returns custom properties attached to this group.
     *
     * @return the properties map, or null if none set
     */
    @Nullable Map<String, Object> getProperties();

    /**
     * Returns tags assigned to this group for organization and filtering.
     *
     * @return the list of tags, or null if none set
     */
    @Nullable List<String> getTags();

    /**
     * Returns the type of servers in this group (SERVER or PROXY).
     *
     * @return the group type
     */
    GroupServerType getType();

    /**
     * Returns the timestamp when this group was created (ISO 8601 format).
     *
     * @return the creation timestamp
     */
    String getCreatedAt();

    /**
     * Returns the timestamp when this group was last updated (ISO 8601 format).
     *
     * @return the update timestamp
     */
    String getUpdatedAt();
}

