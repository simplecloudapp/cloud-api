package app.simplecloud.api.platform.bungeecord;

import app.simplecloud.api.CloudApi;
import app.simplecloud.api.internal.CloudApiImpl;
import app.simplecloud.api.internal.integration.player.ConnectTarget;
import app.simplecloud.api.internal.integration.player.PlayerIntegration;
import app.simplecloud.api.internal.integration.presence.ProxyPresenceResponder;
import app.simplecloud.api.internal.integration.presence.ProxyPresenceTracker;
import app.simplecloud.api.presence.ProxyPresencePlayer;
import app.simplecloud.api.presence.ProxyPresencePlayerProvider;
import app.simplecloud.api.player.CloudPlayer;
import app.simplecloud.api.runtime.SimpleCloudRuntime;
import app.simplecloud.api.platform.shared.LuckPermsPlayerPropertySynchronizer;
import app.simplecloud.api.platform.shared.PlayerSynchronizer;
import net.kyori.adventure.platform.bungeecord.BungeeAudiences;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.bungeecord.BungeeComponentSerializer;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Plugin;
import net.luckperms.api.LuckPermsProvider;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class BungeeCordApiProvider extends Plugin implements ProxyPresencePlayerProvider {

    private CloudApiImpl cloudApi;
    private String proxyName;
    private PlayerSynchronizer playerSynchronizer;
    private PlayerIntegration playerIntegration;
    private ProxyPresenceTracker proxyPresenceTracker;
    private ProxyPresenceResponder proxyPresenceResponder;
    private LuckPermsPlayerPropertySynchronizer luckPermsSynchronizer;

    private BungeeAudiences bungeeAudiences;

    private final BungeeComponentSerializer bungeeComponentSerializer = BungeeComponentSerializer.get();
    private final GsonComponentSerializer gsonComponentSerializer = GsonComponentSerializer.gson();

    @Override
    public void onEnable() {
        this.cloudApi = (CloudApiImpl) CloudApi.create();
        this.proxyName = SimpleCloudRuntime.serverName();
        this.playerSynchronizer = new PlayerSynchronizer(
            cloudApi,
            () -> (long) getProxy().getOnlineCount()
        );
        this.playerIntegration = new PlayerIntegration(cloudApi);
        this.proxyPresenceTracker = new ProxyPresenceTracker(proxyName);
        this.proxyPresenceResponder = new ProxyPresenceResponder(
                cloudApi.getNatsConnection(),
                cloudApi.getNetworkId(),
                SimpleCloudRuntime.serverId(),
                this
        );

        playerIntegration.onKick(this::handleKickRequest);
        playerIntegration.onConnect(this::handleConnectRequest);

        this.bungeeAudiences = BungeeAudiences.create(this);

        initializeLuckPermsIntegration();

        getLogger().info("SimpleCloud v3 API provider initialized!");
        getProxy().getPluginManager().registerListener(this, new PlayerConnectionListener(
                this,
                playerSynchronizer,
                playerIntegration,
                proxyPresenceTracker,
                proxyName,
                this::synchronizeLuckPermsProperties,
                this::forgetLuckPermsProperties
        ));

        playerSynchronizer.start();
        playerIntegration.start();
        proxyPresenceResponder.start();
    }

    @Override
    public void onDisable() {
        getLogger().info("SimpleCloud v3 API provider uninitialized!");
        proxyPresenceResponder.stop();
        if (luckPermsSynchronizer != null) {
            luckPermsSynchronizer.stop();
        }
        playerSynchronizer.stop();
        playerIntegration.stop();

        if (bungeeAudiences != null) {
            bungeeAudiences.close();
        }
        cloudApi.close();
    }

    private void initializeLuckPermsIntegration() {
        if (getProxy().getPluginManager().getPlugin("LuckPerms") == null) {
            return;
        }
        try {
            luckPermsSynchronizer = new LuckPermsPlayerPropertySynchronizer(
                    cloudApi,
                    LuckPermsProvider.get(),
                    this,
                    uniqueId -> getProxy().getPlayer(uniqueId) != null,
                    (message, throwable) -> getLogger().log(java.util.logging.Level.SEVERE, message, throwable)
            );
            luckPermsSynchronizer.start();
            getLogger().info("LuckPerms player property synchronization enabled");
        } catch (IllegalStateException exception) {
            luckPermsSynchronizer = null;
            getLogger().log(java.util.logging.Level.WARNING, "LuckPerms is present but its API is not available", exception);
        }
    }

    private void synchronizeLuckPermsProperties(UUID uniqueId) {
        if (luckPermsSynchronizer != null) {
            luckPermsSynchronizer.synchronize(uniqueId);
        }
    }

    private void forgetLuckPermsProperties(UUID uniqueId) {
        if (luckPermsSynchronizer != null) {
            luckPermsSynchronizer.forget(uniqueId);
        }
    }

    @Override
    public List<ProxyPresencePlayer> getProxyPresencePlayers() {
        return getProxy().getPlayers().stream()
                .map(this::toPresencePlayer)
                .toList();
    }

    private ProxyPresencePlayer toPresencePlayer(ProxiedPlayer player) {
        String connectedServerName = player.getServer() != null ? player.getServer().getInfo().getName() : "";
        Locale locale = player.getLocale();
        var pendingConnection = player.getPendingConnection();

        return proxyPresenceTracker.createSnapshot(
                player.getUniqueId().toString(),
                player.getName(),
                player.getDisplayName(),
                connectedServerName,
                locale != null ? locale.toString() : "en_US",
                pendingConnection != null ? pendingConnection.getVersion() : 0,
                pendingConnection != null && pendingConnection.isOnlineMode()
        );
    }

    private CompletableFuture<Boolean> handleKickRequest(String playerUniqueId, String reason) {
        return CompletableFuture.supplyAsync(() -> {
            ProxiedPlayer player = getProxy().getPlayer(UUID.fromString(playerUniqueId));
            if (player == null) {
                // Don't respond - another proxy might have the player
                return null;
            }
            Component message = reason != null ? gsonComponentSerializer.deserialize(reason) : Component.empty();
            player.disconnect(bungeeComponentSerializer.serialize(message));
            return true;
        });
    }

    private CompletableFuture<CloudPlayer.ConnectResult> handleConnectRequest(String playerUniqueId, ConnectTarget target) {
        ProxiedPlayer player = getProxy().getPlayer(UUID.fromString(playerUniqueId));
        if (player == null) {
            return CompletableFuture.completedFuture(null);
        }
        return target.resolve(name -> Optional.ofNullable(getProxy().getServerInfo(name)), this::findServerByAddress)
                .thenCompose(serverInfo -> serverInfo
                        .map(server -> connect(player, server))
                        .orElseGet(() -> CompletableFuture.completedFuture(CloudPlayer.ConnectResult.SERVER_NOT_FOUND)));
    }

    private Optional<ServerInfo> findServerByAddress(String ip, int port) {
        return getProxy().getServers().values().stream()
                .filter(server -> ConnectTarget.hasAddress(server.getSocketAddress(), ip, port))
                .findFirst();
    }

    private CompletableFuture<CloudPlayer.ConnectResult> connect(ProxiedPlayer player, ServerInfo serverInfo) {
        if (player.getServer() != null && player.getServer().getInfo().getName().equals(serverInfo.getName())) {
            return CompletableFuture.completedFuture(CloudPlayer.ConnectResult.ALREADY_CONNECTED);
        }

        CompletableFuture<CloudPlayer.ConnectResult> future = new CompletableFuture<>();
        player.connect(serverInfo, (success, error) -> {
            if (success) {
                future.complete(CloudPlayer.ConnectResult.SUCCESS);
            } else {
                future.complete(CloudPlayer.ConnectResult.CONNECTION_FAILED);
            }
        });
        return future;
    }
}
