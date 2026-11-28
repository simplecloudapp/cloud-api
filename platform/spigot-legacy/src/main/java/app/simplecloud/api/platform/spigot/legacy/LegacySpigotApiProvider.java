package app.simplecloud.api.platform.spigot.legacy;

import app.simplecloud.api.internal.integration.heap.StandaloneHeapUsageReporter;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Ships the SimpleCloud API for other plugins and reports the heap usage of this server.
 */
public class LegacySpigotApiProvider extends JavaPlugin {

    private final StandaloneHeapUsageReporter heapUsageReporter = new StandaloneHeapUsageReporter();

    @Override
    public void onEnable() {
        heapUsageReporter.start();
    }

    @Override
    public void onDisable() {
        heapUsageReporter.stop();
    }
}
