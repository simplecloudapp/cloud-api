package app.simplecloud.api.internal.integration.heap;

import app.simplecloud.api.CloudApiOptions;
import app.simplecloud.api.internal.nats.NatsFailoverConnectionManager;
import app.simplecloud.api.runtime.SimpleCloudRuntime;

/**
 * Reports heap usage on platforms that otherwise need no CloudApi. It opens only a NATS connection,
 * and none outside SimpleCloud, where there is no server to report for. All its threads are daemon
 * threads, so it cannot keep a server from exiting, also on a platform that never calls {@link #stop()}.
 */
public final class StandaloneHeapUsageReporter {

    private NatsFailoverConnectionManager natsConnectionManager;
    private HeapUsageReporter heapUsageReporter;

    public void start() {
        String serverId = SimpleCloudRuntime.serverId();
        if (serverId.isBlank()) {
            return;
        }
        CloudApiOptions options = CloudApiOptions.DEFAULT;
        natsConnectionManager = NatsFailoverConnectionManager.createOnDaemonThreads(
                options.getNatsUrl(),
                options.getNetworkId(),
                options.getNetworkSecret(),
                options.getNatsFailoverReconnectAfter()
        );
        heapUsageReporter = new HeapUsageReporter(
                natsConnectionManager.getConnection(),
                options.getNetworkId(),
                serverId
        );
        heapUsageReporter.start();
    }

    public void stop() {
        if (heapUsageReporter == null) {
            return;
        }
        heapUsageReporter.stop();
        natsConnectionManager.shutdown();
    }
}
