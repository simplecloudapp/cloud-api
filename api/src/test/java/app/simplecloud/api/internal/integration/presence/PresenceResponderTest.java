package app.simplecloud.api.internal.integration.presence;

import app.simplecloud.api.presence.PresencePlayer;
import build.buf.gen.simplecloud.controller.v2.PresenceCompareRequest;
import build.buf.gen.simplecloud.controller.v2.ProxyPresenceCompareResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PresenceResponderTest {

    @Test
    void returnsMatchWithoutSnapshotWhenSummaryMatches() {
        ProxyPresenceCompareResponse response = PresenceResponder.buildResponse(
                PresenceCompareRequest.newBuilder().setHash(0).build(),
                List.of()
        );

        assertTrue(response.getMatch());
        assertTrue(response.getPlayersList().isEmpty());
    }

    @Test
    void returnsEveryLivePlayerWhenSummaryDiffers() {
        PresencePlayer player = new PresencePlayer(
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

        ProxyPresenceCompareResponse response = PresenceResponder.buildResponse(
                PresenceCompareRequest.newBuilder().setHash(0).build(),
                List.of(player)
        );

        assertFalse(response.getMatch());
        assertTrue(response.getPlayersList().stream().anyMatch(snapshot -> snapshot.getPlayerId().equals("player-1")));
    }

    @Test
    void equalCountsWithDifferentPlayersProduceDifferentHashes() {
        PresencePlayer first = player("player-1");
        PresencePlayer second = player("player-2");

        assertNotEquals(
                PresenceResponder.computeHash(List.of(first)),
                PresenceResponder.computeHash(List.of(second))
        );
        assertEquals(0, PresenceResponder.computeHash(List.of()));
    }

    @Test
    void hashMatchesControllerWireContract() {
        PresencePlayer player = new PresencePlayer(
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

        assertEquals(1745198624, PresenceResponder.computeHash(List.of(player)));
    }

    private static PresencePlayer player(String playerId) {
        return new PresencePlayer(
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
