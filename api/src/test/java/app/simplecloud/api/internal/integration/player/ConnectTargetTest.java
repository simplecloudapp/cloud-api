package app.simplecloud.api.internal.integration.player;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectTargetTest {

    private static final String SERVER_ID = "11111111-2222-3333-4444-555555555555";

    private final Map<String, String> registeredServers = Map.of("lobby-1", "lobby-1-server");
    private final Function<String, Optional<String>> byName = name -> Optional.ofNullable(registeredServers.get(name));

    private Function<String, Optional<String>> mapping;

    @AfterEach
    void unregisterMapping() {
        if (mapping != null) {
            ServerNameRegistry.unregister(mapping);
        }
    }

    @Test
    void resolve_byName_usesRegisteredName() {
        assertEquals(Optional.of("lobby-1-server"), ConnectTarget.byName("lobby-1").resolve(byName));
    }

    @Test
    void resolve_byUnknownName_returnsEmpty() {
        assertTrue(ConnectTarget.byName("lobby-2").resolve(byName).isEmpty());
    }

    @Test
    void resolve_byId_usesRegisteredMapping() {
        register(id -> SERVER_ID.equals(id) ? Optional.of("lobby-1") : Optional.empty());

        assertEquals(Optional.of("lobby-1-server"), ConnectTarget.byId(SERVER_ID).resolve(byName));
    }

    @Test
    void resolve_byIdWithoutMapping_returnsEmpty() {
        assertTrue(ConnectTarget.byId(SERVER_ID).resolve(byName).isEmpty());
    }

    @Test
    void resolve_byIdMappedToUnregisteredName_returnsEmpty() {
        register(id -> Optional.of("lobby-2"));

        assertTrue(ConnectTarget.byId(SERVER_ID).resolve(byName).isEmpty());
    }

    @Test
    void resolve_byId_doesNotTreatIdAsName() {
        Function<String, Optional<String>> idIsAlsoAName = name -> SERVER_ID.equals(name) ? Optional.of("by-name") : Optional.empty();

        assertTrue(ConnectTarget.byId(SERVER_ID).resolve(idIsAlsoAName).isEmpty());
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
}
