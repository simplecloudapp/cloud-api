package app.simplecloud.api.platform.folia;

import app.simplecloud.api.CloudApiOptions;
import app.simplecloud.api.internal.CloudApiImpl;
import app.simplecloud.api.platform.shared.OnlinePlayerTracker;
import app.simplecloud.api.platform.shared.PlayerSynchronizer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class FoliaApiProvider extends JavaPlugin {

    // Created with the plugin, not in onEnable: Folia constructs every plugin before it enables any,
    // so plugins constructed after this one, in practice those that depend on it, find this connection
    // when they create their CloudApi. onEnable creates it again when a disable closed it.
    private CloudApiImpl cloudApi = createCloudApi();
    private FoliaAdventureIntegration foliaAdventureIntegration;
    private PlayerSynchronizer playerSynchronizer;

    @Override
    public void onEnable() {
        if (cloudApi == null) {
            this.cloudApi = createCloudApi();
        }

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
        cloudApi.close();
        this.cloudApi = null;

        getLogger().info("SimpleCloud v3 API provider uninitialized!");
    }

    private static CloudApiImpl createCloudApi() {
        return CloudApiImpl.createNatsConnectionOwner(CloudApiOptions.DEFAULT);
    }

    private void initializePlayerSynchronizer() {
        // The synchronizer reads the count off-thread, so it must not touch Bukkit.getOnlinePlayers() on Folia.
        OnlinePlayerTracker onlinePlayerTracker = new OnlinePlayerTracker();
        onlinePlayerTracker.reset(Bukkit.getOnlinePlayers().stream().map(Player::getUniqueId).toList());
        this.playerSynchronizer = new PlayerSynchronizer(cloudApi, onlinePlayerTracker);
        Bukkit.getPluginManager().registerEvents(new PlayerConnectionListener(onlinePlayerTracker, playerSynchronizer), this);
        playerSynchronizer.start();
        playerSynchronizer.updatePlayerCount();
    }
}
