package app.simplecloud.api.provider.paper;

import app.simplecloud.api.CloudApi;
import app.simplecloud.api.internal.integration.adventure.AdventureIntegration;
import app.simplecloud.api.runtime.SimpleCloudRuntime;
import dev.faststats.Metrics;
import dev.faststats.bukkit.BukkitContext;
import net.kyori.adventure.audience.Audience;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class PaperApiProvider extends JavaPlugin {

    private final BukkitContext fastStatsContext = new BukkitContext.Factory(
            this,
            "2e8308cb6431a46a68fa0f59362978f7"
    ).metrics(Metrics.Factory::create).create();
    private CloudApi cloudApi;
    private AdventureIntegration adventureIntegration;

    @Override
    public void onEnable() {
        this.cloudApi = CloudApi.create();

        String serverId = SimpleCloudRuntime.serverId();
        String groupName = SimpleCloudRuntime.groupName();

        this.adventureIntegration = AdventureIntegration.builder(cloudApi)
                .playerResolver(Bukkit::getPlayer)
                .allPlayersSupplier(() -> Audience.audience(Bukkit.getOnlinePlayers()))
                .forPlayers()
                .forServer(serverId)
                .forGroup(groupName != null ? groupName : serverId)
                .build();
        adventureIntegration.start();
        fastStatsContext.ready();

        getLogger().info("SimpleCloud v3 API provider initialized!");
    }

    @Override
    public void onDisable() {
        if (adventureIntegration != null) {
            adventureIntegration.stop();
        }
        fastStatsContext.shutdown();
        getLogger().info("SimpleCloud v3 API provider uninitialized!");
    }
}
