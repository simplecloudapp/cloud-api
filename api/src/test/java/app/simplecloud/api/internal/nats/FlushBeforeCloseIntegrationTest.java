package app.simplecloud.api.internal.nats;

import app.simplecloud.api.CloudApiOptions;
import app.simplecloud.api.internal.CloudApiImpl;
import io.nats.client.Connection;
import io.nats.client.Nats;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class FlushBeforeCloseIntegrationTest {

    private static final int MESSAGE_COUNT = 2_000;
    private static final int MESSAGE_SIZE = 16 * 1024;
    // Long enough that close() is already waiting for the executor, short enough to fit its one-second budget.
    private static final Duration PUBLISH_DELAY = Duration.ofMillis(200);

    // Run against an isolated broker with NATS_TEST_URL=nats://127.0.0.1:4222.
    @Test
    void shutdownDeliversMessagesStillInTheOutgoingQueue() throws Exception {
        String url = natsTestUrl();
        String subject = "network-1.test." + UUID.randomUUID();
        AtomicInteger received = new AtomicInteger();
        try (Connection subscriber = Nats.connect(url)) {
            subscriber.createDispatcher(message -> received.incrementAndGet()).subscribe(subject);
            subscriber.flush(Duration.ofSeconds(5));

            NatsFailoverConnectionManager manager = new NatsFailoverConnectionManager(
                    url, "network-1", "secret", Duration.ofSeconds(30)
            );
            Connection connection = manager.getConnection();
            awaitConnected(connection);
            byte[] payload = new byte[MESSAGE_SIZE];
            for (int i = 0; i < MESSAGE_COUNT; i++) {
                connection.publish(subject, payload);
            }
            manager.shutdown();

            awaitCount(received, MESSAGE_COUNT);
            assertEquals(MESSAGE_COUNT, received.get());
        }
    }

    @Test
    void closeDeliversAMessageFromATaskStillRunningOnTheExecutor() throws Exception {
        String url = natsTestUrl();
        String subject = "network-1.test." + UUID.randomUUID();
        CountDownLatch received = new CountDownLatch(1);
        try (Connection subscriber = Nats.connect(url)) {
            subscriber.createDispatcher(message -> received.countDown()).subscribe(subject);
            subscriber.flush(Duration.ofSeconds(5));

            CloudApiImpl cloudApi = new CloudApiImpl(CloudApiOptions.builder()
                    .natsUrl(url)
                    .networkId("network-1")
                    .networkSecret("secret")
                    .disableCache()
                    .build());
            Connection connection = cloudApi.getNatsConnection();
            awaitConnected(connection);
            CountDownLatch taskStarted = new CountDownLatch(1);
            cloudApi.getExecutor().execute(() -> {
                taskStarted.countDown();
                try {
                    Thread.sleep(PUBLISH_DELAY);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                connection.publish(subject, new byte[]{1});
            });
            assertTrue(taskStarted.await(5, TimeUnit.SECONDS));
            cloudApi.close();

            assertTrue(received.await(5, TimeUnit.SECONDS), "message from the running task was lost");
        }
    }

    private static String natsTestUrl() {
        String url = System.getenv("NATS_TEST_URL");
        assumeTrue(url != null && !url.isBlank(), "set NATS_TEST_URL to run broker integration tests");
        return url;
    }

    private static void awaitCount(AtomicInteger counter, int expected) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (counter.get() < expected && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
    }

    private static void awaitConnected(Connection connection) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (connection.getStatus() != Connection.Status.CONNECTED && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertEquals(Connection.Status.CONNECTED, connection.getStatus());
    }
}
