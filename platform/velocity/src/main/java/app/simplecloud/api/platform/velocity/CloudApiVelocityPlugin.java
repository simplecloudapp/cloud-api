package app.simplecloud.api.platform.velocity;

import app.simplecloud.api.CloudApi;
import app.simplecloud.api.internal.CloudApiImpl;
import app.simplecloud.api.internal.integration.player.PlayerCommandHandler;
import app.simplecloud.api.internal.integration.player.PlayerSessionReporter;
import app.simplecloud.api.internal.integration.presence.ProxyPresenceResponder;
import app.simplecloud.api.internal.integration.presence.ProxyPresenceTracker;
import app.simplecloud.api.presence.ProxyPresencePlayer;
import app.simplecloud.api.presence.ProxyPresencePlayerProvider;
import app.simplecloud.api.runtime.SimpleCloudRuntime;
import app.simplecloud.api.platform.shared.LuckPermsPlayerPropertySynchronizer;
import app.simplecloud.api.platform.shared.PlayerPropertySynchronizer;
import app.simplecloud.api.platform.shared.PlayerSynchronizer;
import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.luckperms.api.LuckPermsProvider;
import org.slf4j.Logger;

import java.util.List;
import java.util.Locale;

@Plugin(
        id = "simplecloud-api",
        name = "SimpleCloud API",
        version = "1.0",
        authors = {"Fllip"},
        dependencies = {@Dependency(id = "luckperms", optional = true)}
)
public class CloudApiVelocityPlugin implements ProxyPresencePlayerProvider {

    // Higher priorities run first, so this runs before onProxyShutdown (priority 0) closes the CloudApi.
    private static final short STOP_BEFORE_SHUTDOWN_PRIORITY = 100;

    private final Logger logger;
    private final ProxyServer proxyServer;
    private final CloudApiImpl cloudApi;
    private final String proxyName;
    private final PlayerSynchronizer playerSynchronizer;
    private final PlayerSessionReporter playerSessionReporter;
    private final PlayerCommandHandler playerCommandHandler;
    private final ProxyPresenceTracker proxyPresenceTracker;
    private final ProxyPresenceResponder proxyPresenceResponder;

    @Inject
    public CloudApiVelocityPlugin(
            Logger logger,
            ProxyServer proxyServer
    ) {
        this.logger = logger;
        this.proxyServer = proxyServer;
        this.cloudApi = (CloudApiImpl) CloudApi.create();
        this.proxyName = SimpleCloudRuntime.serverName();
        this.playerSynchronizer = new PlayerSynchronizer(cloudApi, proxyServer::getPlayerCount);
        this.playerSessionReporter = new PlayerSessionReporter(cloudApi);
        this.playerCommandHandler = new PlayerCommandHandler(cloudApi, new VelocityPlayers(proxyServer));
        this.proxyPresenceTracker = new ProxyPresenceTracker(proxyName);
        this.proxyPresenceResponder = new ProxyPresenceResponder(
                cloudApi.getNatsConnection(),
                cloudApi.getNetworkId(),
                SimpleCloudRuntime.serverId(),
                this
        );
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        logger.info("SimpleCloud v3 API provider initialized!");
        // LuckPerms registers its API while the proxy initializes, so this cannot happen in the constructor.
        PlayerPropertySynchronizer playerPropertySynchronizer = createPlayerPropertySynchronizer();
        proxyServer.getEventManager().register(this, ProxyShutdownEvent.class, STOP_BEFORE_SHUTDOWN_PRIORITY,
                shutdown -> playerPropertySynchronizer.stop());
        proxyServer.getEventManager().register(this, new PlayerConnectionListener(
                playerSynchronizer,
                playerSessionReporter,
                proxyPresenceTracker,
                proxyName,
                playerPropertySynchronizer
        ));

        playerSynchronizer.start();
        playerCommandHandler.start();
        proxyPresenceResponder.start();
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        logger.info("SimpleCloud v3 API provider uninitialized!");
        proxyPresenceResponder.stop();
        playerSynchronizer.stop();
        playerCommandHandler.stop();
        cloudApi.close();
    }

    private PlayerPropertySynchronizer createPlayerPropertySynchronizer() {
        if (!proxyServer.getPluginManager().isLoaded("luckperms")) {
            return PlayerPropertySynchronizer.NONE;
        }
        try {
            LuckPermsPlayerPropertySynchronizer luckPermsSynchronizer = new LuckPermsPlayerPropertySynchronizer(
                    cloudApi,
                    LuckPermsProvider.get(),
                    this,
                    new VelocityPlayers(proxyServer),
                    logger::error
            );
            luckPermsSynchronizer.start();
            logger.info("LuckPerms player property synchronization enabled");
            return luckPermsSynchronizer;
        } catch (IllegalStateException exception) {
            logger.warn("LuckPerms is present but its API is not available", exception);
            return PlayerPropertySynchronizer.NONE;
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
}
