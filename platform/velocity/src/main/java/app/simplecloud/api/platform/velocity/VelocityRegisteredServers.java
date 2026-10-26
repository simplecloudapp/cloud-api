package app.simplecloud.api.platform.velocity;

import app.simplecloud.api.internal.integration.player.RegisteredServers;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;

import java.util.Optional;

final class VelocityRegisteredServers implements RegisteredServers<RegisteredServer> {

    private final ProxyServer proxyServer;

    VelocityRegisteredServers(ProxyServer proxyServer) {
        this.proxyServer = proxyServer;
    }

    @Override
    public Optional<RegisteredServer> findByName(String name) {
        return proxyServer.getServer(name);
    }
}
