package app.simplecloud.api.platform.shared;

import java.util.UUID;

/**
 * Tells whether a player is currently on this server.
 */
public interface OnlinePlayers {

    boolean isOnline(UUID playerId);
}
