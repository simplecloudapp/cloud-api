package app.simplecloud.api.event.persistentserver;

import app.simplecloud.api.persistentserver.PersistentServer;
import org.jetbrains.annotations.Nullable;

/**
 * Event fired when a persistent server stops.
 */
public interface PersistentServerStoppedEvent {

    /**
     * Returns the network ID this event belongs to.
     *
     * @return the network ID
     */
    String getNetworkId();

    /**
     * Returns the ID of the stopped persistent server.
     *
     * @return the persistent server ID
     */
    String getPersistentServerId();

    /**
     * Returns the ID of the associated server instance.
     *
     * @return the server ID, or null if not available
     */
    @Nullable
    String getServerId();

    /**
     * Returns whether the server crashed (stopped unexpectedly).
     *
     * @return true if the server crashed, false if it stopped normally
     */
    boolean isCrashed();

    /**
     * Returns the exit code of the server process.
     *
     * @return the exit code, or null if not available (an exit code of 0 is reported as null)
     */
    @Nullable
    Integer getExitCode();

    /**
     * Returns the reason why the server stopped.
     *
     * @return the stop reason, or null if not available
     */
    @Nullable
    String getReason();

    /**
     * Returns the persistent server as sent with this event.
     *
     * @return the persistent server
     * @throws IllegalStateException if persistent server data is not available
     */
    PersistentServer getPersistentServer();

    /**
     * Returns the timestamp when this event occurred (ISO 8601 format).
     *
     * @return the timestamp
     */
    String getTimestamp();
}
