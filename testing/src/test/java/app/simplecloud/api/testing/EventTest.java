package app.simplecloud.api.testing;

import app.simplecloud.api.event.Subscription;
import app.simplecloud.api.event.server.ServerDeletedEvent;
import app.simplecloud.api.event.server.ServerStartedEvent;
import app.simplecloud.api.testing.event.TestEvents;
import app.simplecloud.api.testing.server.TestServer;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@CloudTest
class EventTest {

    @Test
    void apiSubscriptionsReceiveSimulatedEvents(TestCloud cloud) {
        List<String> started = new ArrayList<>();
        Subscription subscription = cloud.api().event().server().onStarted(event -> started.add(event.getServerId()));

        TestServer server = cloud.createGroup("lobby").startServer();
        subscription.unsubscribe();
        cloud.createGroup("bedwars").startServer();

        assertEquals(List.of(server.getServerId()), started);
    }

    @Test
    void publishedEventsAreStampedButDoNotChangeState(TestCloud cloud) {
        TestServer server = cloud.createGroup("lobby").startServer();
        var deleted = cloud.collect(ServerDeletedEvent.class);

        cloud.publish(TestEvents.serverDeleted(server));

        ServerDeletedEvent event = deleted.single();
        assertEquals(cloud.networkId(), event.getNetworkId());
        assertEquals(cloud.now().toString(), event.getTimestamp());
        assertTrue(server.isRunning());
    }

    @Test
    void collectorFiltersEvents(TestCloud cloud) {
        var lobbyStarts = cloud.collect(ServerStartedEvent.class,
                event -> event.getServer().getGroup().getName().equals("lobby"));

        cloud.createGroup("lobby").startServer();
        cloud.createGroup("bedwars").startServer();

        assertEquals(1, lobbyStarts.count());
    }

    @Test
    void asyncEventsCanBeAwaited(TestCloud cloud) {
        cloud.asyncEvents(true);
        var started = cloud.collect(ServerStartedEvent.class);

        cloud.createGroup("lobby").startServer();

        assertEquals("lobby", started.awaitFirst(Duration.ofSeconds(2)).getServer().getGroup().getName());
    }

    @Test
    void failingHandlerSurfacesInSyncMode(TestCloud cloud) {
        cloud.api().event().server().onStarted(event -> {
            throw new IllegalStateException("handler failed");
        });

        assertThrows(IllegalStateException.class, () -> cloud.createGroup("lobby").startServer());
    }

    @Test
    void timestampsFollowTheTestClock(TestCloud cloud) {
        var started = cloud.collect(ServerStartedEvent.class);
        cloud.advanceTime(Duration.ofMinutes(5));

        cloud.createGroup("lobby").startServer();

        assertEquals(cloud.now().toString(), started.single().getTimestamp());
    }
}
