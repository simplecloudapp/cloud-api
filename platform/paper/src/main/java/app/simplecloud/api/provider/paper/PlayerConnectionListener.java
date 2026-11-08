package app.simplecloud.api.provider.paper;

import app.simplecloud.api.internal.integration.adventure.AdventureIntegration;
import app.simplecloud.api.platform.shared.PlayerSynchronizer;
import app.simplecloud.api.platform.shared.player.OnlinePlayerTracker;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerConnectionListener implements Listener {

    private final OnlinePlayerTracker onlinePlayerTracker;
    private final PlayerSynchronizer playerSynchronizer;
    private final AdventureIntegration adventureIntegration;

    public PlayerConnectionListener(
            OnlinePlayerTracker onlinePlayerTracker,
            PlayerSynchronizer playerSynchronizer,
            AdventureIntegration adventureIntegration) {
        this.onlinePlayerTracker = onlinePlayerTracker;
        this.playerSynchronizer = playerSynchronizer;
        this.adventureIntegration = adventureIntegration;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        onlinePlayerTracker.add(event.getPlayer().getUniqueId());
        playerSynchronizer.updatePlayerCount();
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        onlinePlayerTracker.remove(event.getPlayer().getUniqueId());
        adventureIntegration.forgetPlayer(event.getPlayer().getUniqueId());
        playerSynchronizer.updatePlayerCount();
    }
}
