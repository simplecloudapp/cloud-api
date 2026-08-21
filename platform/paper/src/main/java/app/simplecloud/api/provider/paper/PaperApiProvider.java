package app.simplecloud.api.provider.paper;

import app.simplecloud.api.CloudApi;
import app.simplecloud.api.internal.CloudApiImpl;
import app.simplecloud.api.internal.integration.adventure.AdventureIntegration;
import app.simplecloud.api.internal.integration.presence.PresenceResponder;
import app.simplecloud.api.presence.PresencePlayer;
import app.simplecloud.api.presence.PresencePlayerProvider;
import app.simplecloud.api.runtime.SimpleCloudRuntime;
import dev.faststats.Metrics;
import dev.faststats.bukkit.BukkitContext;
import net.kyori.adventure.audience.Audience;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public class PaperApiProvider extends JavaPlugin implements PresencePlayerProvider {

    private final BukkitContext fastStatsContext = new BukkitContext.Factory(
            this,
            "2e8308cb6431a46a68fa0f59362978f7"
    ).metrics(Metrics.Factory::create).create();
    private CloudApiImpl cloudApi;
    private AdventureIntegration adventureIntegration;
    private PresenceResponder presenceResponder;

    @Override
    public void onEnable() {
        this.cloudApi = (CloudApiImpl) CloudApi.create();

        String serverId = SimpleCloudRuntime.serverId();
        String groupName = SimpleCloudRuntime.groupName();

        this.adventureIntegration = AdventureIntegration.builder(cloudApi)
                .playerResolver(Bukkit::getPlayer)
                .allPlayersSupplier(() -> Audience.audience(Bukkit.getOnlinePlayers()))
                .forPlayers()
                .forServer(serverId)
                .forGroup(groupName != null ? groupName : serverId)
                .build();
        this.presenceResponder = new PresenceResponder(
                cloudApi.getNatsConnection(),
                cloudApi.getNetworkId(),
                serverId,
                this
        );
        adventureIntegration.start();
        presenceResponder.start();
        fastStatsContext.ready();

        getLogger().info("SimpleCloud v3 API provider initialized!");
    }

    @Override
    public void onDisable() {
        if (presenceResponder != null) {
            presenceResponder.stop();
        }
        if (adventureIntegration != null) {
            adventureIntegration.stop();
        }
        if (cloudApi != null) {
            cloudApi.close();
        }
        fastStatsContext.shutdown();
        getLogger().info("SimpleCloud v3 API provider uninitialized!");
    }

    @Override
    public List<PresencePlayer> getPresencePlayers() {
        String serverName = currentServerName();
        return Bukkit.getOnlinePlayers().stream()
                .map(player -> new PresencePlayer(
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
