package app.simplecloud.api.internal.nats;

import static org.junit.jupiter.api.Assertions.assertTrue;

import app.simplecloud.api.CloudApiOptions;
import app.simplecloud.api.internal.CloudApiImpl;
import app.simplecloud.api.support.Await;
import app.simplecloud.api.support.NatsTest;
import app.simplecloud.api.support.TestNats;
import io.nats.client.Connection;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

@NatsTest
class FlushBeforeCloseIntegrationTest {

    private static final int MESSAGE_COUNT = 2_000;
    private static final int MESSAGE_SIZE = 16 * 1024;
    // Long enough that close() is already waiting for the executor, short enough to fit its one-second budget.
    private static final Duration PUBLISH_DELAY = Duration.ofMillis(200);

    @Test
    void shutdownDeliversMessagesStillInTheOutgoingQueue() throws Exception {
        String networkId = TestNats.randomNetworkId();
        String subject = networkId + ".test.flush";
        AtomicInteger received = new AtomicInteger();
        try (Connection subscriber = TestNats.connect()) {
            subscriber.createDispatcher(message -> received.incrementAndGet()).subscribe(subject);
            subscriber.flush(TestNats.TIMEOUT);

            NatsFailoverConnectionManager manager =
                    new NatsFailoverConnectionManager(TestNats.url(), networkId, "secret", Duration.ofSeconds(30));
            Connection connection = manager.getConnection();
            awaitConnected(connection);
            byte[] payload = new byte[MESSAGE_SIZE];
            for (int i = 0; i < MESSAGE_COUNT; i++) {
                connection.publish(subject, payload);
            }
            manager.shutdown();

            Await.until(() -> received.get() == MESSAGE_COUNT, "all queued messages arrive");
        }
    }

    @Test
    void closeDeliversAMessageFromATaskStillRunningOnTheExecutor() throws Exception {
        String networkId = TestNats.randomNetworkId();
        String subject = networkId + ".test.flush";
        CountDownLatch received = new CountDownLatch(1);
        try (Connection subscriber = TestNats.connect()) {
            subscriber.createDispatcher(message -> received.countDown()).subscribe(subject);
            subscriber.flush(TestNats.TIMEOUT);

            CloudApiImpl cloudApi = new CloudApiImpl(CloudApiOptions.builder()
                    .natsUrl(TestNats.url())
                    .networkId(networkId)
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
                connection.publish(subject, new byte[] {1});
            });
            assertTrue(taskStarted.await(5, TimeUnit.SECONDS));
            cloudApi.close();

            assertTrue(received.await(5, TimeUnit.SECONDS), "message from the running task was lost");
        }
    }

    private static void awaitConnected(Connection connection) throws InterruptedException {
        Await.until(() -> connection.getStatus() == Connection.Status.CONNECTED, "NATS is connected");
    }
}
