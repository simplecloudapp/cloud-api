package app.simplecloud.api.platform.bungeecord;

import app.simplecloud.api.platform.shared.PlayerSynchronizer;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;

import java.util.concurrent.CompletableFuture;

public class PlayerConnectionListener implements Listener {

    private final PlayerSynchronizer playerSynchronizer;

    public PlayerConnectionListener(PlayerSynchronizer playerSynchronizer) {
        this.playerSynchronizer = playerSynchronizer;
    }

    @EventHandler
    public void onPlayerJoin(PostLoginEvent event) {
        CompletableFuture.runAsync(() -> playerSynchronizer.updatePlayerCount());
    }

    @EventHandler
    public void onPlayerQuit(PlayerDisconnectEvent event) {
        CompletableFuture.runAsync(() -> playerSynchronizer.updatePlayerCount());
    }
}


