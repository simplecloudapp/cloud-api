package app.simplecloud.api.platform.spigot.legacy;

import app.simplecloud.api.platform.shared.PlayerSynchronizer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.concurrent.CompletableFuture;

public class PlayerConnectionListener implements Listener {

    private final PlayerSynchronizer playerSynchronizer;

    public PlayerConnectionListener(PlayerSynchronizer playerSynchronizer) {
        this.playerSynchronizer = playerSynchronizer;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        CompletableFuture.runAsync(() -> playerSynchronizer.updatePlayerCount());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        CompletableFuture.runAsync(() -> playerSynchronizer.updatePlayerCount());
    }
}

