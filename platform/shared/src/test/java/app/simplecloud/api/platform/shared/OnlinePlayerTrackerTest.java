package app.simplecloud.api.platform.shared;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OnlinePlayerTrackerTest {

    @Test
    void countsPlayersFromResetJoinAndQuit() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        OnlinePlayerTracker tracker = new OnlinePlayerTracker();

        tracker.reset(List.of(first));
        assertEquals(1, tracker.onlinePlayerCount());

        tracker.add(second);
        tracker.add(second);
        assertEquals(2, tracker.onlinePlayerCount());

        tracker.remove(first);
        tracker.remove(first);
        assertEquals(1, tracker.onlinePlayerCount());

        tracker.reset(List.of());
        assertEquals(0, tracker.onlinePlayerCount());
    }
}
