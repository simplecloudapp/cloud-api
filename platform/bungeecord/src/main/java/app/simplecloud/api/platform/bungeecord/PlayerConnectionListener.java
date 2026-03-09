package app.simplecloud.api.platform.bungeecord;

import app.simplecloud.api.internal.integration.player.PlayerIntegration;
import app.simplecloud.api.internal.integration.presence.ProxyPresenceTracker;
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
    private final ProxyPresenceTracker proxyPresenceTracker;
    private final String proxyName;

    public PlayerConnectionListener(
            PlayerSynchronizer playerSynchronizer,
            PlayerIntegration playerIntegration,
            ProxyPresenceTracker proxyPresenceTracker,
            String proxyName
    ) {
        this.playerSynchronizer = playerSynchronizer;
        this.playerIntegration = playerIntegration;
        this.proxyPresenceTracker = proxyPresenceTracker;
        this.proxyName = proxyName;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerJoin(PostLoginEvent event) {
        ProxiedPlayer player = event.getPlayer();
        PendingConnection connection = player.getPendingConnection();
        String playerId = player.getUniqueId().toString();
        proxyPresenceTracker.trackLogin(playerId);

        playerIntegration.login(
                playerId,
                player.getName(),
                player.getDisplayName(),
                proxyName != null && !proxyName.isBlank() ? proxyName : "unknown",
                String.valueOf(connection.getSocketAddress().hashCode()),
                player.getLocale() != null ? player.getLocale().toString() : "en_US",
                connection.getVersion(),
                connection.isOnlineMode(),
                null
        ).thenAccept(result -> {
            if (result.isSuccess()) {
                proxyPresenceTracker.updateSessionId(playerId, result.getSessionId());
            }
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
        String playerId = player.getUniqueId().toString();

        playerIntegration.disconnect(playerId)
                .exceptionally(e -> {
                    logger.log(Level.SEVERE, "Failed to send disconnect event for " + player.getName(), e);
                    return null;
                });
        proxyPresenceTracker.remove(playerId);

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
