package app.simplecloud.api.platform.bungeecord;

import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.config.ServerInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BungeeCordRegisteredServersTest {

    private final ProxyServer proxy = mock(ProxyServer.class);
    private final ServerInfo lobby = mock(ServerInfo.class);
    private final BungeeCordRegisteredServers registeredServers = new BungeeCordRegisteredServers(proxy);

    @BeforeEach
    void registerLobby() {
        when(proxy.getServerInfo("lobby-1")).thenReturn(lobby);
    }

    @Test
    void findByName_findsRegisteredServer() {
        assertEquals(Optional.of(lobby), registeredServers.findByName("lobby-1"));
    }

    @Test
    void findByName_unknownName_returnsEmpty() {
        assertTrue(registeredServers.findByName("lobby-3").isEmpty());
    }
}
