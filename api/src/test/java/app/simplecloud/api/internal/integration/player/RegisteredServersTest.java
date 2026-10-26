package app.simplecloud.api.internal.integration.player;

import app.simplecloud.api.proxy.ProxyServerNames;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegisteredServersTest {

    private static final String SERVER_ID = "11111111-2222-3333-4444-555555555555";

    private final RegisteredServers<String> registeredServers = new OnlyLobbyRegistered();

    @Test
    void findById_findsServerUnderItsProxyName() {
        try (ProxyServerNames.Registration ignored = ProxyServerNames.register(RegisteredServersTest::proxyNameOfLobby)) {
            assertEquals(Optional.of("lobby-1-server"), registeredServers.findById(SERVER_ID));
        }
    }

    @Test
    void findById_unknownToLookup_returnsEmpty() {
        try (ProxyServerNames.Registration ignored = ProxyServerNames.register(id -> Optional.empty())) {
            assertTrue(registeredServers.findById(SERVER_ID).isEmpty());
        }
    }

    @Test
    void findById_proxyNameNotRegistered_returnsEmpty() {
        try (ProxyServerNames.Registration ignored = ProxyServerNames.register(id -> Optional.of("lobby-3"))) {
            assertTrue(registeredServers.findById(SERVER_ID).isEmpty());
        }
    }

    @Test
    void findById_withoutLookup_returnsEmpty() {
        assertTrue(registeredServers.findById(SERVER_ID).isEmpty());
    }

    private static Optional<String> proxyNameOfLobby(String serverId) {
        if (SERVER_ID.equals(serverId)) {
            return Optional.of("lobby-1");
        }
        return Optional.empty();
    }

    private static final class OnlyLobbyRegistered implements RegisteredServers<String> {

        @Override
        public Optional<String> findByName(String name) {
            if (name.equals("lobby-1")) {
                return Optional.of("lobby-1-server");
            }
            return Optional.empty();
        }
    }
}
