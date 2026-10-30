package app.simplecloud.api.platform.velocity;

import app.simplecloud.api.internal.integration.player.ProxyPlayer;
import app.simplecloud.api.player.CloudPlayer;
import com.velocitypowered.api.proxy.ConnectionRequestBuilder;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

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

        ProxyPlayer proxyPlayer = players.findPlayer(playerId).orElseThrow();
        CloudPlayer.ConnectResult result = proxyPlayer.connect("lobby-1").join();

        assertEquals(CloudPlayer.ConnectResult.SERVER_NOT_FOUND, result);
        verify(player, never()).createConnectionRequest(any());
    }

    @Test
    void connectToRegisteredServerConnectsThePlayerToIt() {
        RegisteredServer lobby = mock(RegisteredServer.class);
        ConnectionRequestBuilder connectionRequest = mock(ConnectionRequestBuilder.class);
        ConnectionRequestBuilder.Result connected = mock(ConnectionRequestBuilder.Result.class);
        when(proxyServer.getPlayer(playerId)).thenReturn(Optional.of(player));
        when(proxyServer.getServer("lobby-1")).thenReturn(Optional.of(lobby));
        when(player.createConnectionRequest(lobby)).thenReturn(connectionRequest);
        when(connectionRequest.connect()).thenReturn(CompletableFuture.completedFuture(connected));
        when(connected.getStatus()).thenReturn(ConnectionRequestBuilder.Status.SUCCESS);

        ProxyPlayer proxyPlayer = players.findPlayer(playerId).orElseThrow();
        CloudPlayer.ConnectResult result = proxyPlayer.connect("lobby-1").join();

        assertEquals(CloudPlayer.ConnectResult.SUCCESS, result);
        verify(player).createConnectionRequest(lobby);
    }
}
