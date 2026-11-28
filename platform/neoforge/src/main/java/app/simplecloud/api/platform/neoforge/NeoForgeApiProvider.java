package app.simplecloud.api.platform.neoforge;

import app.simplecloud.api.internal.integration.heap.StandaloneHeapUsageReporter;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

/**
 * Reports the heap usage of this server.
 */
@Mod(value = "simplecloud_api", dist = Dist.DEDICATED_SERVER)
public final class NeoForgeApiProvider {

    private final StandaloneHeapUsageReporter heapUsageReporter = new StandaloneHeapUsageReporter();

    public NeoForgeApiProvider() {
        NeoForge.EVENT_BUS.addListener(this::onServerStarted);
        NeoForge.EVENT_BUS.addListener(this::onServerStopping);
    }

    private void onServerStarted(ServerStartedEvent event) {
        heapUsageReporter.start();
    }

    private void onServerStopping(ServerStoppingEvent event) {
        heapUsageReporter.stop();
    }
}
