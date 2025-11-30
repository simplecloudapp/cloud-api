package app.simplecloud.api.provider.paper;

import org.bukkit.plugin.java.JavaPlugin;

public class PaperApiProvider extends JavaPlugin {
    @Override
    public void onEnable() {
        getLogger().info("SimpleCloud v3 API provider initialized!");
    }

    @Override
    public void onDisable() {
        getLogger().info("SimpleCloud v3 API provider uninitialized!");
    }
}


