package app.simplecloud.api.event.server;

import app.simplecloud.api.server.Server;
import app.simplecloud.api.server.ServerState;

/**
 * Event fired when a server's state changes.
 */
public interface ServerStateChangedEvent {

    /**
     * Returns the network ID this event belongs to.
     *
     * @return the network ID
     */
    String getNetworkId();

    /**
     * Returns the ID of the server whose state changed.
     *
     * @return the server ID
     */
    String getServerId();

    /**
     * Returns the previous state of the server.
     *
     * @return the old state
     * @throws IllegalStateException if old state is not available
     */
    ServerState getOldState();

    /**
     * Returns the new state of the server.
     *
     * @return the new state
     * @throws IllegalStateException if new state is not available
     */
    ServerState getNewState();

    /**
     * Returns the server instance with complete configuration and runtime information.
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

