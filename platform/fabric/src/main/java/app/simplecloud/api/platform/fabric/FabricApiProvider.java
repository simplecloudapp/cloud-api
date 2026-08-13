package app.simplecloud.api.platform.fabric;

import app.simplecloud.api.CloudApi;
import app.simplecloud.api.internal.CloudApiImpl;
import app.simplecloud.api.internal.integration.presence.ProxyPresenceResponder;
import app.simplecloud.api.platform.shared.PlayerSynchronizer;
import app.simplecloud.api.presence.ProxyPresencePlayer;
import app.simplecloud.api.presence.ProxyPresencePlayerProvider;
import app.simplecloud.api.runtime.SimpleCloudRuntime;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.SharedConstants;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public final class FabricApiProvider implements DedicatedServerModInitializer, ProxyPresencePlayerProvider {

    public static final String MOD_ID = "simplecloud_api";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private final AtomicLong onlinePlayerCount = new AtomicLong();

    private MinecraftServer server;
    private CloudApiImpl cloudApi;
    private PlayerSynchronizer playerSynchronizer;
    private ProxyPresenceResponder presenceResponder;

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
        this.cloudApi = (CloudApiImpl) CloudApi.create();
        this.playerSynchronizer = new PlayerSynchronizer(cloudApi, onlinePlayerCount::get);
        this.presenceResponder = new ProxyPresenceResponder(
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

    private void onServerStopping(MinecraftServer server) {
        ProxyPresenceResponder responder = this.presenceResponder;
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

    @Override
    public List<ProxyPresencePlayer> getProxyPresencePlayers() {
        MinecraftServer currentServer = this.server;
        if (currentServer == null) {
            return List.of();
        }

        String serverName = SimpleCloudRuntime.serverName();
        return currentServer.getPlayerList().getPlayers().stream()
                .map(player -> toPresencePlayer(currentServer, player, serverName))
                .toList();
    }

    private ProxyPresencePlayer toPresencePlayer(
            MinecraftServer currentServer,
            ServerPlayer player,
            String serverName
    ) {
        String name = player.getName().getString();
        return new ProxyPresencePlayer(
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
