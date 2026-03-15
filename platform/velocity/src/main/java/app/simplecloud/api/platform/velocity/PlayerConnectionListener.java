package app.simplecloud.api.platform.velocity;

import app.simplecloud.api.internal.integration.player.PlayerIntegration;
import app.simplecloud.api.internal.integration.presence.ProxyPresenceTracker;
import app.simplecloud.api.platform.shared.PlayerSynchronizer;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

public class PlayerConnectionListener {

    private static final Logger logger = LoggerFactory.getLogger(PlayerConnectionListener.class);

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

    @Subscribe
    public void onPlayerJoin(PostLoginEvent event) {
        Player player = event.getPlayer();
        String playerId = player.getUniqueId().toString();
        proxyPresenceTracker.trackLogin(playerId);

        String texture = null;
        var texturesProperty = player.getGameProfile().getProperties().stream()
                .filter(p -> p.getName().equals("textures"))
                .findFirst();
        if (texturesProperty.isPresent()) {
            texture = texturesProperty.get().getValue();
        }

        playerIntegration.login(
                playerId,
                player.getUsername(),
                player.getUsername(),
                proxyName != null && !proxyName.isBlank() ? proxyName : "unknown",
                String.valueOf(player.getRemoteAddress().hashCode()),
                player.getEffectiveLocale() != null ? player.getEffectiveLocale().toString() : "en_US",
                player.getProtocolVersion().getProtocol(),
                player.isOnlineMode(),
                texture
        ).thenAccept(result -> {
            if (result.isSuccess()) {
                proxyPresenceTracker.updateSessionId(playerId, result.getSessionId());
            }
            if (!result.isSuccess()) {
                logger.warn("Login failed for {}: {}", player.getUsername(), result.getErrorMessage());
            }
        }).exceptionally(e -> {
            logger.error("Failed to send login event for {}: {}", player.getUsername(), e.getMessage());
            return null;
        });

        CompletableFuture.runAsync(() -> playerSynchronizer.updatePlayerCount());
    }

    @Subscribe
    public void onPlayerQuit(DisconnectEvent event) {
        Player player = event.getPlayer();
        String playerId = player.getUniqueId().toString();

        playerIntegration.disconnect(playerId)
                .exceptionally(e -> {
                    logger.error("Failed to send disconnect event for {}: {}", player.getUsername(), e.getMessage());
                    return null;
                });
        proxyPresenceTracker.remove(playerId);

        CompletableFuture.runAsync(() -> playerSynchronizer.updatePlayerCount());
    }

    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        Player player = event.getPlayer();
        String newServer = event.getServer().getServerInfo().getName();

        playerIntegration.serverSwitch(player.getUniqueId().toString(), newServer)
                .exceptionally(e -> {
                    logger.error("Failed to send server switch event for {}: {}", player.getUsername(), e.getMessage());
                    return null;
                });
    }
}
