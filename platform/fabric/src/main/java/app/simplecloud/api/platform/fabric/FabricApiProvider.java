package app.simplecloud.api.platform.fabric;

import app.simplecloud.api.internal.integration.heap.StandaloneHeapUsageReporter;
import net.fabricmc.api.DedicatedServerModInitializer;

/**
 * Reports the heap usage of this server until the JVM exits. Nothing stops the reporter, because
 * Fabric Loader has no server stop event without Fabric API, which this mod does not require.
 */
public final class FabricApiProvider implements DedicatedServerModInitializer {

    @Override
    public void onInitializeServer() {
        StandaloneHeapUsageReporter heapUsageReporter = new StandaloneHeapUsageReporter();
        heapUsageReporter.start();
    }
}
