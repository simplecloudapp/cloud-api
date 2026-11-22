package app.simplecloud.api.internal.integration.adventure;

import java.util.UUID;
import net.kyori.adventure.audience.Audience;

/**
 * Runs adventure actions for the players on this server.
 */
public interface PlayerAudiences {

    /**
     * Runs the action for the player if they are on this server, otherwise does nothing.
     */
    void runForPlayer(UUID playerId, PlayerAction action);

    /**
     * Runs the action once for every player on this server.
     */
    void runForAllPlayers(PlayerAction action);

    @FunctionalInterface
    interface PlayerAction {

        void run(UUID playerId, Audience player);
    }
}
