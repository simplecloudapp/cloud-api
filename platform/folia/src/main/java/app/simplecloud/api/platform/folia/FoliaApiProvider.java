package app.simplecloud.api.platform.folia;

import app.simplecloud.api.CloudApi;
import app.simplecloud.api.internal.CloudApiImpl;
import app.simplecloud.api.internal.integration.heap.HeapUsageReporter;
import org.bukkit.plugin.java.JavaPlugin;

public class FoliaApiProvider extends JavaPlugin {

    private CloudApiImpl cloudApi;
    private FoliaAdventureIntegration foliaAdventureIntegration;
    private HeapUsageReporter heapUsageReporter;

    @Override
    public void onEnable() {
        this.cloudApi = (CloudApiImpl) CloudApi.create();
        this.foliaAdventureIntegration = new FoliaAdventureIntegration(this, cloudApi);
        this.foliaAdventureIntegration.start();
        this.heapUsageReporter = new HeapUsageReporter(cloudApi);
        this.heapUsageReporter.start();

        getLogger().info("SimpleCloud v3 API provider initialized!");
    }

    @Override
    public void onDisable() {
        if (heapUsageReporter != null) {
            heapUsageReporter.stop();
        }
        if (foliaAdventureIntegration != null) {
            foliaAdventureIntegration.stop();
        }
        if (cloudApi != null) {
            cloudApi.close();
        }

        getLogger().info("SimpleCloud v3 API provider uninitialized!");
    }
}
