package app.simplecloud.api.internal.nats;

import io.nats.client.Connection;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class DaemonNatsThreadsIntegrationTest {

    // Run against an isolated broker with NATS_TEST_URL=nats://127.0.0.1:4222.
    @Test
    void openConnectionCannotKeepTheJvmAlive() throws Exception {
        String url = System.getenv("NATS_TEST_URL");
        assumeTrue(url != null && !url.isBlank(), "set NATS_TEST_URL to run broker integration tests");
        Set<Thread> threadsBeforeConnecting = Thread.getAllStackTraces().keySet();

        NatsFailoverConnectionManager manager = NatsFailoverConnectionManager.createOnDaemonThreads(
                url, "network-1", "secret", Duration.ofSeconds(30)
        );
        try {
            Connection connection = manager.getConnection();
            awaitConnected(connection);
            connection.publish("network-1.test." + UUID.randomUUID(), new byte[0]);
            connection.flush(Duration.ofSeconds(5));

            assertEquals(List.of(), findNewNonDaemonThreadNames(threadsBeforeConnecting));
        } finally {
            manager.shutdown();
        }
    }

    private static List<String> findNewNonDaemonThreadNames(Set<Thread> threadsBeforeConnecting) {
        List<String> nonDaemonThreadNames = new ArrayList<>();
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            if (!thread.isDaemon() && !threadsBeforeConnecting.contains(thread)) {
                nonDaemonThreadNames.add(thread.getName());
            }
        }
        return nonDaemonThreadNames;
    }

    private static void awaitConnected(Connection connection) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (connection.getStatus() != Connection.Status.CONNECTED && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertEquals(Connection.Status.CONNECTED, connection.getStatus());
    }
}
