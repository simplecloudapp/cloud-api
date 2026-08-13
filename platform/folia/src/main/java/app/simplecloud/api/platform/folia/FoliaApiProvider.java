package app.simplecloud.api.platform.folia;

import app.simplecloud.api.CloudApi;
import app.simplecloud.api.internal.CloudApiImpl;
import app.simplecloud.api.internal.integration.presence.ProxyPresenceResponder;
import app.simplecloud.api.presence.ProxyPresencePlayer;
import app.simplecloud.api.presence.ProxyPresencePlayerProvider;
import app.simplecloud.api.runtime.SimpleCloudRuntime;
import dev.faststats.Metrics;
import dev.faststats.bukkit.BukkitContext;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public class FoliaApiProvider extends JavaPlugin implements ProxyPresencePlayerProvider {

    private final BukkitContext fastStatsContext = new BukkitContext.Factory(
        this,
        "2e8308cb6431a46a68fa0f59362978f7"
    ).metrics(Metrics.Factory::create).create();
    private CloudApiImpl cloudApi;
    private FoliaAdventureIntegration foliaAdventureIntegration;
    private ProxyPresenceResponder presenceResponder;

    @Override
    public void onEnable() {
        this.cloudApi = (CloudApiImpl) CloudApi.create();
        this.foliaAdventureIntegration = new FoliaAdventureIntegration(this, cloudApi);
        this.presenceResponder = new ProxyPresenceResponder(
                cloudApi.getNatsConnection(),
                cloudApi.getNetworkId(),
                SimpleCloudRuntime.serverId(),
                this
        );
        this.foliaAdventureIntegration.start();
        this.presenceResponder.start();
        fastStatsContext.ready();

        getLogger().info("SimpleCloud v3 API provider initialized!");
    }

    @Override
    public void onDisable() {
        if (presenceResponder != null) {
            presenceResponder.stop();
        }
        if (foliaAdventureIntegration != null) {
            foliaAdventureIntegration.stop();
        }
        if (cloudApi != null) {
            cloudApi.close();
        }
        fastStatsContext.shutdown();

        getLogger().info("SimpleCloud v3 API provider uninitialized!");
    }

    @Override
    public List<ProxyPresencePlayer> getProxyPresencePlayers() {
        String serverName = currentServerName();
        return Bukkit.getOnlinePlayers().stream()
                .map(player -> new ProxyPresencePlayer(
                        player.getUniqueId().toString(),
                        player.getName(),
                        player.getName(),
                        serverName,
                        "",
                        0L,
                        player.getLocale(),
                        0,
                        Bukkit.getOnlineMode(),
                        ""
                ))
                .toList();
    }

    private String currentServerName() {
        String serverName = SimpleCloudRuntime.serverName();
        return serverName == null || serverName.isBlank() ? SimpleCloudRuntime.serverId() : serverName;
    }
}
