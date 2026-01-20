package app.simplecloud.api.platform.bungeecord;

import app.simplecloud.api.CloudApi;
import app.simplecloud.api.internal.integration.player.PlayerIntegration;
import app.simplecloud.api.player.CloudPlayer;
import app.simplecloud.api.platform.shared.PlayerSynchronizer;
import net.kyori.adventure.platform.bungeecord.BungeeAudiences;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Plugin;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class BungeeCordApiProvider extends Plugin {

    private CloudApi cloudApi;
    private PlayerSynchronizer playerSynchronizer;
    private PlayerIntegration playerIntegration;

    private BungeeAudiences bungeeAudiences;

    @Override
    public void onEnable() {
        this.cloudApi = CloudApi.create();
        this.playerSynchronizer = new PlayerSynchronizer(
            cloudApi,
            () -> (long) getProxy().getOnlineCount()
        );
        this.playerIntegration = new PlayerIntegration(cloudApi);

        playerIntegration.onKick(this::handleKickRequest);
        playerIntegration.onConnect(this::handleConnectRequest);

        this.bungeeAudiences = BungeeAudiences.create(this);

        getLogger().info("SimpleCloud v3 API provider initialized!");
        getProxy().getPluginManager().registerListener(this, new PlayerConnectionListener(playerSynchronizer, playerIntegration));

        playerSynchronizer.start();
        playerIntegration.start();
    }

    @Override
    public void onDisable() {
        getLogger().info("SimpleCloud v3 API provider uninitialized!");
        playerSynchronizer.stop();
        playerIntegration.stop();

        if (bungeeAudiences != null) {
            bungeeAudiences.close();
        }
    }

    private CompletableFuture<Boolean> handleKickRequest(String playerUniqueId, String reason) {
        return CompletableFuture.supplyAsync(() -> {
            ProxiedPlayer player = getProxy().getPlayer(UUID.fromString(playerUniqueId));
            if (player == null) {
                // Don't respond - another proxy might have the player
                return null;
            }
            player.disconnect(TextComponent.fromLegacy(reason != null ? reason : ""));
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


