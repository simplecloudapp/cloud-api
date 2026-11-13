package app.simplecloud.api.platform.spigot.legacy;

import app.simplecloud.api.CloudApi;
import org.bukkit.plugin.java.JavaPlugin;

public class LegacySpigotApiProvider extends JavaPlugin {

    private final CloudApi cloudApi = CloudApi.create();

    @Override
    public void onEnable() {
        getLogger().info("SimpleCloud v3 API provider initialized!");
    }

    @Override
    public void onDisable() {
        getLogger().info("SimpleCloud v3 API provider uninitialized!");
        cloudApi.close();
    }
}
