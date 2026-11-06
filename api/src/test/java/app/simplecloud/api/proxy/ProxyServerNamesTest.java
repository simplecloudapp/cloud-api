package app.simplecloud.api.proxy;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProxyServerNamesTest {

    private static final String SERVER_ID = "11111111-2222-3333-4444-555555555555";

    @Test
    void withoutRegistrationNothingIsFound() {
        assertTrue(ProxyServerNames.proxyNameOf(SERVER_ID).isEmpty());
    }

    @Test
    void newerRegistrationReplacesOlderOne() {
        try (ProxyServerNames.Registration ignored = ProxyServerNames.register(id -> Optional.of("lobby-1"));
                ProxyServerNames.Registration current = ProxyServerNames.register(id -> Optional.of("lobby-2"))) {
            assertEquals(Optional.of("lobby-2"), ProxyServerNames.proxyNameOf(SERVER_ID));
        }
    }

    @Test
    void closingCurrentRegistrationRemovesLookup() {
        ProxyServerNames.register(id -> Optional.of("lobby-1")).close();

        assertTrue(ProxyServerNames.proxyNameOf(SERVER_ID).isEmpty());
    }

    @Test
    void closingReplacedRegistrationKeepsCurrentOne() {
        ProxyServerNames.Registration replaced = ProxyServerNames.register(id -> Optional.of("lobby-1"));
        try (ProxyServerNames.Registration current = ProxyServerNames.register(id -> Optional.of("lobby-2"))) {
            replaced.close();

            assertEquals(Optional.of("lobby-2"), ProxyServerNames.proxyNameOf(SERVER_ID));
        }
    }
}
