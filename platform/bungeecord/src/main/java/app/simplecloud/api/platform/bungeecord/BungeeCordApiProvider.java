package app.simplecloud.api.platform.bungeecord;

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
import app.simplecloud.api.platform.shared.PlayerSynchronizer;
import net.kyori.adventure.platform.bungeecord.BungeeAudiences;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Plugin;
import net.luckperms.api.LuckPermsProvider;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class BungeeCordApiProvider extends Plugin implements ProxyPresencePlayerProvider {

    private CloudApiImpl cloudApi;
    private String proxyName;
    private PlayerSynchronizer playerSynchronizer;
    private PlayerSessionReporter playerSessionReporter;
    private PlayerCommandHandler playerCommandHandler;
    private ProxyPresenceTracker proxyPresenceTracker;
    private ProxyPresenceResponder proxyPresenceResponder;
    private LuckPermsPlayerPropertySynchronizer luckPermsSynchronizer;

    private BungeeAudiences bungeeAudiences;

    @Override
    public void onEnable() {
        this.cloudApi = (CloudApiImpl) CloudApi.create();
        this.proxyName = SimpleCloudRuntime.serverName();
        this.playerSynchronizer = new PlayerSynchronizer(
            cloudApi,
            () -> (long) getProxy().getOnlineCount()
        );
        this.playerSessionReporter = new PlayerSessionReporter(cloudApi);
        this.playerCommandHandler = new PlayerCommandHandler(cloudApi, new BungeeCordPlayers(getProxy()));
        this.proxyPresenceTracker = new ProxyPresenceTracker(proxyName);
        this.proxyPresenceResponder = new ProxyPresenceResponder(
                cloudApi.getNatsConnection(),
                cloudApi.getNetworkId(),
                SimpleCloudRuntime.serverId(),
                this
        );

        this.bungeeAudiences = BungeeAudiences.create(this);

        initializeLuckPermsIntegration();

        getLogger().info("SimpleCloud v3 API provider initialized!");
        getProxy().getPluginManager().registerListener(this, new PlayerConnectionListener(
                this,
                playerSynchronizer,
                playerSessionReporter,
                proxyPresenceTracker,
                proxyName,
                this::synchronizeLuckPermsProperties,
                this::forgetLuckPermsProperties
        ));

        playerSynchronizer.start();
        playerCommandHandler.start();
        proxyPresenceResponder.start();
    }

    @Override
    public void onDisable() {
        getLogger().info("SimpleCloud v3 API provider uninitialized!");
        proxyPresenceResponder.stop();
        if (luckPermsSynchronizer != null) {
            luckPermsSynchronizer.stop();
        }
        playerSynchronizer.stop();
        playerCommandHandler.stop();

        if (bungeeAudiences != null) {
            bungeeAudiences.close();
        }
        cloudApi.close();
    }

    private void initializeLuckPermsIntegration() {
        if (getProxy().getPluginManager().getPlugin("LuckPerms") == null) {
            return;
        }
        try {
            luckPermsSynchronizer = new LuckPermsPlayerPropertySynchronizer(
                    cloudApi,
                    LuckPermsProvider.get(),
                    this,
                    uniqueId -> getProxy().getPlayer(uniqueId) != null,
                    (message, throwable) -> getLogger().log(java.util.logging.Level.SEVERE, message, throwable)
            );
            luckPermsSynchronizer.start();
            getLogger().info("LuckPerms player property synchronization enabled");
        } catch (IllegalStateException exception) {
            luckPermsSynchronizer = null;
            getLogger().log(java.util.logging.Level.WARNING, "LuckPerms is present but its API is not available", exception);
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
        return getProxy().getPlayers().stream()
                .map(this::toPresencePlayer)
                .toList();
    }

    private ProxyPresencePlayer toPresencePlayer(ProxiedPlayer player) {
        String connectedServerName = player.getServer() != null ? player.getServer().getInfo().getName() : "";
        Locale locale = player.getLocale();
        var pendingConnection = player.getPendingConnection();

        return proxyPresenceTracker.createSnapshot(
                player.getUniqueId().toString(),
                player.getName(),
                player.getDisplayName(),
                connectedServerName,
                locale != null ? locale.toString() : "en_US",
                pendingConnection != null ? pendingConnection.getVersion() : 0,
                pendingConnection != null && pendingConnection.isOnlineMode()
        );
    }
}
