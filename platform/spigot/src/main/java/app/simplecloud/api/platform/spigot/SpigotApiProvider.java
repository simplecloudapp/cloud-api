package app.simplecloud.api.platform.spigot;

import app.simplecloud.api.CloudApi;
import app.simplecloud.api.platform.shared.PlayerSynchronizer;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class SpigotApiProvider extends JavaPlugin {

    private final CloudApi cloudApi = CloudApi.create();
    private final PlayerSynchronizer playerSynchronizer = new PlayerSynchronizer(
        cloudApi,
        () -> (long) Bukkit.getOnlinePlayers().size()
    );

    @Override
    public void onEnable() {
        getLogger().info("SimpleCloud v3 API provider initialized!");
        Bukkit.getPluginManager().registerEvents(new PlayerConnectionListener(playerSynchronizer), this);

        playerSynchronizer.start();
    }

    @Override
    public void onDisable() {
        getLogger().info("SimpleCloud v3 API provider uninitialized!");
        playerSynchronizer.stop();
    }
}


