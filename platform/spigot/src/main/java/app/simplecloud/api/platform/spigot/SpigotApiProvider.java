package app.simplecloud.api.platform.spigot;

import app.simplecloud.api.CloudApi;
import app.simplecloud.api.internal.CloudApiImpl;
import app.simplecloud.api.internal.integration.presence.PresenceResponder;
import app.simplecloud.api.platform.shared.PlayerSynchronizer;
import app.simplecloud.api.presence.PresencePlayer;
import app.simplecloud.api.presence.PresencePlayerProvider;
import app.simplecloud.api.runtime.SimpleCloudRuntime;
import dev.faststats.Metrics;
import dev.faststats.bukkit.BukkitContext;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public class SpigotApiProvider extends JavaPlugin implements PresencePlayerProvider {

    private final BukkitContext fastStatsContext = new BukkitContext.Factory(
        this,
        "2e8308cb6431a46a68fa0f59362978f7"
    ).metrics(Metrics.Factory::create).create();
    private final CloudApiImpl cloudApi = (CloudApiImpl) CloudApi.create();
    private final PlayerSynchronizer playerSynchronizer = new PlayerSynchronizer(
        cloudApi,
        () -> (long) Bukkit.getOnlinePlayers().size()
    );
    private final PresenceResponder presenceResponder = new PresenceResponder(
            cloudApi.getNatsConnection(),
            cloudApi.getNetworkId(),
            SimpleCloudRuntime.serverId(),
            this
    );

    @Override
    public void onEnable() {
        getLogger().info("SimpleCloud v3 API provider initialized!");
        Bukkit.getPluginManager().registerEvents(new PlayerConnectionListener(playerSynchronizer), this);

        playerSynchronizer.start();
        presenceResponder.start();
        fastStatsContext.ready();
    }

    @Override
    public void onDisable() {
        getLogger().info("SimpleCloud v3 API provider uninitialized!");
        presenceResponder.stop();
        playerSynchronizer.stop();
        cloudApi.close();
        fastStatsContext.shutdown();
    }

    @Override
    public List<PresencePlayer> getPresencePlayers() {
        String serverName = currentServerName();
        return Bukkit.getOnlinePlayers().stream()
                .map(player -> new PresencePlayer(
                        player.getUniqueId().toString(),
                        player.getName(),
                        player.getDisplayName(),
                        serverName,
                        "",
                        0L,
                        player.getLocale(),
                        0,
                        Bukkit.getOnlineMode(),
                        ""
                ))
                .toList();
    }

    private String currentServerName() {
        String serverName = SimpleCloudRuntime.serverName();
        return serverName == null || serverName.isBlank() ? SimpleCloudRuntime.serverId() : serverName;
    }
}
