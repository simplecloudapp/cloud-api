package app.simplecloud.api.server;

import app.simplecloud.api.blueprint.Blueprint;
import app.simplecloud.api.group.Group;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Represents a running server instance.
 *
 * <p>Provides detailed information about a server's state, resource usage,
 * network configuration, and associated group and blueprint.
 */
public interface Server {

    /**
     * Returns the unique identifier of this server.
     *
     * @return the server ID
     */
    String getServerId();

    /**
     * Returns the persistent server ID this instance is associated with.
     *
     * @return the persistent server ID
     */
    String getPersistentServerId();

    /**
     * Returns the numerical ID of this server (typically a sequential number).
     *
     * @return the numerical ID
     */
    int getNumericalId();

    /**
     * Returns the ID of the server group this instance belongs to.
     *
     * @return the server group ID
     */
    String getServerGroupId();

    /**
     * Returns the ID of the host machine running this server.
     *
     * @return the serverhost ID
     */
    String getServerhostId();

    /**
     * Returns the network ID this server belongs to.
     *
     * @return the network ID
     */
    String getNetworkId();

    /**
     * Returns the IP address where this server is accessible.
     *
     * @return the IP address, or null if not yet assigned
     */
    @Nullable String getIp();

    /**
     * Returns the port where this server is listening.
     *
     * @return the port number, or null if not yet assigned
     */
    @Nullable Integer getPort();

    /**
     * Returns the minimum memory allocation for this server (in MB).
     *
     * @return the minimum memory
     */
    Integer getMinMemory();

    /**
     * Returns the maximum memory allocation for this server (in MB).
     *
     * @return the maximum memory
     */
    Integer getMaxMemory();

    /**
     * Returns the current CPU usage as a percentage (0.0 to 100.0).
     *
     * @return the CPU usage percentage, or null if not available
     */
    @Nullable Double getCpuUsage();

    /**
     * Returns the current memory usage (in MB).
     *
     * @return the memory usage, or null if not available
     */
    @Nullable Double getMemoryUsage();

    /**
     * Returns the current number of connected players.
     *
     * @return the player count, or null if not available
     */
    @Nullable Integer getPlayerCount();

    /**
     * Returns the maximum number of players allowed on this server.
     *
     * @return the max players
     */
    Integer getMaxPlayers();

    /**
     * Returns the current lifecycle state of this server.
     *
     * @return the server state
     */
    ServerState getState();

    /**
     * Returns the timestamp when this server was created (ISO 8601 format).
     *
     * @return the creation timestamp
     */
    String getCreatedAt();

    /**
     * Returns the timestamp when this server was last updated (ISO 8601 format).
     *
     * @return the update timestamp
     */
    String getUpdatedAt();

    /**
     * Returns the timestamp of the last activity on this server (ISO 8601 format).
     *
     * @return the last activity timestamp, or null if not available
     */
    @Nullable String getLastActivity();

    /**
     * Returns custom properties attached to this server.
     *
     * @return the properties map, or null if none set
     */
    @Nullable Map<String, Object> getProperties();

    /**
     * Returns the blueprint this server was created from.
     *
     * @return the blueprint, or null if not available
     */
    @Nullable Blueprint getBlueprint();

    /**
     * Returns the server group this server belongs to.
     *
     * @return the server group
     */
    Group getServerGroup();
}

