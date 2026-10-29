package app.simplecloud.api.platform.velocity;

import app.simplecloud.api.internal.integration.player.ProxyPlayer;
import app.simplecloud.api.internal.integration.player.ProxyPlayers;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;

import java.util.Optional;
import java.util.UUID;

final class VelocityPlayers implements ProxyPlayers {

    private final ProxyServer proxyServer;

    VelocityPlayers(ProxyServer proxyServer) {
        this.proxyServer = proxyServer;
    }

    @Override
    public Optional<ProxyPlayer> findPlayer(UUID playerId) {
        Optional<Player> player = proxyServer.getPlayer(playerId);
        return player.map(found -> new VelocityPlayer(proxyServer, found));
    }
}
