package app.simplecloud.api.provider.paper;

import app.simplecloud.api.CloudApiOptions;
import app.simplecloud.api.internal.CloudApiImpl;
import app.simplecloud.api.internal.integration.adventure.AdventureIntegration;
import app.simplecloud.api.runtime.SimpleCloudRuntime;
import org.bukkit.plugin.java.JavaPlugin;

public class PaperApiProvider extends JavaPlugin {

    // Created with the plugin, not in onEnable: Paper constructs every plugin before it enables any,
    // so plugins constructed after this one, in practice those that depend on it, find this connection
    // when they create their CloudApi. onEnable creates it again when a disable closed it.
    private CloudApiImpl cloudApi = createCloudApi();
    private AdventureIntegration adventureIntegration;

    @Override
    public void onEnable() {
        if (cloudApi == null) {
            this.cloudApi = createCloudApi();
        }

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
        cloudApi.close();
        this.cloudApi = null;
        getLogger().info("SimpleCloud v3 API provider uninitialized!");
    }

    private static CloudApiImpl createCloudApi() {
        return CloudApiImpl.createNatsConnectionOwner(CloudApiOptions.DEFAULT);
    }
}
