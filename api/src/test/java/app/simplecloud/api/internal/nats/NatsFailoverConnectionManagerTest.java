package app.simplecloud.api.internal.nats;

import app.simplecloud.api.CloudApiException;
import app.simplecloud.api.support.Await;
import app.simplecloud.api.support.NatsTest;
import app.simplecloud.api.support.TestNats;
import io.nats.client.Connection;
import io.nats.client.Message;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NatsFailoverConnectionManagerTest {

    private NatsFailoverConnectionManager manager;

    @AfterEach
    void shutdown() {
        if (manager != null) {
            manager.shutdown();
        }
    }

    @Test
    void callsFailAsUnavailableWhileNotConnected() {
        manager = new NatsFailoverConnectionManager("nats://127.0.0.1:1", "network-1", "secret", Duration.ofSeconds(30));

        CloudApiException exception = assertThrows(CloudApiException.class,
                () -> manager.getConnection().publish("network-1.test", new byte[0]));

        assertEquals(CloudApiException.Reason.UNAVAILABLE, exception.getReason());
        assertEquals(Connection.Status.DISCONNECTED, manager.getConnection().getStatus());
    }

    @Test
    @NatsTest
    void requestsUseNetworkScopedInboxesAlsoAfterReconnect() throws Exception {
        String networkId = TestNats.randomNetworkId();
        String subject = networkId + ".test.echo";
        try (Connection responder = TestNats.connect()) {
            responder.createDispatcher(message -> responder.publish(
                    message.getReplyTo(), message.getReplyTo().getBytes(StandardCharsets.UTF_8)
            )).subscribe(subject);
            responder.flush(TestNats.TIMEOUT);

            manager = new NatsFailoverConnectionManager(TestNats.url(), networkId, "secret", Duration.ofSeconds(30));
            Connection connection = manager.getConnection();
            awaitConnected(connection);
            assertRepliesGoToNetworkInbox(connection, subject, networkId);

            connection.forceReconnect();
            awaitConnected(connection);
            assertRepliesGoToNetworkInbox(connection, subject, networkId);
        }
    }

    @Test
    @NatsTest
    void subscriptionMadeBeforeConnectingReceivesMessages() throws Exception {
        String networkId = TestNats.randomNetworkId();
        String subject = networkId + ".test.events";
        BlockingQueue<Message> received = new LinkedBlockingQueue<>();

        manager = new NatsFailoverConnectionManager(TestNats.url(), networkId, "secret", Duration.ofSeconds(30));
        manager.getConnection().createDispatcher(null).subscribe(subject, received::add);

        try (Connection publisher = TestNats.connect()) {
            Await.until(() -> {
                publisher.publish(subject, new byte[0]);
                return !received.isEmpty();
            }, "the early subscription receives messages");
        }
    }

    private static void assertRepliesGoToNetworkInbox(Connection connection, String subject, String networkId) throws Exception {
        assertTrue(connection.createInbox().startsWith(networkId + "._INBOX."));
        Message response = TestNats.request(connection, subject, new byte[0]);
        assertTrue(new String(response.getData(), StandardCharsets.UTF_8).startsWith(networkId + "._INBOX."));
    }

    private static void awaitConnected(Connection connection) throws InterruptedException {
        Await.until(() -> connection.getStatus() == Connection.Status.CONNECTED, "NATS is connected");
    }
}
