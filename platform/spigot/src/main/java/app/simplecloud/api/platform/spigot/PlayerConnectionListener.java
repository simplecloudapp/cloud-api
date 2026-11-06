package app.simplecloud.api.platform.spigot;

import app.simplecloud.api.platform.shared.PlayerSynchronizer;
import app.simplecloud.api.platform.shared.player.OnlinePlayerTracker;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerConnectionListener implements Listener {

    private final OnlinePlayerTracker onlinePlayerTracker;
    private final PlayerSynchronizer playerSynchronizer;

    public PlayerConnectionListener(OnlinePlayerTracker onlinePlayerTracker, PlayerSynchronizer playerSynchronizer) {
        this.onlinePlayerTracker = onlinePlayerTracker;
        this.playerSynchronizer = playerSynchronizer;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        onlinePlayerTracker.add(event.getPlayer().getUniqueId());
        playerSynchronizer.updatePlayerCount();
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        onlinePlayerTracker.remove(event.getPlayer().getUniqueId());
        playerSynchronizer.updatePlayerCount();
    }
}
