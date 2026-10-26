package app.simplecloud.api.platform.bungeecord;

import app.simplecloud.api.internal.integration.player.ProxyPlayer;
import app.simplecloud.api.internal.integration.player.ProxyPlayers;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.connection.ProxiedPlayer;

import java.util.Optional;
import java.util.UUID;

final class BungeeCordPlayers implements ProxyPlayers {

    private final ProxyServer proxy;
    private final BungeeCordRegisteredServers registeredServers;

    BungeeCordPlayers(ProxyServer proxy) {
        this.proxy = proxy;
        this.registeredServers = new BungeeCordRegisteredServers(proxy);
    }

    @Override
    public Optional<ProxyPlayer> findPlayer(UUID playerId) {
        ProxiedPlayer player = proxy.getPlayer(playerId);
        if (player == null) {
            return Optional.empty();
        }
        return Optional.of(new BungeeCordPlayer(registeredServers, player));
    }
}
