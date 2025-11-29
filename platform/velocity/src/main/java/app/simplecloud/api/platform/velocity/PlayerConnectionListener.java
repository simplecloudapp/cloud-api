package app.simplecloud.api.platform.velocity;

import app.simplecloud.api.platform.shared.PlayerSynchronizer;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;

import java.util.concurrent.CompletableFuture;

public class PlayerConnectionListener {

    private final PlayerSynchronizer playerSynchronizer;

    public PlayerConnectionListener(PlayerSynchronizer playerSynchronizer) {
        this.playerSynchronizer = playerSynchronizer;
    }

    @Subscribe
    public void onPlayerJoin(PostLoginEvent event) {
        CompletableFuture.runAsync(() -> playerSynchronizer.updatePlayerCount());
    }

    @Subscribe
    public void onPlayerQuit(DisconnectEvent event) {
        CompletableFuture.runAsync(() -> playerSynchronizer.updatePlayerCount());
    }
}

