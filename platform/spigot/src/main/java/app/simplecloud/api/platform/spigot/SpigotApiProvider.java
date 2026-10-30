package app.simplecloud.api.platform.spigot;

import app.simplecloud.api.CloudApi;
import app.simplecloud.api.platform.shared.OnlinePlayerTracker;
import app.simplecloud.api.platform.shared.PlayerSynchronizer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class SpigotApiProvider extends JavaPlugin {

    private final CloudApi cloudApi = CloudApi.create();
    private final OnlinePlayerTracker onlinePlayerTracker = new OnlinePlayerTracker();
    private final PlayerSynchronizer playerSynchronizer = new PlayerSynchronizer(cloudApi, onlinePlayerTracker);

    @Override
    public void onEnable() {
        getLogger().info("SimpleCloud v3 API provider initialized!");
        onlinePlayerTracker.reset(Bukkit.getOnlinePlayers().stream().map(Player::getUniqueId).toList());
        Bukkit.getPluginManager().registerEvents(new PlayerConnectionListener(onlinePlayerTracker, playerSynchronizer), this);

        playerSynchronizer.start();
        playerSynchronizer.updatePlayerCount();
    }

    @Override
    public void onDisable() {
        getLogger().info("SimpleCloud v3 API provider uninitialized!");
        playerSynchronizer.stop();
        cloudApi.close();
    }
}
