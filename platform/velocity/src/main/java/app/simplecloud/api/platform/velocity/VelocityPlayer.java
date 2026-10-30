package app.simplecloud.api.platform.velocity;

import app.simplecloud.api.internal.integration.player.ProxyPlayer;
import app.simplecloud.api.player.CloudPlayer;
import com.velocitypowered.api.proxy.ConnectionRequestBuilder;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

final class VelocityPlayer implements ProxyPlayer {

    private final ProxyServer proxyServer;
    private final Player player;

    VelocityPlayer(ProxyServer proxyServer, Player player) {
        this.proxyServer = proxyServer;
        this.player = player;
    }

    @Override
    public void kick(Component reason) {
        player.disconnect(reason);
    }

    @Override
    public CompletableFuture<CloudPlayer.ConnectResult> connect(String proxyServerName) {
        Optional<RegisteredServer> registeredServer = proxyServer.getServer(proxyServerName);
        if (registeredServer.isEmpty()) {
            return CompletableFuture.completedFuture(CloudPlayer.ConnectResult.SERVER_NOT_FOUND);
        }
        return connectTo(registeredServer.get());
    }

    private CompletableFuture<CloudPlayer.ConnectResult> connectTo(RegisteredServer registeredServer) {
        ConnectionRequestBuilder connectionRequest = player.createConnectionRequest(registeredServer);
        CompletableFuture<ConnectionRequestBuilder.Result> connection = connectionRequest.connect();
        return connection.thenApply(result -> toConnectResult(result.getStatus()));
    }

    private static CloudPlayer.ConnectResult toConnectResult(ConnectionRequestBuilder.Status status) {
        return switch (status) {
            case SUCCESS -> CloudPlayer.ConnectResult.SUCCESS;
            case ALREADY_CONNECTED -> CloudPlayer.ConnectResult.ALREADY_CONNECTED;
            default -> CloudPlayer.ConnectResult.CONNECTION_FAILED;
        };
    }
}
