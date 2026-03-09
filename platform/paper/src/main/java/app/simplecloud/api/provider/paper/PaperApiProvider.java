package app.simplecloud.api.provider.paper;

import app.simplecloud.api.CloudApi;
import app.simplecloud.api.internal.integration.adventure.AdventureIntegration;
import app.simplecloud.api.runtime.SimpleCloudRuntime;
import net.kyori.adventure.audience.Audience;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class PaperApiProvider extends JavaPlugin {

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

        getLogger().info("SimpleCloud v3 API provider initialized!");
    }

    @Override
    public void onDisable() {
        if (adventureIntegration != null) {
            adventureIntegration.stop();
        }
        getLogger().info("SimpleCloud v3 API provider uninitialized!");
    }
}
