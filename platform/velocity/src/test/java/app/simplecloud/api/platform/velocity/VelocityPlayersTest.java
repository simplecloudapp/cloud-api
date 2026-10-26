package app.simplecloud.api.platform.velocity;

import app.simplecloud.api.internal.integration.player.ConnectTarget;
import app.simplecloud.api.internal.integration.player.ProxyPlayer;
import app.simplecloud.api.player.CloudPlayer;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class VelocityPlayersTest {

    private final UUID playerId = UUID.randomUUID();
    private final ProxyServer proxyServer = mock(ProxyServer.class);
    private final Player player = mock(Player.class);
    private final VelocityPlayers players = new VelocityPlayers(proxyServer);

    @Test
    void playerNotOnThisProxyIsNotFound() {
        when(proxyServer.getPlayer(playerId)).thenReturn(Optional.empty());

        assertTrue(players.findPlayer(playerId).isEmpty());
    }

    @Test
    void kickDisconnectsThePlayerWithTheReason() {
        when(proxyServer.getPlayer(playerId)).thenReturn(Optional.of(player));
        Component reason = Component.text("Maintenance");

        ProxyPlayer proxyPlayer = players.findPlayer(playerId).orElseThrow();
        proxyPlayer.kick(reason);

        verify(player).disconnect(reason);
    }

    @Test
    void connectToUnregisteredServerAnswersServerNotFound() {
        when(proxyServer.getPlayer(playerId)).thenReturn(Optional.of(player));
        when(proxyServer.getServer("lobby-1")).thenReturn(Optional.empty());
        ConnectTarget target = ConnectTarget.byName("lobby-1", serverId -> {
            throw new AssertionError("Unexpected server lookup");
        });

        ProxyPlayer proxyPlayer = players.findPlayer(playerId).orElseThrow();
        CloudPlayer.ConnectResult result = proxyPlayer.connect(target).join();

        assertEquals(CloudPlayer.ConnectResult.SERVER_NOT_FOUND, result);
        verify(player, never()).createConnectionRequest(any());
    }
}
