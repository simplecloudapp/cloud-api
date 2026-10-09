package app.simplecloud.api.platform.velocity;

import app.simplecloud.api.internal.integration.player.PlayerIntegration;
import app.simplecloud.api.internal.integration.presence.ProxyPresenceTracker;
import app.simplecloud.api.platform.shared.PlayerSynchronizer;
import app.simplecloud.api.player.PlayerClientSettings;
import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.player.PlayerSettingsChangedEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.player.PlayerSettings;
import com.velocitypowered.api.proxy.player.SkinParts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class PlayerConnectionListener {

    private static final Logger logger = LoggerFactory.getLogger(PlayerConnectionListener.class);

    private final PlayerSynchronizer playerSynchronizer;
    private final PlayerIntegration playerIntegration;
    private final ProxyPresenceTracker proxyPresenceTracker;
    private final String proxyName;
    private final Consumer<UUID> synchronizePlayerProperties;
    private final Consumer<UUID> forgetPlayerProperties;

    public PlayerConnectionListener(
            PlayerSynchronizer playerSynchronizer,
            PlayerIntegration playerIntegration,
            ProxyPresenceTracker proxyPresenceTracker,
            String proxyName,
            Consumer<UUID> synchronizePlayerProperties,
            Consumer<UUID> forgetPlayerProperties
    ) {
        this.playerSynchronizer = playerSynchronizer;
        this.playerIntegration = playerIntegration;
        this.proxyPresenceTracker = proxyPresenceTracker;
        this.proxyName = proxyName;
        this.synchronizePlayerProperties = synchronizePlayerProperties;
        this.forgetPlayerProperties = forgetPlayerProperties;
    }

    @Subscribe(priority = 100)
    public EventTask onPlayerJoin(PostLoginEvent event) {
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

        CompletableFuture<Void> registration = playerIntegration.login(
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
                synchronizePlayerProperties.accept(player.getUniqueId());
                if (player.hasSentPlayerSettings()) {
                    sendClientSettings(player, result.getSessionId(), player.getPlayerSettings());
                }
            }
            if (!result.isSuccess()) {
                logger.warn("Login failed for {}: {}", player.getUsername(), result.getErrorMessage());
            }
        }).exceptionally(e -> {
            logger.error("Failed to send login event for {}: {}", player.getUsername(), e.getMessage());
            return null;
        });

        CompletableFuture.runAsync(() -> playerSynchronizer.updatePlayerCount());
        // Keep later login listeners and the initial backend connection behind registration.
        return EventTask.resumeWhenComplete(registration);
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
        forgetPlayerProperties.accept(player.getUniqueId());

        CompletableFuture.runAsync(() -> playerSynchronizer.updatePlayerCount());
    }

    @Subscribe
    public void onPlayerSettingsChanged(PlayerSettingsChangedEvent event) {
        Player player = event.getPlayer();
        String sessionId = proxyPresenceTracker.getSessionId(player.getUniqueId().toString());
        // Settings sent before the login completed are picked up once the session exists.
        if (sessionId == null) {
            return;
        }

        sendClientSettings(player, sessionId, event.getPlayerSettings());
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

    private void sendClientSettings(Player player, String sessionId, PlayerSettings settings) {
        playerIntegration.updateClientSettings(sessionId, toClientSettings(settings))
                .exceptionally(e -> {
                    logger.error("Failed to send client settings for {}: {}", player.getUsername(), e.getMessage());
                    return null;
                });
    }

    static PlayerClientSettings toClientSettings(PlayerSettings settings) {
        PlayerClientSettings result = new PlayerClientSettings();
        result.setChatMode(switch (settings.getChatMode()) {
            case SHOWN -> PlayerClientSettings.ChatMode.ENABLED;
            case COMMANDS_ONLY -> PlayerClientSettings.ChatMode.COMMANDS_ONLY;
            case HIDDEN -> PlayerClientSettings.ChatMode.HIDDEN;
        });
        result.setLocale(settings.getLocale() != null ? settings.getLocale().toString() : null);
        result.setMainHand(settings.getMainHand() == PlayerSettings.MainHand.LEFT
                ? PlayerClientSettings.MainHand.LEFT
                : PlayerClientSettings.MainHand.RIGHT);
        result.setViewDistance(settings.getViewDistance());
        result.setChatColors(settings.hasChatColors());
        result.setClientListingAllowed(settings.isClientListingAllowed());
        result.setSkinParts(toSkinParts(settings.getSkinParts()));
        return result;
    }

    private static List<String> toSkinParts(SkinParts skinParts) {
        List<String> result = new ArrayList<>();
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
