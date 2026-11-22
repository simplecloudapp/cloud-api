package app.simplecloud.api.internal.integration.presence;

import app.simplecloud.api.presence.ProxyPresencePlayer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ProxyPresenceHashTest {

    private static final ProxyPresencePlayer LOBBY_PLAYER =
            player("11111111-1111-1111-1111-111111111111", "lobby-1");
    private static final ProxyPresencePlayer GAME_PLAYER =
            player("22222222-2222-2222-2222-222222222222", "game-1");

    @Test
    void emptyProxyHashesToZero() {
        assertEquals(0, ProxyPresenceResponder.computeHash(List.of()));
    }

    @Test
    void hashesPlayerIdAndServerWithFnv1a() {
        assertEquals(-1839369380, ProxyPresenceResponder.computeHash(List.of(LOBBY_PLAYER)));
        assertEquals(1221966717, ProxyPresenceResponder.computeHash(List.of(LOBBY_PLAYER, GAME_PLAYER)));
    }

    @Test
    void serverSwitchChangesTheHash() {
        ProxyPresencePlayer switched = player("11111111-1111-1111-1111-111111111111", "lobby-2");

        assertNotEquals(
                ProxyPresenceResponder.computeHash(List.of(LOBBY_PLAYER)),
                ProxyPresenceResponder.computeHash(List.of(switched))
        );
    }

    private static ProxyPresencePlayer player(String playerId, String serverName) {
        return new ProxyPresencePlayer(playerId, "Player", null, serverName, "proxy-1", 0, "en_us", 770, true, "session");
    }
}
