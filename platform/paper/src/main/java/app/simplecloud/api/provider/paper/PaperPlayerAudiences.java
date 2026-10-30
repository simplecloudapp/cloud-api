package app.simplecloud.api.provider.paper;

import app.simplecloud.api.internal.integration.adventure.PlayerAudiences;
import net.kyori.adventure.audience.Audience;
import org.bukkit.Bukkit;

import java.util.Optional;
import java.util.UUID;

final class PaperPlayerAudiences implements PlayerAudiences {

    @Override
    public Optional<Audience> findPlayer(UUID playerId) {
        Audience player = Bukkit.getPlayer(playerId);
        return Optional.ofNullable(player);
    }

    @Override
    public Audience allPlayers() {
        return Audience.audience(Bukkit.getOnlinePlayers());
    }
}
