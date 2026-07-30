package app.simplecloud.api.platform.fabric;

import app.simplecloud.api.CloudApi;
import app.simplecloud.api.platform.shared.PlayerSynchronizer;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicLong;

public final class FabricApiProvider implements DedicatedServerModInitializer {

    public static final String MOD_ID = "simplecloud_api";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private final AtomicLong onlinePlayerCount = new AtomicLong();

    private MinecraftServer server;
    private CloudApi cloudApi;
    private PlayerSynchronizer playerSynchronizer;

    @Override
    public void onInitializeServer() {
        ServerLifecycleEvents.SERVER_STARTED.register(this::onServerStarted);
        ServerLifecycleEvents.SERVER_STOPPING.register(this::onServerStopping);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> refreshPlayerCount(server));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> refreshPlayerCount(server));
    }

    private void onServerStarted(MinecraftServer server) {
        this.server = server;
        this.onlinePlayerCount.set(server.getPlayerList().getPlayerCount());
        this.cloudApi = CloudApi.create();
        this.playerSynchronizer = new PlayerSynchronizer(cloudApi, onlinePlayerCount::get);
        this.playerSynchronizer.start();
        this.playerSynchronizer.updatePlayerCount();
        LOGGER.info("SimpleCloud v3 API provider initialized");
    }

    private void onServerStopping(MinecraftServer server) {
        PlayerSynchronizer synchronizer = this.playerSynchronizer;
        if (synchronizer != null) {
            synchronizer.stop();
            this.playerSynchronizer = null;
        }

        CloudApi api = this.cloudApi;
        if (api != null) {
            api.close();
            this.cloudApi = null;
        }

        this.server = null;
        LOGGER.info("SimpleCloud v3 API provider uninitialized");
    }

    private void refreshPlayerCount(MinecraftServer eventServer) {
        eventServer.execute(() -> {
            if (eventServer != server) {
                return;
            }

            onlinePlayerCount.set(eventServer.getPlayerList().getPlayerCount());
            PlayerSynchronizer synchronizer = playerSynchronizer;
            if (synchronizer != null) {
                synchronizer.updatePlayerCount();
            }
        });
    }
}
