package app.simplecloud.api.platform.velocity;

import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VelocityRegisteredServersTest {

    private final ProxyServer proxyServer = mock(ProxyServer.class);
    private final RegisteredServer lobby = mock(RegisteredServer.class);
    private final VelocityRegisteredServers registeredServers = new VelocityRegisteredServers(proxyServer);

    @BeforeEach
    void registerLobby() {
        when(proxyServer.getServer("lobby-1")).thenReturn(Optional.of(lobby));
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
