package app.simplecloud.api.internal.nats;

import io.nats.client.Connection;
import io.nats.client.Message;
import io.nats.client.Nats;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class NetworkScopedInboxIntegrationTest {

    // Run against an isolated broker with NATS_TEST_URL=nats://127.0.0.1:4222.
    @Test
    void requestsUseNetworkInboxesAfterConnectingAndReconnecting() throws Exception {
        String url = System.getenv("NATS_TEST_URL");
        assumeTrue(url != null && !url.isBlank(), "set NATS_TEST_URL to run broker integration tests");

        String subject = "network-1.test." + UUID.randomUUID();
        try (Connection responder = Nats.connect(url)) {
            responder.createDispatcher(message -> responder.publish(
                    message.getReplyTo(), message.getReplyTo().getBytes(StandardCharsets.UTF_8)
            )).subscribe(subject);
            responder.flush(Duration.ofSeconds(5));

            NatsFailoverConnectionManager manager = new NatsFailoverConnectionManager(
                    url, "network-1", "secret", Duration.ofSeconds(30)
            );
            try {
                Connection connection = manager.getConnection();
                awaitConnected(connection);
                assertScopedRequest(connection, subject);
                connection.forceReconnect();
                awaitConnected(connection);
                assertScopedRequest(connection, subject);
            } finally {
                manager.shutdown();
            }
        }
    }

    private static void assertScopedRequest(Connection connection, String subject) throws Exception {
        assertTrue(connection.createInbox().startsWith("network-1._INBOX."));
        Message response = connection.request(subject, new byte[0], Duration.ofSeconds(5));
        assertNotNull(response, "request timed out");
        assertTrue(new String(response.getData(), StandardCharsets.UTF_8).startsWith("network-1._INBOX."));
    }

    private static void awaitConnected(Connection connection) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (connection.getStatus() != Connection.Status.CONNECTED && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertEquals(Connection.Status.CONNECTED, connection.getStatus());
    }
}
