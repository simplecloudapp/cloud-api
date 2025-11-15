package app.simplecloud.api.event.server;

import app.simplecloud.api.server.Server;
import org.jetbrains.annotations.Nullable;

/**
 * Event fired when a server instance stops.
 */
public interface ServerStoppedEvent {
    
    /**
     * Returns the network ID this event belongs to.
     *
     * @return the network ID
     */
    String getNetworkId();
    
    /**
     * Returns the ID of the stopped server.
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
     * Returns the stopped server instance with complete configuration and runtime information.
     *
     * @return the server
     * @throws IllegalStateException if server data is not available
     */
    Server getServer();
    
    /**
     * Returns whether the server crashed (stopped unexpectedly).
     *
     * @return true if the server crashed, false if it stopped normally
     */
    boolean getCrashed();
    
    /**
     * Returns the exit code of the server process.
     *
     * @return the exit code, or null if not available
     */
    @Nullable Integer getExitCode();
    
    /**
     * Returns the reason why the server stopped.
     *
     * @return the stop reason, or null if not available
     */
    @Nullable String getReason();
    
    /**
     * Returns the timestamp when this event occurred (ISO 8601 format).
     *
     * @return the timestamp
     */
    String getTimestamp();
}

