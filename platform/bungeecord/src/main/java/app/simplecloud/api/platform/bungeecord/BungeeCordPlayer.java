package app.simplecloud.api.platform.bungeecord;

import app.simplecloud.api.internal.integration.player.ProxyPlayer;
import app.simplecloud.api.player.CloudPlayer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.bungeecord.BungeeComponentSerializer;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;

import java.util.concurrent.CompletableFuture;

final class BungeeCordPlayer implements ProxyPlayer {

    private final ProxyServer proxy;
    private final ProxiedPlayer player;

    BungeeCordPlayer(ProxyServer proxy, ProxiedPlayer player) {
        this.proxy = proxy;
        this.player = player;
    }

    @Override
    public void kick(Component reason) {
        BaseComponent[] message = BungeeComponentSerializer.get().serialize(reason);
        player.disconnect(message);
    }

    @Override
    public CompletableFuture<CloudPlayer.ConnectResult> connect(String proxyServerName) {
        ServerInfo serverInfo = proxy.getServerInfo(proxyServerName);
        if (serverInfo == null) {
            return CompletableFuture.completedFuture(CloudPlayer.ConnectResult.SERVER_NOT_FOUND);
        }
        return connectTo(serverInfo);
    }

    private CompletableFuture<CloudPlayer.ConnectResult> connectTo(ServerInfo serverInfo) {
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
