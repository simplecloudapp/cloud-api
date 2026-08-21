package app.simplecloud.api.platform.neoforge;

import app.simplecloud.api.CloudApi;
import app.simplecloud.api.internal.CloudApiImpl;
import app.simplecloud.api.internal.integration.presence.PresenceResponder;
import app.simplecloud.api.platform.shared.PlayerSynchronizer;
import app.simplecloud.api.presence.PresencePlayer;
import app.simplecloud.api.presence.PresencePlayerProvider;
import app.simplecloud.api.runtime.SimpleCloudRuntime;
import net.minecraft.SharedConstants;
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

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Mod(value = NeoForgeApiProvider.MOD_ID, dist = Dist.DEDICATED_SERVER)
public final class NeoForgeApiProvider implements PresencePlayerProvider {

    public static final String MOD_ID = "simplecloud_api";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private final AtomicLong onlinePlayerCount = new AtomicLong();

    private MinecraftServer server;
    private CloudApiImpl cloudApi;
    private PlayerSynchronizer playerSynchronizer;
    private PresenceResponder presenceResponder;

    public NeoForgeApiProvider() {
        NeoForge.EVENT_BUS.addListener(this::onServerStarted);
        NeoForge.EVENT_BUS.addListener(this::onServerStopping);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLoggedOut);
    }

    private void onServerStarted(ServerStartedEvent event) {
        this.server = event.getServer();
        this.onlinePlayerCount.set(server.getPlayerList().getPlayerCount());
        this.cloudApi = (CloudApiImpl) CloudApi.create();
        this.playerSynchronizer = new PlayerSynchronizer(cloudApi, onlinePlayerCount::get);
        this.presenceResponder = new PresenceResponder(
                cloudApi.getNatsConnection(),
                cloudApi.getNetworkId(),
                SimpleCloudRuntime.serverId(),
                this
        );
        this.playerSynchronizer.start();
        this.presenceResponder.start();
        this.playerSynchronizer.updatePlayerCount();
        LOGGER.info("SimpleCloud v3 API provider initialized");
    }

    private void onServerStopping(ServerStoppingEvent event) {
        PresenceResponder responder = this.presenceResponder;
        if (responder != null) {
            responder.stop();
            this.presenceResponder = null;
        }

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

    @Override
    public List<PresencePlayer> getPresencePlayers() {
        MinecraftServer currentServer = this.server;
        if (currentServer == null) {
            return List.of();
        }

        String serverName = SimpleCloudRuntime.serverName();
        return currentServer.getPlayerList().getPlayers().stream()
                .map(player -> toPresencePlayer(currentServer, player, serverName))
                .toList();
    }

    private PresencePlayer toPresencePlayer(
            MinecraftServer currentServer,
            ServerPlayer player,
            String serverName
    ) {
        String name = player.getName().getString();
        return new PresencePlayer(
                player.getStringUUID(),
                name,
                player.getDisplayName().getString(),
                serverName,
                "",
                0L,
                player.clientInformation().language(),
                SharedConstants.getProtocolVersion(),
                currentServer.usesAuthentication(),
                ""
        );
    }
}
