package app.simplecloud.api.platform.bungeecord;

import app.simplecloud.api.CloudApi;
import app.simplecloud.api.internal.CloudApiImpl;
import app.simplecloud.api.internal.integration.player.PlayerIntegration;
import app.simplecloud.api.internal.integration.presence.ProxyPresenceResponder;
import app.simplecloud.api.internal.integration.presence.ProxyPresenceTracker;
import app.simplecloud.api.presence.ProxyPresencePlayer;
import app.simplecloud.api.presence.ProxyPresencePlayerProvider;
import app.simplecloud.api.player.CloudPlayer;
import app.simplecloud.api.runtime.SimpleCloudRuntime;
import app.simplecloud.api.platform.shared.PlayerSynchronizer;
import net.kyori.adventure.platform.bungeecord.BungeeAudiences;
import net.kyori.adventure.text.serializer.bungeecord.BungeeComponentSerializer;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Plugin;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class BungeeCordApiProvider extends Plugin implements ProxyPresencePlayerProvider {

    private CloudApiImpl cloudApi;
    private String proxyName;
    private PlayerSynchronizer playerSynchronizer;
    private PlayerIntegration playerIntegration;
    private ProxyPresenceTracker proxyPresenceTracker;
    private ProxyPresenceResponder proxyPresenceResponder;

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

        getLogger().info("SimpleCloud v3 API provider initialized!");
        getProxy().getPluginManager().registerListener(this, new PlayerConnectionListener(
                playerSynchronizer,
                playerIntegration,
                proxyPresenceTracker,
                proxyName
        ));

        playerSynchronizer.start();
        playerIntegration.start();
        proxyPresenceResponder.start();
    }

    @Override
    public void onDisable() {
        getLogger().info("SimpleCloud v3 API provider uninitialized!");
        proxyPresenceResponder.stop();
        playerSynchronizer.stop();
        playerIntegration.stop();

        if (bungeeAudiences != null) {
            bungeeAudiences.close();
        }
        cloudApi.close();
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
            player.disconnect(bungeeComponentSerializer.serialize(gsonComponentSerializer.deserialize(reason != null ? reason : "{}")));
            return true;
        });
    }

    private CompletableFuture<CloudPlayer.ConnectResult> handleConnectRequest(String playerUniqueId, String serverName) {
        ProxiedPlayer player = getProxy().getPlayer(UUID.fromString(playerUniqueId));
        if (player == null) {
            return CompletableFuture.completedFuture(null);
        }
        var serverInfo = getProxy().getServerInfo(serverName);
        if (serverInfo == null) {
            return CompletableFuture.completedFuture(CloudPlayer.ConnectResult.SERVER_NOT_FOUND);
        }
        if (player.getServer() != null && player.getServer().getInfo().getName().equals(serverName)) {
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
