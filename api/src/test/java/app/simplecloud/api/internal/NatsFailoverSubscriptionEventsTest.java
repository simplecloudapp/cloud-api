package app.simplecloud.api.internal;

import app.simplecloud.api.internal.nats.NatsFailoverConnectionManager;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * How {@link NatsFailoverConnectionManager} turns the events of its jnats connection into calls of its
 * subscription listeners, on which a view's reload after a reconnect rests.
 */
class NatsFailoverSubscriptionEventsTest {

    private static final String SUBJECT_PREFIX = "network-1.test.";
    /**
     * So many that jnats takes a while to send them again after it reconnected. A listener told at RECONNECTED,
     * before jnats sends them, then confirms a flush before the last of them arrives.
     */
    private static final int SUBSCRIPTION_COUNT = 2_000;
    /**
     * jnats waits up to 2 s before it connects again, and the rest is room for a slow build machine.
     */
    private static final Duration EVENT_WAIT_LIMIT = Duration.ofSeconds(10);
    /**
     * Longer than the test, so the manager's own failover never replaces the connection.
     */
    private static final Duration FAILOVER_AFTER = Duration.ofMinutes(5);

    private enum Event {
        CONNECTION_LOST,
        SUBSCRIPTIONS_SENT
    }

    private FakeNatsServer nats;
    private NatsFailoverConnectionManager manager;

    @BeforeEach
    void start() throws IOException {
        nats = new FakeNatsServer();
        manager = new NatsFailoverConnectionManager(nats.url(), "network-1", "secret", FAILOVER_AFTER);
    }

    @AfterEach
    void stop() throws IOException {
        manager.shutdown();
        nats.close();
    }

    @Test
    void aListenerAddedWhileConnectedHearsSubscriptionsSentAtOnce() throws Exception {
        RecordingListener first = new RecordingListener();
        manager.addSubscriptionListener(first);
        first.awaitNext(Event.SUBSCRIPTIONS_SENT);
        RecordingListener addedWhileConnected = new RecordingListener();

        manager.addSubscriptionListener(addedWhileConnected);
        Event heardDuringAdd = addedWhileConnected.firstHeardSoFar();

        assertEquals(Event.SUBSCRIPTIONS_SENT, heardDuringAdd, "the listener hears it before the add returns");
    }

    @Test
    void aDroppedConnectionIsLostAndThenSentAgainOnceJnatsResubscribed() throws Exception {
        RecordingListener listener = new RecordingListener();
        manager.addSubscriptionListener(listener);
        listener.awaitNext(Event.SUBSCRIPTIONS_SENT);
        Connection connection = manager.getConnection();
        subscribeAll(connection);

        nats.dropClient();
        Event afterDrop = listener.next();
        Event afterReconnect = listener.next();
        connection.flush(EVENT_WAIT_LIMIT);

        assertEquals(Event.CONNECTION_LOST, afterDrop);
        assertEquals(Event.SUBSCRIPTIONS_SENT, afterReconnect);
        assertEquals(SUBSCRIPTION_COUNT, nats.subscriptionCount(), "every subscription is sent on the new connection");
    }

    private static void subscribeAll(Connection connection) {
        Dispatcher dispatcher = connection.createDispatcher(message -> {
        });
        for (int number = 0; number < SUBSCRIPTION_COUNT; number++) {
            dispatcher.subscribe(SUBJECT_PREFIX + number);
        }
    }

    private static final class RecordingListener implements NatsFailoverConnectionManager.SubscriptionListener {

        private final BlockingQueue<Event> heard = new LinkedBlockingQueue<>();

        @Override
        public void connectionLost() {
            heard.add(Event.CONNECTION_LOST);
        }

        @Override
        public void subscriptionsSent() {
            heard.add(Event.SUBSCRIPTIONS_SENT);
        }

        Event next() throws InterruptedException, TimeoutException {
            Event event = heard.poll(EVENT_WAIT_LIMIT.toMillis(), TimeUnit.MILLISECONDS);
            if (event == null) {
                throw new TimeoutException("the listener heard nothing within " + EVENT_WAIT_LIMIT);
            }
            return event;
        }

        void awaitNext(Event expected) throws InterruptedException, TimeoutException {
            Event event = next();
            assertEquals(expected, event);
        }

        /**
         * Returns null when it heard nothing yet.
         */
        Event firstHeardSoFar() {
            return heard.peek();
        }
    }
}
