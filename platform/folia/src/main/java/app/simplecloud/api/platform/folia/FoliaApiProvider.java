package app.simplecloud.api.platform.folia;

import app.simplecloud.api.CloudApi;
import app.simplecloud.api.internal.CloudApiImpl;
import org.bukkit.plugin.java.JavaPlugin;

public class FoliaApiProvider extends JavaPlugin {

    private CloudApiImpl cloudApi;
    private FoliaAdventureIntegration foliaAdventureIntegration;

    @Override
    public void onEnable() {
        this.cloudApi = (CloudApiImpl) CloudApi.create();
        this.foliaAdventureIntegration = new FoliaAdventureIntegration(this, cloudApi);
        this.foliaAdventureIntegration.start();

        getLogger().info("SimpleCloud v3 API provider initialized!");
    }

    @Override
    public void onDisable() {
        if (foliaAdventureIntegration != null) {
            foliaAdventureIntegration.stop();
        }
        if (cloudApi != null) {
            cloudApi.close();
        }

        getLogger().info("SimpleCloud v3 API provider uninitialized!");
    }
}
