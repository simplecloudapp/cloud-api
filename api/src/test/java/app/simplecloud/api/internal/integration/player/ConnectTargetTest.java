package app.simplecloud.api.internal.integration.player;

import app.simplecloud.api.server.Server;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectTargetTest {

    private static final String SERVER_ID = "11111111-2222-3333-4444-555555555555";

    private final Map<String, String> registeredServers = Map.of("lobby-1", "lobby-1-server");
    private final Function<String, Optional<String>> byName = name -> Optional.ofNullable(registeredServers.get(name));
    private final BiFunction<String, Integer, Optional<String>> byAddress =
            (ip, port) -> "10.0.0.5".equals(ip) && port == 25565 ? Optional.of("by-address") : Optional.empty();
    private final Function<String, CompletableFuture<Optional<Server>>> noServers =
            id -> CompletableFuture.completedFuture(Optional.empty());
    private final Function<String, CompletableFuture<Optional<Server>>> knownServer =
            id -> CompletableFuture.completedFuture(SERVER_ID.equals(id) ? Optional.of(server("10.0.0.5", 25565)) : Optional.empty());

    private Function<String, Optional<String>> mapping;

    @AfterEach
    void unregisterMapping() {
        if (mapping != null) {
            ServerNameRegistry.unregister(mapping);
        }
    }

    @Test
    void resolve_byName_usesRegisteredName() {
        assertEquals(Optional.of("lobby-1-server"), ConnectTarget.byName("lobby-1", noServers).resolve(byName, byAddress).join());
    }

    @Test
    void resolve_byUnknownName_returnsEmpty() {
        assertTrue(ConnectTarget.byName("lobby-2", knownServer).resolve(byName, byAddress).join().isEmpty());
    }

    @Test
    void resolve_byUnregisteredIdAsName_matchesAddress() {
        assertEquals(Optional.of("by-address"), ConnectTarget.byName(SERVER_ID, knownServer).resolve(byName, byAddress).join());
    }

    @Test
    void resolve_byId_usesRegisteredMapping() {
        register(id -> SERVER_ID.equals(id) ? Optional.of("lobby-1") : Optional.empty());

        assertEquals(Optional.of("lobby-1-server"), ConnectTarget.byId(SERVER_ID, knownServer).resolve(byName, byAddress).join());
    }

    @Test
    void resolve_byIdWithoutMapping_matchesAddress() {
        assertEquals(Optional.of("by-address"), ConnectTarget.byId(SERVER_ID, knownServer).resolve(byName, byAddress).join());
    }

    @Test
    void resolve_byIdOfUnknownServer_returnsEmpty() {
        assertTrue(ConnectTarget.byId(SERVER_ID, noServers).resolve(byName, byAddress).join().isEmpty());
    }

    @Test
    void resolve_byIdMappedToUnregisteredName_matchesAddress() {
        register(id -> Optional.of("lobby-2"));

        assertEquals(Optional.of("by-address"), ConnectTarget.byId(SERVER_ID, knownServer).resolve(byName, byAddress).join());
    }

    @Test
    void resolve_byId_doesNotTreatIdAsName() {
        Function<String, Optional<String>> idIsAlsoAName = name -> SERVER_ID.equals(name) ? Optional.of("by-name") : Optional.empty();

        assertTrue(ConnectTarget.byId(SERVER_ID, noServers).resolve(idIsAlsoAName, byAddress).join().isEmpty());
    }

    @Test
    void hasAddress_matchesUnresolvedAndResolvedAddresses() {
        assertTrue(ConnectTarget.hasAddress(InetSocketAddress.createUnresolved("10.0.0.5", 25565), "10.0.0.5", 25565));
        assertTrue(ConnectTarget.hasAddress(new InetSocketAddress("127.0.0.1", 25565), "127.0.0.1", 25565));
        assertFalse(ConnectTarget.hasAddress(InetSocketAddress.createUnresolved("10.0.0.5", 25566), "10.0.0.5", 25565));
        assertFalse(ConnectTarget.hasAddress(InetSocketAddress.createUnresolved("10.0.0.6", 25565), "10.0.0.5", 25565));
    }

    @Test
    void unregister_otherMapping_keepsCurrentMapping() {
        register(id -> Optional.of("lobby-1"));

        ServerNameRegistry.unregister(id -> Optional.of("other"));

        assertEquals(Optional.of("lobby-1"), ServerNameRegistry.nameOf(SERVER_ID));
    }

    @Test
    void unregister_currentMapping_clearsMapping() {
        register(id -> Optional.of("lobby-1"));

        ServerNameRegistry.unregister(mapping);

        assertTrue(ServerNameRegistry.nameOf(SERVER_ID).isEmpty());
    }

    private void register(Function<String, Optional<String>> mapping) {
        this.mapping = mapping;
        ServerNameRegistry.register(mapping);
    }

    private static Server server(String ip, int port) {
        return (Server) Proxy.newProxyInstance(
                Server.class.getClassLoader(),
                new Class<?>[]{Server.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getIp" -> ip;
                    case "getPort" -> port;
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }
}
