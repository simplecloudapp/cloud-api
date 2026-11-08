package app.simplecloud.api.server;

import java.util.Locale;

/**
 * Represents the lifecycle state of a server instance.
 */
public enum ServerState {
    /**
     * Unknown or unspecified server state.
     */
    UNKNOWN_STATE,

    /**
     * Server is queued and waiting to be started.
     */
    QUEUED,

    /**
     * Server is being prepared (downloading files, setting up).
     */
    PREPARING,

    /**
     * Server is in the process of starting up.
     */
    STARTING,

    /**
     * Server is available and accepting connections.
     */
    AVAILABLE,

    /**
     * Server is in-game with active players.
     */
    INGAME,

    /**
     * Server is in the process of shutting down.
     */
    STOPPING,

    /**
     * Server was shut down and is now being cleaned up.
     */
    CLEANUP,

    /**
     * Server has been stopped.
     */
    STOPPED;

    /**
     * Parses a state with or without the {@code SERVER_STATE_} prefix, ignoring case.
     *
     * @param state the value to parse
     * @return the state
     * @throws IllegalArgumentException if the value is not a known state
     */
    public static ServerState parse(String state) {
        return ServerState.valueOf(state.toUpperCase(Locale.ROOT).replace("SERVER_STATE_", ""));
    }
}
