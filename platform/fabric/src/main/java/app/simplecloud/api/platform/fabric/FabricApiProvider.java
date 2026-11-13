package app.simplecloud.api.platform.fabric;

import app.simplecloud.api.CloudApi;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FabricApiProvider implements DedicatedServerModInitializer {

    public static final String MOD_ID = "simplecloud_api";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private CloudApi cloudApi;

    @Override
    public void onInitializeServer() {
        ServerLifecycleEvents.SERVER_STARTED.register(this::onServerStarted);
        ServerLifecycleEvents.SERVER_STOPPING.register(this::onServerStopping);
    }

    private void onServerStarted(MinecraftServer server) {
        this.cloudApi = CloudApi.create();
        LOGGER.info("SimpleCloud v3 API provider initialized");
    }

    private void onServerStopping(MinecraftServer server) {
        CloudApi api = this.cloudApi;
        if (api != null) {
            api.close();
            this.cloudApi = null;
        }

        LOGGER.info("SimpleCloud v3 API provider uninitialized");
    }
}
