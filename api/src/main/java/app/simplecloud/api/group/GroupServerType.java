package app.simplecloud.api.group;

/**
 * Represents the type of servers in a server group.
 */
public enum GroupServerType {
    /** Unknown or unspecified server type. */
    UNKNOWN_SERVER,
    
    /** Standard game server (e.g., Minecraft server with worlds). */
    SERVER,
    
    /** Proxy server that connects to backend servers (e.g., Velocity, BungeeCord). */
    PROXY
}

