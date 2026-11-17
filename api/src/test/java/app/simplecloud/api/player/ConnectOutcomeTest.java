package app.simplecloud.api.player;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectOutcomeTest {

    @ParameterizedTest
    @EnumSource(value = ConnectStatus.class, names = {"SUCCESS", "ALREADY_CONNECTED"})
    void isConnected_withPlayerOnTheServer_isTrue(ConnectStatus status) {
        ConnectOutcome outcome = ConnectOutcome.of(status, "lobby-1");

        assertTrue(outcome.isConnected());
    }

    @ParameterizedTest
    @EnumSource(value = ConnectStatus.class, names = {"SUCCESS", "ALREADY_CONNECTED"}, mode = EnumSource.Mode.EXCLUDE)
    void isConnected_withPlayerNotMoved_isFalse(ConnectStatus status) {
        ConnectOutcome outcome = ConnectOutcome.of(status, null);

        assertFalse(outcome.isConnected());
    }
}
