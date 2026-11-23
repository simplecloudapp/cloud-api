package app.simplecloud.api.internal.event.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.simplecloud.api.player.CloudPlayer;
import build.buf.gen.simplecloud.player.v2.PlayerConfig;
import build.buf.gen.simplecloud.player.v2.PlayerConnectionConfig;
import build.buf.gen.simplecloud.player.v2.PlayerPropertiesUpdatedEvent;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlayerPropertiesUpdatedEventImplTest {

    private static final UUID PLAYER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    void mapsOldAndCurrentProperties() {
        PlayerPropertiesUpdatedEventImpl event = event(PlayerPropertiesUpdatedEvent.newBuilder()
                .setPlayerId(PLAYER_ID.toString())
                .putOldProperties("rank", "member")
                .putProperties("rank", "admin"));

        assertEquals(Map.of("rank", "member"), event.getOldProperties());
        assertEquals(Map.of("rank", "admin"), event.getProperties());
    }

    @Test
    void mapsPlayerFromConfig() {
        PlayerPropertiesUpdatedEventImpl event = event(PlayerPropertiesUpdatedEvent.newBuilder()
                .setPlayerId(PLAYER_ID.toString())
                .setConfig(PlayerConfig.newBuilder()
                        .setUniqueId(PLAYER_ID.toString())
                        .setName("Steve")
                        .setFirstSeen(1_700_000_000_000L)
                        .setLastSeen(1_700_000_060_000L)
                        .setConnection(PlayerConnectionConfig.newBuilder().setOnline(true))
                        .putProperties("rank", "admin")));

        CloudPlayer player = event.getPlayer();
        assertEquals("Steve", player.getName());
        assertEquals("2023-11-14T22:13:20Z", player.getFirstSeen());
        assertEquals("2023-11-14T22:14:20Z", player.getLastSeen());
        assertEquals(Map.of("rank", "admin"), player.getProperties());
        assertTrue(player.isOnline());
    }

    @Test
    void missingConfigHasNoPlayer() {
        PlayerPropertiesUpdatedEventImpl event =
                event(PlayerPropertiesUpdatedEvent.newBuilder().setPlayerId(PLAYER_ID.toString()));

        assertNull(event.getPlayer());
    }

    private static PlayerPropertiesUpdatedEventImpl event(PlayerPropertiesUpdatedEvent.Builder builder) {
        return new PlayerPropertiesUpdatedEventImpl(builder.build(), null, null);
    }
}
