package app.simplecloud.api.proxy;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProxyServerNamesTest {

    @Test
    void resolvesNamesThroughTheRegisteredLookup() {
        try (ProxyServerNames.Registration ignored = ProxyServerNames.register(serverId -> Optional.of("proxy-" + serverId))) {
            assertEquals(Optional.of("proxy-server-1"), ProxyServerNames.proxyNameOf("server-1"));
        }

        assertEquals(Optional.empty(), ProxyServerNames.proxyNameOf("server-1"));
    }

    @Test
    void closingAnOldRegistrationKeepsTheNewOne() {
        ProxyServerNames.Registration old = ProxyServerNames.register(serverId -> Optional.of("old"));
        try (ProxyServerNames.Registration ignored = ProxyServerNames.register(serverId -> Optional.of("new"))) {
            old.close();

            assertEquals(Optional.of("new"), ProxyServerNames.proxyNameOf("server-1"));
        }
    }
}
