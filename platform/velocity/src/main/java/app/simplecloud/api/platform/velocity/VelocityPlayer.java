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
        return player.createConnectionRequest(registeredServer).connect()
                .thenApply(result -> {
                    ConnectionRequestBuilder.Status status = result.getStatus();
                    if (status == ConnectionRequestBuilder.Status.SUCCESS) {
                        return CloudPlayer.ConnectResult.SUCCESS;
                    } else if (status == ConnectionRequestBuilder.Status.ALREADY_CONNECTED) {
                        return CloudPlayer.ConnectResult.ALREADY_CONNECTED;
                    } else {
                        return CloudPlayer.ConnectResult.CONNECTION_FAILED;
                    }
                });
    }
}
