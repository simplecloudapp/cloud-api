package app.simplecloud.api.platform.spigot;

import app.simplecloud.api.CloudApi;
import app.simplecloud.api.platform.shared.PlayerSynchronizer;
import dev.faststats.Metrics;
import dev.faststats.bukkit.BukkitContext;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class SpigotApiProvider extends JavaPlugin {

    private final BukkitContext fastStatsContext = new BukkitContext.Factory(
        this,
        "2e8308cb6431a46a68fa0f59362978f7"
    ).metrics(Metrics.Factory::create).create();
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
        fastStatsContext.ready();
    }

    @Override
    public void onDisable() {
        getLogger().info("SimpleCloud v3 API provider uninitialized!");
        playerSynchronizer.stop();
        cloudApi.close();
        fastStatsContext.shutdown();
    }
}
