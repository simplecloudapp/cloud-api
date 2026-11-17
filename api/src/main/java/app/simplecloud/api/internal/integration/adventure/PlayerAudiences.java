package app.simplecloud.api.internal.integration.adventure;

import net.kyori.adventure.audience.Audience;

import java.util.UUID;

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
