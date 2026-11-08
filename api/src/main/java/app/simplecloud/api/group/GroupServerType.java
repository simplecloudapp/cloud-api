package app.simplecloud.api.group;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Represents the type of servers in a server group.
 */
public enum GroupServerType {
    /**
     * Unknown or unspecified server type.
     */
    UNKNOWN_SERVER,

    /**
     * Standard game server (e.g., Minecraft server with worlds).
     */
    SERVER,

    /**
     * Proxy server that connects to backend servers (e.g., Velocity, BungeeCord).
     */
    PROXY;

    /**
     * Parses a type name from the controller, ignoring case.
     *
     * @param name the type name, may be null
     * @return the matching type, or {@link #UNKNOWN_SERVER} if the name is null or unknown
     */
    public static GroupServerType parse(@Nullable String name) {
        if (name == null) {
            return UNKNOWN_SERVER;
        }
        try {
            return valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return UNKNOWN_SERVER;
        }
    }
}
