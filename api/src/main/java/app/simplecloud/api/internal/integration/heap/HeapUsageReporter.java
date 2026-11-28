package app.simplecloud.api.internal.integration.heap;

import app.simplecloud.api.internal.CloudApiImpl;
import app.simplecloud.api.runtime.SimpleCloudRuntime;
import io.nats.client.Connection;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Publishes the used Java heap of this server to the controller, as a decimal number of bytes:
 * every 5 seconds in the first minute, then every 30 seconds.
 */
public final class HeapUsageReporter {

    private static final Logger LOGGER = Logger.getLogger(HeapUsageReporter.class.getName());
    private static final Duration TICK_INTERVAL = Duration.ofSeconds(5);
    private static final Duration STARTUP_PHASE_DURATION = Duration.ofMinutes(1);
    private static final Duration REGULAR_REPORT_INTERVAL = Duration.ofSeconds(30);
    private static final long LAST_STARTUP_TICK = STARTUP_PHASE_DURATION.dividedBy(TICK_INTERVAL);
    private static final long TICKS_PER_REGULAR_REPORT = REGULAR_REPORT_INTERVAL.dividedBy(TICK_INTERVAL);

    private final Connection natsConnection;
    private final String serverId;
    private final String subject;
    private final ScheduledExecutorService tickScheduler =
            Executors.newSingleThreadScheduledExecutor(HeapUsageReporter::newTickThread);

    private long nextTick;

    public HeapUsageReporter(CloudApiImpl cloudApi) {
        this(cloudApi.getNatsConnection(), cloudApi.getNetworkId(), SimpleCloudRuntime.serverId());
    }

    HeapUsageReporter(Connection natsConnection, String networkId, String serverId) {
        this.natsConnection = natsConnection;
        this.serverId = serverId;
        this.subject = networkId + ".server." + serverId + ".heap-usage";
    }

    public void start() {
        if (serverId.isBlank()) {
            LOGGER.warning("Heap usage reporter not started because SIMPLECLOUD_UNIQUE_ID is missing");
            return;
        }
        tickScheduler.scheduleWithFixedDelay(this::reportIfDue, 0, TICK_INTERVAL.toMillis(), TimeUnit.MILLISECONDS);
    }

    public void stop() {
        tickScheduler.shutdownNow();
    }

    static boolean isReportDue(long tick) {
        return tick <= LAST_STARTUP_TICK || tick % TICKS_PER_REGULAR_REPORT == 0;
    }

    /**
     * Catches every failure because a repeating task that throws never runs again, and the reports matter most
     * when the heap is full. It logs nothing because logging can fail again on a full heap.
     */
    void reportIfDue() {
        long tick = nextTick++;
        if (!isReportDue(tick)) {
            return;
        }
        try {
            publishHeapUsage();
        } catch (Throwable ignored) {
        }
    }

    /**
     * Skips a report while disconnected, because jnats would queue it and send every old value at once
     * after the reconnect.
     */
    private void publishHeapUsage() {
        if (natsConnection.getStatus() != Connection.Status.CONNECTED) {
            return;
        }
        MemoryUsage heapMemoryUsage = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        String heapUsedBytesText = Long.toString(heapMemoryUsage.getUsed());
        natsConnection.publish(subject, heapUsedBytesText.getBytes(StandardCharsets.US_ASCII));
    }

    private static Thread newTickThread(Runnable tickTask) {
        Thread tickThread = new Thread(tickTask, "simplecloud-heap-usage-reporter");
        tickThread.setDaemon(true);
        return tickThread;
    }
}
