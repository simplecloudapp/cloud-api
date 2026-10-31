package app.simplecloud.api.proxy;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProxyServerNamesTest {

    private static final String SERVER_ID = "11111111-2222-3333-4444-555555555555";

    @Test
    void proxyNameOf_withoutRegistration_returnsEmpty() {
        assertTrue(ProxyServerNames.proxyNameOf(SERVER_ID).isEmpty());
    }

    @Test
    void proxyNameOf_withRegistration_usesLookup() {
        try (ProxyServerNames.Registration ignored = ProxyServerNames.register(id -> Optional.of("lobby-1"))) {
            assertEquals(Optional.of("lobby-1"), ProxyServerNames.proxyNameOf(SERVER_ID));
        }
    }

    @Test
    void register_replacesPreviousRegistration() {
        try (ProxyServerNames.Registration first = ProxyServerNames.register(id -> Optional.of("lobby-1"));
             ProxyServerNames.Registration second = ProxyServerNames.register(id -> Optional.of("lobby-2"))) {
            assertEquals(Optional.of("lobby-2"), ProxyServerNames.proxyNameOf(SERVER_ID));
        }
    }

    @Test
    void close_currentRegistration_clearsLookup() {
        ProxyServerNames.Registration registration = ProxyServerNames.register(id -> Optional.of("lobby-1"));

        registration.close();

        assertTrue(ProxyServerNames.proxyNameOf(SERVER_ID).isEmpty());
    }

    @Test
    void close_staleRegistration_keepsNewerRegistration() {
        ProxyServerNames.Registration stale = ProxyServerNames.register(id -> Optional.of("lobby-1"));
        try (ProxyServerNames.Registration current = ProxyServerNames.register(id -> Optional.of("lobby-2"))) {
            stale.close();

            assertEquals(Optional.of("lobby-2"), ProxyServerNames.proxyNameOf(SERVER_ID));
        }
    }

    @Test
    void close_twice_keepsNewerRegistration() {
        ProxyServerNames.Registration first = ProxyServerNames.register(id -> Optional.of("lobby-1"));
        first.close();
        try (ProxyServerNames.Registration second = ProxyServerNames.register(id -> Optional.of("lobby-2"))) {
            first.close();

            assertEquals(Optional.of("lobby-2"), ProxyServerNames.proxyNameOf(SERVER_ID));
        }
    }

    @Test
    void register_null_throws() {
        assertThrows(NullPointerException.class, () -> ProxyServerNames.register(null));
    }

    @Test
    void proxyNameOf_lookupReturningNull_throws() {
        try (ProxyServerNames.Registration ignored = ProxyServerNames.register(id -> null)) {
            assertThrows(NullPointerException.class, () -> ProxyServerNames.proxyNameOf(SERVER_ID));
        }
    }
}
