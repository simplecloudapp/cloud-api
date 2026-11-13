package app.simplecloud.api.provider.paper;

import app.simplecloud.api.CloudApi;
import app.simplecloud.api.internal.CloudApiImpl;
import app.simplecloud.api.internal.integration.adventure.AdventureIntegration;
import app.simplecloud.api.runtime.SimpleCloudRuntime;
import org.bukkit.plugin.java.JavaPlugin;

public class PaperApiProvider extends JavaPlugin {

    private CloudApiImpl cloudApi;
    private AdventureIntegration adventureIntegration;

    @Override
    public void onEnable() {
        this.cloudApi = (CloudApiImpl) CloudApi.create();

        String serverId = SimpleCloudRuntime.serverId();
        String groupName = SimpleCloudRuntime.groupName();

        this.adventureIntegration = new AdventureIntegration(
                cloudApi,
                new PaperPlayerAudiences(),
                serverId,
                groupName != null ? groupName : serverId
        );
        adventureIntegration.start();

        getLogger().info("SimpleCloud v3 API provider initialized!");
    }

    @Override
    public void onDisable() {
        if (adventureIntegration != null) {
            adventureIntegration.stop();
        }
        if (cloudApi != null) {
            cloudApi.close();
        }
        getLogger().info("SimpleCloud v3 API provider uninitialized!");
    }
}
