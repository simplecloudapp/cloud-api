package app.simplecloud.api.server;

/**
 * Represents the lifecycle state of a server instance.
 */
public enum ServerState {
    /** Unknown or unspecified server state. */
    UNKNOWN_STATE,
    
    /** Server is being prepared (downloading files, setting up). */
    PREPARING,
    
    /** Server is in the process of starting up. */
    STARTING,
    
    /** Server is available and accepting connections. */
    AVAILABLE,
    
    /** Server is in-game with active players. */
    INGAME,
    
    /** Server is in the process of shutting down. */
    STOPPING
}

