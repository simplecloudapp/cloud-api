package app.simplecloud.api.platform.velocity;

import app.simplecloud.api.CloudApi;
import app.simplecloud.api.internal.CloudApiImpl;
import app.simplecloud.api.internal.integration.player.PlayerIntegration;
import app.simplecloud.api.internal.integration.presence.ProxyPresenceResponder;
import app.simplecloud.api.internal.integration.presence.ProxyPresenceTracker;
import app.simplecloud.api.presence.ProxyPresencePlayer;
import app.simplecloud.api.presence.ProxyPresencePlayerProvider;
import app.simplecloud.api.player.CloudPlayer;
import app.simplecloud.api.runtime.SimpleCloudRuntime;
import app.simplecloud.api.platform.shared.LuckPermsPlayerPropertySynchronizer;
import app.simplecloud.api.platform.shared.PlayerSynchronizer;
import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ConnectionRequestBuilder;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import dev.faststats.Metrics;
import dev.faststats.velocity.VelocityContext;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.luckperms.api.LuckPermsProvider;
import org.slf4j.Logger;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Plugin(
        id = "simplecloud-api",
        name = "SimpleCloud API",
        version = "1.0",
        authors = {"Fllip"},
        dependencies = {@Dependency(id = "luckperms", optional = true)}
)
public class CloudApiVelocityPlugin implements ProxyPresencePlayerProvider {

    private final Logger logger;
    private final ProxyServer proxyServer;
    private final VelocityContext fastStatsContext;
    private final CloudApiImpl cloudApi;
    private final String proxyName;
    private final PlayerSynchronizer playerSynchronizer;
    private final PlayerIntegration playerIntegration;
    private final ProxyPresenceTracker proxyPresenceTracker;
    private final ProxyPresenceResponder proxyPresenceResponder;
    private LuckPermsPlayerPropertySynchronizer luckPermsSynchronizer;

    private final GsonComponentSerializer gsonComponentSerializer = GsonComponentSerializer.gson();

    @Inject
    public CloudApiVelocityPlugin(
            Logger logger,
            ProxyServer proxyServer,
            VelocityContext.Builder fastStatsContextBuilder
    ) {
        this.logger = logger;
        this.proxyServer = proxyServer;
        this.fastStatsContext = fastStatsContextBuilder
                .token("2e8308cb6431a46a68fa0f59362978f7")
                .metrics(Metrics.Factory::create)
                .create();
        this.cloudApi = (CloudApiImpl) CloudApi.create();
        this.proxyName = SimpleCloudRuntime.serverName();
        this.playerSynchronizer = new PlayerSynchronizer(
            cloudApi,
            () -> (long) proxyServer.getPlayerCount()
        );
        this.playerIntegration = new PlayerIntegration(cloudApi);
        this.proxyPresenceTracker = new ProxyPresenceTracker(proxyName);
        this.proxyPresenceResponder = new ProxyPresenceResponder(
                cloudApi.getNatsConnection(),
                cloudApi.getNetworkId(),
                SimpleCloudRuntime.serverId(),
                this
        );

        playerIntegration.onKick(this::handleKickRequest);
        playerIntegration.onConnect(this::handleConnectRequest);
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        logger.info("SimpleCloud v3 API provider initialized!");
        initializeLuckPermsIntegration();
        proxyServer.getEventManager().register(this, new PlayerConnectionListener(
                playerSynchronizer,
                playerIntegration,
                proxyPresenceTracker,
                proxyName,
                this::synchronizeLuckPermsProperties,
                this::forgetLuckPermsProperties
        ));

        playerSynchronizer.start();
        playerIntegration.start();
        proxyPresenceResponder.start();
        fastStatsContext.ready();
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        logger.info("SimpleCloud v3 API provider uninitialized!");
        proxyPresenceResponder.stop();
        if (luckPermsSynchronizer != null) {
            luckPermsSynchronizer.stop();
        }
        playerSynchronizer.stop();
        playerIntegration.stop();
        cloudApi.close();
        fastStatsContext.shutdown();
    }

    private void initializeLuckPermsIntegration() {
        if (!proxyServer.getPluginManager().isLoaded("luckperms")) {
            return;
        }
        try {
            luckPermsSynchronizer = new LuckPermsPlayerPropertySynchronizer(
                    cloudApi,
                    LuckPermsProvider.get(),
                    this,
                    uniqueId -> proxyServer.getPlayer(uniqueId).isPresent(),
                    (message, throwable) -> logger.error(message, throwable)
            );
            luckPermsSynchronizer.start();
            logger.info("LuckPerms player property synchronization enabled");
        } catch (IllegalStateException exception) {
            luckPermsSynchronizer = null;
            logger.warn("LuckPerms is present but its API is not available", exception);
        }
    }

    private void synchronizeLuckPermsProperties(UUID uniqueId) {
        if (luckPermsSynchronizer != null) {
            luckPermsSynchronizer.synchronize(uniqueId);
        }
    }

    private void forgetLuckPermsProperties(UUID uniqueId) {
        if (luckPermsSynchronizer != null) {
            luckPermsSynchronizer.forget(uniqueId);
        }
    }

    @Override
    public List<ProxyPresencePlayer> getProxyPresencePlayers() {
        return proxyServer.getAllPlayers().stream()
                .map(this::toPresencePlayer)
                .toList();
    }

    private ProxyPresencePlayer toPresencePlayer(Player player) {
        String connectedServerName = player.getCurrentServer()
                .map(serverConnection -> serverConnection.getServerInfo().getName())
                .orElse("");
        Locale locale = player.getEffectiveLocale();

        return proxyPresenceTracker.createSnapshot(
                player.getUniqueId().toString(),
                player.getUsername(),
                player.getUsername(),
                connectedServerName,
                locale != null ? locale.toString() : "en_US",
                player.getProtocolVersion().getProtocol(),
                player.isOnlineMode()
        );
    }

    private CompletableFuture<Boolean> handleKickRequest(String playerUniqueId, String reason) {
        return CompletableFuture.supplyAsync(() -> {
            Player player = proxyServer.getPlayer(UUID.fromString(playerUniqueId)).orElse(null);
            if (player == null) {
                // Don't respond - another proxy might have the player
                // Returning null signals to not send a response
                return null;
            }
            player.disconnect(gsonComponentSerializer.deserialize(reason != null ? reason : "{}"));
            return true;
        });
    }

    private CompletableFuture<CloudPlayer.ConnectResult> handleConnectRequest(String playerUniqueId, String serverName) {
        Player player = proxyServer.getPlayer(UUID.fromString(playerUniqueId)).orElse(null);
        if (player == null) {
            return CompletableFuture.completedFuture(null);
        }
        var registeredServer = proxyServer.getServer(serverName).orElse(null);
        if (registeredServer == null) {
            return CompletableFuture.completedFuture(CloudPlayer.ConnectResult.SERVER_NOT_FOUND);
        }

        return player.createConnectionRequest(registeredServer).connect()
                .thenApply(result -> {
                    ConnectionRequestBuilder.Status status = result.getStatus();
                    if (status == ConnectionRequestBuilder.Status.SUCCESS) {
                        return CloudPlayer.ConnectResult.SUCCESS;
                    } else if (status == ConnectionRequestBuilder.Status.ALREADY_CONNECTED) {
                        return CloudPlayer.ConnectResult.ALREADY_CONNECTED;
                    } else {
                        return CloudPlayer.ConnectResult.CONNECTION_FAILED;
                    }
                });
    }
}
