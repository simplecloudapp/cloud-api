package app.simplecloud.api.platform.minestom;

import app.simplecloud.api.internal.integration.adventure.PlayerAudiences;
import net.kyori.adventure.audience.Audience;
import net.minestom.server.MinecraftServer;

import java.util.Optional;
import java.util.UUID;

final class MinestomPlayerAudiences implements PlayerAudiences {

    @Override
    public Optional<Audience> findPlayer(UUID playerId) {
        return Optional.ofNullable(MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(playerId));
    }

    @Override
    public Audience allPlayers() {
        return Audience.audience(MinecraftServer.getConnectionManager().getOnlinePlayers());
    }
}
