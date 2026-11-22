package app.simplecloud.api.internal.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.simplecloud.api.event.Subscription;
import app.simplecloud.api.event.group.GroupUpdatedEvent;
import app.simplecloud.api.event.server.ServerStateChangedEvent;
import app.simplecloud.api.server.ServerState;
import app.simplecloud.api.support.NatsTest;
import app.simplecloud.api.support.TestNats;
import build.buf.gen.simplecloud.controller.v2.ServerGroupUpdatedEvent;
import io.nats.client.Connection;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@NatsTest
class EventApiImplTest {

    private final String networkId = TestNats.randomNetworkId();
    private Connection apiConnection;
    private Connection controller;
    private EventApiImpl events;

    @BeforeEach
    void connect() throws Exception {
        apiConnection = TestNats.connect();
        controller = TestNats.connect();
        events = new EventApiImpl(apiConnection, networkId, null);
    }

    @AfterEach
    void close() throws InterruptedException {
        apiConnection.close();
        controller.close();
    }

    @Test
    void deliversTypedEvent() throws Exception {
        BlockingQueue<ServerStateChangedEvent> received = new LinkedBlockingQueue<>();
        events.server().onStateChanged(received::add);
        flush();

        publish(
                networkId,
                "server.state-changed",
                build.buf.gen.simplecloud.controller.v2.ServerStateChangedEvent.newBuilder()
                        .setServerId("server-1")
                        .setNewState(build.buf.gen.simplecloud.controller.v2.ServerState.SERVER_STATE_AVAILABLE)
                        .build()
                        .toByteArray());

        ServerStateChangedEvent event = TestNats.next(received);
        assertEquals("server-1", event.getServerId());
        assertEquals(ServerState.AVAILABLE, event.getNewState());
    }

    @Test
    void ignoresEventsOfOtherNetworks() throws Exception {
        BlockingQueue<GroupUpdatedEvent> received = new LinkedBlockingQueue<>();
        events.group().onUpdated(received::add);
        flush();

        publish(TestNats.randomNetworkId(), "group.updated", groupUpdated("other-network"));
        publish(networkId, "group.updated", groupUpdated("own-network"));

        assertEquals("own-network", TestNats.next(received).getServerGroupId());
        assertTrue(received.isEmpty());
    }

    @Test
    void closedSubscriptionReceivesNothing() throws Exception {
        BlockingQueue<GroupUpdatedEvent> closed = new LinkedBlockingQueue<>();
        BlockingQueue<GroupUpdatedEvent> open = new LinkedBlockingQueue<>();
        Subscription subscription = events.group().onUpdated(closed::add);
        events.group().onUpdated(open::add);
        flush();

        subscription.close();
        publish(networkId, "group.updated", groupUpdated("group-1"));

        TestNats.next(open);
        assertTrue(closed.isEmpty());
    }

    @Test
    void brokenPayloadDoesNotEndTheSubscription() throws Exception {
        BlockingQueue<GroupUpdatedEvent> received = new LinkedBlockingQueue<>();
        events.group().onUpdated(received::add);
        flush();

        publish(networkId, "group.updated", new byte[] {(byte) 0xFF});
        publish(networkId, "group.updated", groupUpdated("group-1"));

        assertEquals("group-1", TestNats.next(received).getServerGroupId());
    }

    private void flush() throws Exception {
        apiConnection.flush(TestNats.TIMEOUT);
    }

    private void publish(String network, String event, byte[] data) {
        controller.publish(network + ".event." + event, data);
    }

    private static byte[] groupUpdated(String groupId) {
        return ServerGroupUpdatedEvent.newBuilder()
                .setServerGroupId(groupId)
                .build()
                .toByteArray();
    }
}
