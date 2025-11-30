package app.simplecloud.api.platform.bungeecord;

import app.simplecloud.api.CloudApi;
import app.simplecloud.api.platform.shared.PlayerSynchronizer;
import net.md_5.bungee.api.plugin.Plugin;

public class BungeeCordApiProvider extends Plugin {

    private final CloudApi cloudApi = CloudApi.create();
    private final PlayerSynchronizer playerSynchronizer = new PlayerSynchronizer(
        cloudApi,
        () -> (long) getProxy().getOnlineCount()
    );

    @Override
    public void onEnable() {
        getLogger().info("SimpleCloud v3 API provider initialized!");
        getProxy().getPluginManager().registerListener(this, new PlayerConnectionListener(playerSynchronizer));

        playerSynchronizer.start();
    }

    @Override
    public void onDisable() {
        getLogger().info("SimpleCloud v3 API provider uninitialized!");
        playerSynchronizer.stop();
    }
}


