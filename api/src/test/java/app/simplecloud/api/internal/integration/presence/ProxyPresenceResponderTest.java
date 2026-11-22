package app.simplecloud.api.internal.integration.presence;

import app.simplecloud.api.presence.ProxyPresencePlayer;
import app.simplecloud.api.support.NatsTest;
import app.simplecloud.api.support.TestNats;
import build.buf.gen.simplecloud.controller.v2.PresenceCompareRequest;
import build.buf.gen.simplecloud.controller.v2.ProxyPresenceCompareResponse;
import io.nats.client.Connection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@NatsTest
class ProxyPresenceResponderTest {

    private static final List<ProxyPresencePlayer> PLAYERS = List.of(new ProxyPresencePlayer(
            "11111111-1111-1111-1111-111111111111", "Steve", "Steve", "lobby-1", "proxy-1", 0, "en_us", 770, true, "session-1"
    ));

    private final String networkId = TestNats.randomNetworkId();
    private Connection proxyConnection;
    private Connection controller;
    private ProxyPresenceResponder responder;

    @BeforeEach
    void start() throws Exception {
        proxyConnection = TestNats.connect();
        controller = TestNats.connect();
        responder = new ProxyPresenceResponder(proxyConnection, networkId, "proxy-1", () -> PLAYERS);
        responder.start();
        proxyConnection.flush(TestNats.TIMEOUT);
    }

    @AfterEach
    void stop() throws InterruptedException {
        responder.stop();
        proxyConnection.close();
        controller.close();
    }

    @Test
    void matchingHashSkipsThePlayerList() throws Exception {
        ProxyPresenceCompareResponse response = compare(ProxyPresenceResponder.computeHash(PLAYERS));

        assertTrue(response.getMatch());
        assertEquals(0, response.getPlayersCount());
    }

    @Test
    void differentHashSendsAllPlayers() throws Exception {
        ProxyPresenceCompareResponse response = compare(0);

        assertFalse(response.getMatch());
        assertEquals("lobby-1", response.getPlayers(0).getConnectedServerName());
    }

    private ProxyPresenceCompareResponse compare(int hash) throws Exception {
        byte[] request = PresenceCompareRequest.newBuilder().setHash(hash).build().toByteArray();
        return ProxyPresenceCompareResponse.parseFrom(
                TestNats.request(controller, networkId + ".server.proxy-1.presence.compare", request).getData());
    }
}
