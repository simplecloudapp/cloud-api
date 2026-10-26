package app.simplecloud.api.internal.integration.player;

import java.util.Optional;
import java.util.UUID;

/**
 * The players connected to this proxy.
 */
public interface ProxyPlayers {

    /**
     * Returns the player if they are connected to this proxy.
     */
    Optional<ProxyPlayer> findPlayer(UUID playerId);
}
