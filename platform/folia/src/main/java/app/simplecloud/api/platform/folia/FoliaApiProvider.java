package app.simplecloud.api.platform.folia;

import app.simplecloud.api.CloudApi;
import app.simplecloud.api.internal.CloudApiImpl;
import app.simplecloud.api.platform.shared.PlayerSynchronizer;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class FoliaApiProvider extends JavaPlugin {

    private CloudApiImpl cloudApi;
    private FoliaAdventureIntegration foliaAdventureIntegration;
    private PlayerSynchronizer playerSynchronizer;

    @Override
    public void onEnable() {
        this.cloudApi = (CloudApiImpl) CloudApi.create();
        this.foliaAdventureIntegration = new FoliaAdventureIntegration(this, cloudApi);
        this.foliaAdventureIntegration.start();

        this.initializePlayerSynchronizer();

        getLogger().info("SimpleCloud v3 API provider initialized!");
    }

    @Override
    public void onDisable() {
        if (playerSynchronizer != null) {
            playerSynchronizer.stop();
        }
        if (foliaAdventureIntegration != null) {
            foliaAdventureIntegration.stop();
        }
        if (cloudApi != null) {
            cloudApi.close();
        }

        getLogger().info("SimpleCloud v3 API provider uninitialized!");
    }

    private void initializePlayerSynchronizer() {
        this.playerSynchronizer = new PlayerSynchronizer(cloudApi, () -> (long) Bukkit.getOnlinePlayers().size());
        Bukkit.getPluginManager().registerEvents(new PlayerConnectionListener(playerSynchronizer), this);
        playerSynchronizer.start();
    }
}
