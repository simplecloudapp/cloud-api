package app.simplecloud.api.internal.integration.adventure;

import net.kyori.adventure.audience.Audience;

import java.util.Optional;
import java.util.UUID;

/**
 * The players on this server, as audiences for adventure actions.
 */
public interface PlayerAudiences {

    /**
     * Returns the player if they are on this server.
     */
    Optional<Audience> findPlayer(UUID playerId);

    /**
     * Returns all players on this server.
     */
    Audience allPlayers();
}
