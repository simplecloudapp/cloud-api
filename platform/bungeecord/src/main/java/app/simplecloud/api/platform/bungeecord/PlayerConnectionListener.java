package app.simplecloud.api.platform.bungeecord;

import app.simplecloud.api.internal.integration.player.PlayerIntegration;
import app.simplecloud.api.internal.integration.presence.ProxyPresenceTracker;
import app.simplecloud.api.platform.shared.PlayerSynchronizer;
import app.simplecloud.api.player.PlayerClientSettings;
import net.md_5.bungee.api.SkinConfiguration;
import net.md_5.bungee.api.connection.PendingConnection;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.event.ServerSwitchEvent;
import net.md_5.bungee.api.event.SettingsChangedEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.event.EventPriority;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PlayerConnectionListener implements Listener {

    private static final Logger logger = Logger.getLogger(PlayerConnectionListener.class.getName());

    private final Plugin plugin;
    private final PlayerSynchronizer playerSynchronizer;
    private final PlayerIntegration playerIntegration;
    private final ProxyPresenceTracker proxyPresenceTracker;
    private final String proxyName;
    private final Consumer<UUID> synchronizePlayerProperties;
    private final Consumer<UUID> forgetPlayerProperties;

    public PlayerConnectionListener(
            Plugin plugin,
            PlayerSynchronizer playerSynchronizer,
            PlayerIntegration playerIntegration,
            ProxyPresenceTracker proxyPresenceTracker,
            String proxyName,
            Consumer<UUID> synchronizePlayerProperties,
            Consumer<UUID> forgetPlayerProperties
    ) {
        this.plugin = plugin;
        this.playerSynchronizer = playerSynchronizer;
        this.playerIntegration = playerIntegration;
        this.proxyPresenceTracker = proxyPresenceTracker;
        this.proxyName = proxyName;
        this.synchronizePlayerProperties = synchronizePlayerProperties;
        this.forgetPlayerProperties = forgetPlayerProperties;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerJoin(PostLoginEvent event) {
        ProxiedPlayer player = event.getPlayer();
        PendingConnection connection = player.getPendingConnection();
        String playerId = player.getUniqueId().toString();
        proxyPresenceTracker.trackLogin(playerId);

        var registration = playerIntegration.login(
                playerId,
                player.getName(),
                player.getDisplayName(),
                proxyName != null && !proxyName.isBlank() ? proxyName : "unknown",
                String.valueOf(connection.getSocketAddress().hashCode()),
                player.getLocale() != null ? player.getLocale().toString() : "en_US",
                connection.getVersion(),
                connection.isOnlineMode(),
                null
        );
        // Keep the initial backend connection behind controller registration.
        event.registerIntent(plugin);
        registration.thenAccept(result -> {
            if (result.isSuccess()) {
                proxyPresenceTracker.updateSessionId(playerId, result.getSessionId());
                synchronizePlayerProperties.accept(player.getUniqueId());
                if (player.getLocale() != null) {
                    sendClientSettings(player, result.getSessionId());
                }
            }
            if (!result.isSuccess()) {
                logger.warning("Login failed for " + player.getName() + ": " + result.getErrorMessage());
            }
        }).exceptionally(e -> {
            logger.log(Level.SEVERE, "Failed to send login event for " + player.getName(), e);
            return null;
        }).whenComplete((ignored, error) -> event.completeIntent(plugin));

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
        forgetPlayerProperties.accept(player.getUniqueId());

        CompletableFuture.runAsync(() -> playerSynchronizer.updatePlayerCount());
    }

    @EventHandler
    public void onSettingsChanged(SettingsChangedEvent event) {
        ProxiedPlayer player = event.getPlayer();
        String sessionId = proxyPresenceTracker.getSessionId(player.getUniqueId().toString());
        // Settings sent before the login completed are picked up once the session exists.
        if (sessionId == null) {
            return;
        }

        sendClientSettings(player, sessionId);
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

    private void sendClientSettings(ProxiedPlayer player, String sessionId) {
        playerIntegration.updateClientSettings(sessionId, toClientSettings(player))
                .exceptionally(e -> {
                    logger.log(Level.SEVERE, "Failed to send client settings for " + player.getName(), e);
                    return null;
                });
    }

    static PlayerClientSettings toClientSettings(ProxiedPlayer player) {
        PlayerClientSettings result = new PlayerClientSettings();
        if (player.getChatMode() != null) {
            result.setChatMode(switch (player.getChatMode()) {
                case SHOWN -> PlayerClientSettings.ChatMode.ENABLED;
                case COMMANDS_ONLY -> PlayerClientSettings.ChatMode.COMMANDS_ONLY;
                case HIDDEN -> PlayerClientSettings.ChatMode.HIDDEN;
            });
        }
        result.setLocale(player.getLocale() != null ? player.getLocale().toString() : null);
        if (player.getMainHand() != null) {
            result.setMainHand(player.getMainHand() == ProxiedPlayer.MainHand.LEFT
                    ? PlayerClientSettings.MainHand.LEFT
                    : PlayerClientSettings.MainHand.RIGHT);
        }
        result.setViewDistance(player.getViewDistance());
        result.setChatColors(player.hasChatColors());
        // Bungee does not expose the client listing flag.
        result.setClientListingAllowed(true);
        result.setSkinParts(toSkinParts(player.getSkinParts()));
        return result;
    }

    private static List<String> toSkinParts(SkinConfiguration skinParts) {
        List<String> result = new ArrayList<>();
        if (skinParts == null) {
            return result;
        }
        if (skinParts.hasCape()) result.add("CAPE");
        if (skinParts.hasJacket()) result.add("JACKET");
        if (skinParts.hasLeftSleeve()) result.add("LEFT_SLEEVE");
        if (skinParts.hasRightSleeve()) result.add("RIGHT_SLEEVE");
        if (skinParts.hasLeftPants()) result.add("LEFT_PANTS");
        if (skinParts.hasRightPants()) result.add("RIGHT_PANTS");
        if (skinParts.hasHat()) result.add("HAT");
        return result;
    }
}
