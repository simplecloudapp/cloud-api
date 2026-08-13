package app.simplecloud.api.internal.integration.presence;

import app.simplecloud.api.presence.ProxyPresencePlayer;
import build.buf.gen.simplecloud.controller.v2.PresenceCompareRequest;
import build.buf.gen.simplecloud.controller.v2.ProxyPresenceCompareResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProxyPresenceResponderTest {

    @Test
    void returnsMatchWithoutSnapshotWhenSummaryMatches() {
        ProxyPresenceCompareResponse response = ProxyPresenceResponder.buildResponse(
                PresenceCompareRequest.newBuilder().setHash(0).build(),
                List.of()
        );

        assertTrue(response.getMatch());
        assertTrue(response.getPlayersList().isEmpty());
    }

    @Test
    void returnsEveryLivePlayerWhenSummaryDiffers() {
        ProxyPresencePlayer player = new ProxyPresencePlayer(
                "player-1",
                "PlayerOne",
                "PlayerOne",
                "",
                "Proxy-1",
                1L,
                "en_US",
                765,
                true,
                "session-1"
        );

        ProxyPresenceCompareResponse response = ProxyPresenceResponder.buildResponse(
                PresenceCompareRequest.newBuilder().setHash(0).build(),
                List.of(player)
        );

        assertFalse(response.getMatch());
        assertTrue(response.getPlayersList().stream().anyMatch(snapshot -> snapshot.getPlayerId().equals("player-1")));
    }

    @Test
    void equalCountsWithDifferentPlayersProduceDifferentHashes() {
        ProxyPresencePlayer first = player("player-1");
        ProxyPresencePlayer second = player("player-2");

        assertNotEquals(
                ProxyPresenceResponder.computeHash(List.of(first)),
                ProxyPresenceResponder.computeHash(List.of(second))
        );
        assertEquals(0, ProxyPresenceResponder.computeHash(List.of()));
    }

    @Test
    void hashMatchesControllerWireContract() {
        ProxyPresencePlayer player = new ProxyPresencePlayer(
                "player-1",
                "PlayerOne",
                "PlayerOne",
                "Lobby-1",
                "",
                0L,
                "",
                0,
                true,
                ""
        );

        assertEquals(1745198624, ProxyPresenceResponder.computeHash(List.of(player)));
    }

    private static ProxyPresencePlayer player(String playerId) {
        return new ProxyPresencePlayer(
                playerId,
                playerId,
                playerId,
                "",
                "Proxy-1",
                1L,
                "en_US",
                765,
                true,
                "session-1"
        );
    }
}
