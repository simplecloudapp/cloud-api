package app.simplecloud.api.event.server;

import app.simplecloud.api.server.Server;

/**
 * Event fired when a server instance is updated.
 */
public interface ServerUpdatedEvent {

    /**
     * Returns the network ID this event belongs to.
     *
     * @return the network ID
     */
    String getNetworkId();

    /**
     * Returns the ID of the updated server.
     *
     * @return the server ID
     */
    String getServerId();

    /**
     * Returns the server group ID this server belongs to.
     *
     * @return the server group ID
     */
    String getServerGroupId();

    /**
     * Returns the updated server instance with complete configuration and runtime information.
     *
     * @return the server
     * @throws IllegalStateException if server data is not available
     */
    Server getServer();

    /**
     * Returns the timestamp when this event occurred (ISO 8601 format).
     *
     * @return the timestamp
     */
    String getTimestamp();
}



