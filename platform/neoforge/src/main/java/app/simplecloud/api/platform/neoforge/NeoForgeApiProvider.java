package app.simplecloud.api.platform.neoforge;

import app.simplecloud.api.CloudApi;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(value = NeoForgeApiProvider.MOD_ID, dist = Dist.DEDICATED_SERVER)
public final class NeoForgeApiProvider {

    public static final String MOD_ID = "simplecloud_api";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private CloudApi cloudApi;

    public NeoForgeApiProvider() {
        NeoForge.EVENT_BUS.addListener(this::onServerStarted);
        NeoForge.EVENT_BUS.addListener(this::onServerStopping);
    }

    private void onServerStarted(ServerStartedEvent event) {
        this.cloudApi = CloudApi.create();
        LOGGER.info("SimpleCloud v3 API provider initialized");
    }

    private void onServerStopping(ServerStoppingEvent event) {
        CloudApi api = this.cloudApi;
        if (api != null) {
            api.close();
            this.cloudApi = null;
        }

        LOGGER.info("SimpleCloud v3 API provider uninitialized");
    }
}
