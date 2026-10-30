package app.simplecloud.api.provider.paper;

import app.simplecloud.api.CloudApi;
import app.simplecloud.api.internal.CloudApiImpl;
import app.simplecloud.api.internal.integration.adventure.AdventureIntegration;
import app.simplecloud.api.platform.shared.OnlinePlayerTracker;
import app.simplecloud.api.platform.shared.PlayerSynchronizer;
import app.simplecloud.api.runtime.SimpleCloudRuntime;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class PaperApiProvider extends JavaPlugin {

    private CloudApiImpl cloudApi;
    private AdventureIntegration adventureIntegration;
    private PlayerSynchronizer playerSynchronizer;

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

        this.initializePlayerSynchronizer();

        getLogger().info("SimpleCloud v3 API provider initialized!");
    }

    @Override
    public void onDisable() {
        if (playerSynchronizer != null) {
            playerSynchronizer.stop();
        }
        if (adventureIntegration != null) {
            adventureIntegration.stop();
        }
        if (cloudApi != null) {
            cloudApi.close();
        }
        getLogger().info("SimpleCloud v3 API provider uninitialized!");
    }

    private void initializePlayerSynchronizer() {
        OnlinePlayerTracker onlinePlayerTracker = new OnlinePlayerTracker();
        onlinePlayerTracker.reset(Bukkit.getOnlinePlayers().stream().map(Player::getUniqueId).toList());
        this.playerSynchronizer = new PlayerSynchronizer(cloudApi, onlinePlayerTracker);
        Bukkit.getPluginManager().registerEvents(new PlayerConnectionListener(onlinePlayerTracker, playerSynchronizer), this);
        playerSynchronizer.start();
        playerSynchronizer.updatePlayerCount();
    }
}
