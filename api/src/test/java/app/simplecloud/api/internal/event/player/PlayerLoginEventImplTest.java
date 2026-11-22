package app.simplecloud.api.internal.event.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import build.buf.gen.simplecloud.player.v2.PlayerConfig;
import build.buf.gen.simplecloud.player.v2.PlayerLoginEvent;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlayerLoginEventImplTest {

    private static final UUID PLAYER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    void mapsPlayerIdAndTimestampInMillis() {
        PlayerLoginEventImpl event = event(PlayerLoginEvent.newBuilder()
                .setPlayerId(PLAYER_ID.toString())
                .setTimestamp(1_700_000_000_000L)
                .setIsNewPlayer(true));

        assertEquals(PLAYER_ID, event.getPlayerId());
        assertEquals("2023-11-14T22:13:20Z", event.getTimestamp());
        assertTrue(event.isNewPlayer());
    }

    @Test
    void emptyStringsBecomeNull() {
        PlayerLoginEventImpl event = event(PlayerLoginEvent.newBuilder().setPlayerId(PLAYER_ID.toString()));

        assertNull(event.getProxyName());
        assertNull(event.getSessionId());
        assertNull(event.getPlayer());
    }

    @Test
    void sessionIdFallsBackToPlayerConfig() {
        PlayerLoginEventImpl event = event(PlayerLoginEvent.newBuilder()
                .setPlayerId(PLAYER_ID.toString())
                .setConfig(PlayerConfig.newBuilder().setSessionId("session-1")));

        assertEquals("session-1", event.getSessionId());
    }

    private static PlayerLoginEventImpl event(PlayerLoginEvent.Builder builder) {
        return new PlayerLoginEventImpl(builder.build(), null, null);
    }
}
