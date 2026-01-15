package app.simplecloud.api.platform.bungeecord;

import app.simplecloud.api.internal.integration.player.PlayerIntegration;
import app.simplecloud.api.platform.shared.PlayerSynchronizer;
import net.md_5.bungee.api.connection.PendingConnection;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.event.ServerSwitchEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.event.EventPriority;

import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PlayerConnectionListener implements Listener {

    private static final Logger logger = Logger.getLogger(PlayerConnectionListener.class.getName());

    private final PlayerSynchronizer playerSynchronizer;
    private final PlayerIntegration playerIntegration;
    private final String proxyId;

    public PlayerConnectionListener(PlayerSynchronizer playerSynchronizer, PlayerIntegration playerIntegration) {
        this.playerSynchronizer = playerSynchronizer;
        this.playerIntegration = playerIntegration;
        this.proxyId = System.getenv("SIMPLECLOUD_UNIQUE_ID");
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerJoin(PostLoginEvent event) {
        ProxiedPlayer player = event.getPlayer();
        PendingConnection connection = player.getPendingConnection();

        playerIntegration.login(
                player.getUniqueId().toString(),
                player.getName(),
                player.getDisplayName(),
                proxyId != null ? proxyId : "unknown",
                String.valueOf(connection.getSocketAddress().hashCode()),
                connection.getListener().getDefaultServer(),
                connection.getVersion(),
                connection.isOnlineMode(),
                null
        ).thenAccept(result -> {
            if (!result.isSuccess()) {
                logger.warning("Login failed for " + player.getName() + ": " + result.getErrorMessage());
            }
        }).exceptionally(e -> {
            logger.log(Level.SEVERE, "Failed to send login event for " + player.getName(), e);
            return null;
        });

        CompletableFuture.runAsync(() -> playerSynchronizer.updatePlayerCount());
    }

    @EventHandler
    public void onPlayerQuit(PlayerDisconnectEvent event) {
        ProxiedPlayer player = event.getPlayer();

        playerIntegration.disconnect(player.getUniqueId().toString())
                .exceptionally(e -> {
                    logger.log(Level.SEVERE, "Failed to send disconnect event for " + player.getName(), e);
                    return null;
                });

        CompletableFuture.runAsync(() -> playerSynchronizer.updatePlayerCount());
    }

    @EventHandler
    public void onServerSwitch(ServerSwitchEvent event) {
        ProxiedPlayer player = event.getPlayer();
        String newServer = player.getServer() != null ? player.getServer().getInfo().getName() : null;

        if (newServer != null) {
            playerIntegration.serverSwitch(player.getUniqueId().toString(), newServer)
                    .exceptionally(e -> {
                        logger.log(Level.SEVERE, "Failed to send server switch event for " + player.getName(), e);
                        return null;
                    });
        }
    }
}


