package app.simplecloud.api.platform.neoforge;

import app.simplecloud.api.CloudApi;
import app.simplecloud.api.platform.shared.PlayerSynchronizer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicLong;

@Mod(value = NeoForgeApiProvider.MOD_ID, dist = Dist.DEDICATED_SERVER)
public final class NeoForgeApiProvider {

    public static final String MOD_ID = "simplecloud_api";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private final AtomicLong onlinePlayerCount = new AtomicLong();

    private MinecraftServer server;
    private CloudApi cloudApi;
    private PlayerSynchronizer playerSynchronizer;

    public NeoForgeApiProvider() {
        NeoForge.EVENT_BUS.addListener(this::onServerStarted);
        NeoForge.EVENT_BUS.addListener(this::onServerStopping);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLoggedOut);
    }

    private void onServerStarted(ServerStartedEvent event) {
        this.server = event.getServer();
        this.onlinePlayerCount.set(server.getPlayerList().getPlayerCount());
        this.cloudApi = CloudApi.create();
        this.playerSynchronizer = new PlayerSynchronizer(cloudApi, onlinePlayerCount::get);
        this.playerSynchronizer.start();
        this.playerSynchronizer.updatePlayerCount();
        LOGGER.info("SimpleCloud v3 API provider initialized");
    }

    private void onServerStopping(ServerStoppingEvent event) {
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

    private void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        refreshPlayerCount(((ServerPlayer) event.getEntity()).level().getServer());
    }

    private void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        refreshPlayerCount(((ServerPlayer) event.getEntity()).level().getServer());
    }

    private void refreshPlayerCount(MinecraftServer eventServer) {
        if (eventServer == null) {
            return;
        }

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
