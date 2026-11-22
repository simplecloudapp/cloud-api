package app.simplecloud.api.platform.minestom;

import app.simplecloud.api.internal.integration.adventure.PlayerAudiences;
import java.util.UUID;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;

final class MinestomPlayerAudiences implements PlayerAudiences {

    @Override
    public void runForPlayer(UUID playerId, PlayerAction action) {
        Player player = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(playerId);
        if (player != null) {
            run(player, action);
        }
    }

    @Override
    public void runForAllPlayers(PlayerAction action) {
        for (Player player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            run(player, action);
        }
    }

    private void run(Player player, PlayerAction action) {
        player.scheduleNextTick(_ -> action.run(player.getUuid(), player));
    }
}
