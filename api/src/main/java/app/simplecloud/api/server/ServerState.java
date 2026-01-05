package app.simplecloud.api.server;

import app.simplecloud.api.web.models.ModelsPatchServerRequest;
import app.simplecloud.api.web.models.ModelsServerSummary;

/**
 * Represents the lifecycle state of a server instance.
 */
public enum ServerState {
    /**
     * Unknown or unspecified server state.
     */
    UNKNOWN_STATE,

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
    STOPPING;

    public static ServerState parse(String state) {
        return ServerState.valueOf(state.toUpperCase().replace("SERVER_STATE_", ""));
    }

    public static ServerState parse(ModelsServerSummary.StateEnum state) {
        return parse(state.toString());
    }

    public static ServerState parse(ModelsPatchServerRequest.StateEnum state) {
        return parse(state.toString());
    }

}

