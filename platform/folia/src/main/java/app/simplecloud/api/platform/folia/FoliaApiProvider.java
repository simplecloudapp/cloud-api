package app.simplecloud.api.platform.folia;

import app.simplecloud.api.CloudApi;
import app.simplecloud.api.internal.CloudApiImpl;
import app.simplecloud.api.platform.shared.OnlinePlayerTracker;
import app.simplecloud.api.platform.shared.PlayerSynchronizer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
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
        // The synchronizer reads the count off-thread, so it must not touch Bukkit.getOnlinePlayers() on Folia.
        OnlinePlayerTracker onlinePlayerTracker = new OnlinePlayerTracker();
        onlinePlayerTracker.reset(Bukkit.getOnlinePlayers().stream().map(Player::getUniqueId).toList());
        this.playerSynchronizer = new PlayerSynchronizer(cloudApi, onlinePlayerTracker::count);
        Bukkit.getPluginManager().registerEvents(new PlayerConnectionListener(onlinePlayerTracker, playerSynchronizer), this);
        playerSynchronizer.start();
        playerSynchronizer.updatePlayerCount();
    }
}
