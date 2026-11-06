package app.simplecloud.api.player;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlayerRequestValidationTest {

    @Test
    void queryLimitMustBeInRange() {
        assertThrows(IllegalArgumentException.class, () -> PlayerQuery.create().limit(0));
        assertThrows(IllegalArgumentException.class, () -> PlayerQuery.create().limit(PlayerQuery.MAX_LIMIT + 1));
        assertEquals(PlayerQuery.MAX_LIMIT, PlayerQuery.create().limit(PlayerQuery.MAX_LIMIT).getLimit());
    }

    @Test
    void queryRejectsNegativeValues() {
        assertThrows(IllegalArgumentException.class, () -> PlayerQuery.create().offset(-1));
        assertThrows(
                IllegalArgumentException.class, () -> PlayerQuery.create().filterByOnlineTimeSeconds(-1L, null));
    }

    @Test
    void queryCopyIsIndependent() {
        PlayerQuery original = PlayerQuery.create().filterByPlayerId(UUID.randomUUID());
        PlayerQuery copy = original.copy();

        copy.filterByPlayerId(UUID.randomUUID());

        assertEquals(1, original.getPlayerIds().size());
        assertEquals(2, copy.getPlayerIds().size());
    }

    @Test
    void clientSettingsUpdateRejectsUnknownValues() {
        assertThrows(
                IllegalArgumentException.class,
                () -> UpdateClientSettingsRequest.builder()
                        .chatMode(PlayerClientSettings.ChatMode.UNKNOWN)
                        .build());
        assertThrows(
                IllegalArgumentException.class,
                () -> UpdateClientSettingsRequest.builder()
                        .mainHand(PlayerClientSettings.MainHand.UNKNOWN)
                        .build());
    }

    @Test
    void clientSettingsParseControllerValues() {
        assertEquals(PlayerClientSettings.ChatMode.COMMANDS_ONLY, PlayerClientSettings.ChatMode.parse("CHAT_MODE_COMMANDS_ONLY"));
        assertEquals(PlayerClientSettings.MainHand.LEFT, PlayerClientSettings.MainHand.parse("left"));
        assertEquals(PlayerClientSettings.ChatMode.UNKNOWN, PlayerClientSettings.ChatMode.parse("something new"));
        assertEquals(PlayerClientSettings.MainHand.UNKNOWN, PlayerClientSettings.MainHand.parse(null));
    }
}
