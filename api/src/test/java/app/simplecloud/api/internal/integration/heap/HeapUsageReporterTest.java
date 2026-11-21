package app.simplecloud.api.internal.integration.heap;

import app.simplecloud.api.internal.nats.FakeNatsConnection;
import io.nats.client.Connection;
import io.nats.client.Message;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(5)
class HeapUsageReporterTest {

    private static final String NETWORK_ID = "test-network";
    private static final String SERVER_ID = "11111111-1111-1111-1111-111111111111";
    private static final int TICK_AT_ONE_MINUTE = 12;
    private static final int TICK_AT_NINETY_SECONDS = 18;
    private static final int TICK_AT_TWO_MINUTES = 24;

    @Test
    void reportIsDueOnEveryTickOfTheFirstMinute() {
        for (int tick = 0; tick <= TICK_AT_ONE_MINUTE; tick++) {
            assertTrue(HeapUsageReporter.isReportDue(tick), "tick " + tick);
        }
    }

    @Test
    void reportIsDueEveryThirtySecondsAfterTheFirstMinute() {
        for (int tick = TICK_AT_ONE_MINUTE + 1; tick < TICK_AT_NINETY_SECONDS; tick++) {
            assertFalse(HeapUsageReporter.isReportDue(tick), "tick " + tick);
        }
        assertTrue(HeapUsageReporter.isReportDue(TICK_AT_NINETY_SECONDS));
        for (int tick = TICK_AT_NINETY_SECONDS + 1; tick < TICK_AT_TWO_MINUTES; tick++) {
            assertFalse(HeapUsageReporter.isReportDue(tick), "tick " + tick);
        }
        assertTrue(HeapUsageReporter.isReportDue(TICK_AT_TWO_MINUTES));
    }

    @Test
    void publishesUsedHeapAsDecimalNumberOnTheServerSubject() {
        FakeNatsConnection nats = FakeNatsConnection.withoutRequests();
        HeapUsageReporter reporter = new HeapUsageReporter(nats.connection(), NETWORK_ID, SERVER_ID);

        reporter.reportIfDue();

        Message report = nats.published().getFirst();
        assertEquals(NETWORK_ID + ".server." + SERVER_ID + ".heap-usage", report.getSubject());
        String heapUsedBytesText = new String(report.getData(), StandardCharsets.US_ASCII);
        assertTrue(Long.parseLong(heapUsedBytesText) > 0);
    }

    @Test
    void startPublishesNothingWithoutServerId() throws InterruptedException {
        FakeNatsConnection nats = FakeNatsConnection.withoutRequests();
        HeapUsageReporter reporter = new HeapUsageReporter(nats.connection(), NETWORK_ID, "");

        reporter.start();
        Thread.sleep(50);
        reporter.stop();

        assertTrue(nats.published().isEmpty());
    }

    @Test
    void failedPublishDoesNotEndTheRepeatingTask() {
        Connection failingConnection = newConnectionThrowing(new IllegalStateException("connection closed"));
        HeapUsageReporter reporter = new HeapUsageReporter(failingConnection, NETWORK_ID, SERVER_ID);

        assertDoesNotThrow(reporter::reportIfDue);
    }

    @Test
    void outOfMemoryErrorDoesNotEndTheRepeatingTask() {
        Connection failingConnection = newConnectionThrowing(new OutOfMemoryError("Java heap space"));
        HeapUsageReporter reporter = new HeapUsageReporter(failingConnection, NETWORK_ID, SERVER_ID);

        assertDoesNotThrow(reporter::reportIfDue);
    }

    private static Connection newConnectionThrowing(Throwable publishFailure) {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    throw publishFailure;
                }
        );
    }
}
