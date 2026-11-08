package app.simplecloud.api.provider.paper;

import app.simplecloud.api.internal.integration.adventure.PlayerAudiences;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

final class PaperPlayerAudiences implements PlayerAudiences {

    private final Plugin plugin;

    PaperPlayerAudiences(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void runForPlayer(UUID playerId, PlayerAction action) {
        Bukkit.getGlobalRegionScheduler().execute(plugin, () -> {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                run(player, action);
            }
        });
    }

    private void run(Player player, PlayerAction action) {
        player.getScheduler().run(plugin, task -> action.run(player.getUniqueId(), player), null);
    }
}
