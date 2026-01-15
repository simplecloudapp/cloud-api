package app.simplecloud.api.provider.paper;

import app.simplecloud.api.CloudApi;
import app.simplecloud.api.internal.integration.adventure.AdventureIntegration;
import net.kyori.adventure.audience.Audience;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class PaperApiProvider extends JavaPlugin {

    private CloudApi cloudApi;
    private AdventureIntegration adventureIntegration;

    @Override
    public void onEnable() {
        this.cloudApi = CloudApi.create();

        String serverId = System.getenv("SIMPLECLOUD_UNIQUE_ID");
        if (serverId == null) {
            serverId = "unknown";
        }

        String groupName = System.getenv("SIMPLECLOUD_GROUP");

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
