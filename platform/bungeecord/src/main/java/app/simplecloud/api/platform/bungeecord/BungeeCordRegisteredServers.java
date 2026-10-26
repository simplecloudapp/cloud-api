package app.simplecloud.api.platform.bungeecord;

import app.simplecloud.api.internal.integration.player.RegisteredServers;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.config.ServerInfo;

import java.util.Optional;

final class BungeeCordRegisteredServers implements RegisteredServers<ServerInfo> {

    private final ProxyServer proxy;

    BungeeCordRegisteredServers(ProxyServer proxy) {
        this.proxy = proxy;
    }

    @Override
    public Optional<ServerInfo> findByName(String name) {
        ServerInfo serverInfo = proxy.getServerInfo(name);
        return Optional.ofNullable(serverInfo);
    }
}
