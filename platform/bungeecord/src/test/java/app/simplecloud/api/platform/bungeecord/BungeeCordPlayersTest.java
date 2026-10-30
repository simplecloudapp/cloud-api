package app.simplecloud.api.platform.bungeecord;

import app.simplecloud.api.internal.integration.player.ConnectTarget;
import app.simplecloud.api.internal.integration.player.ProxyPlayer;
import app.simplecloud.api.player.CloudPlayer;
import net.kyori.adventure.text.Component;
import net.md_5.bungee.api.Callback;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BungeeCordPlayersTest {

    private final UUID playerId = UUID.randomUUID();
    private final ProxyServer proxy = mock(ProxyServer.class);
    private final ProxiedPlayer player = mock(ProxiedPlayer.class);
    private final BungeeCordPlayers players = new BungeeCordPlayers(proxy);

    @Test
    void playerNotOnThisProxyIsNotFound() {
        assertTrue(players.findPlayer(playerId).isEmpty());
    }

    @Test
    void kickDisconnectsThePlayerWithTheReason() {
        when(proxy.getPlayer(playerId)).thenReturn(player);

        ProxyPlayer proxyPlayer = players.findPlayer(playerId).orElseThrow();
        proxyPlayer.kick(Component.text("Maintenance"));

        ArgumentCaptor<BaseComponent[]> message = ArgumentCaptor.forClass(BaseComponent[].class);
        verify(player).disconnect(message.capture());
        assertEquals("Maintenance", TextComponent.toPlainText(message.getValue()));
    }

    @Test
    void connectToUnregisteredServerAnswersServerNotFound() {
        when(proxy.getPlayer(playerId)).thenReturn(player);
        ConnectTarget target = ConnectTarget.byName("lobby-1", serverId -> {
            throw new AssertionError("Unexpected server lookup");
        });

        ProxyPlayer proxyPlayer = players.findPlayer(playerId).orElseThrow();
        CloudPlayer.ConnectResult result = proxyPlayer.connect(target).join();

        assertEquals(CloudPlayer.ConnectResult.SERVER_NOT_FOUND, result);
        verify(player, never()).connect(any(ServerInfo.class), ArgumentMatchers.<Callback<Boolean>>any());
    }
}
