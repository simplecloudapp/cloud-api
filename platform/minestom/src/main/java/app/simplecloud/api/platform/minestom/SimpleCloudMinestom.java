package app.simplecloud.api.platform.minestom;

import app.simplecloud.api.CloudApi;
import app.simplecloud.api.internal.CloudApiImpl;
import app.simplecloud.api.internal.integration.adventure.AdventureIntegration;
import app.simplecloud.api.runtime.SimpleCloudRuntime;
import net.minestom.server.MinecraftServer;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SimpleCloudMinestom {

    private static final Logger LOGGER = LoggerFactory.getLogger(SimpleCloudMinestom.class);

    private static SimpleCloudMinestom instance;

    private final CloudApiImpl cloudApi;
    private final AdventureIntegration adventureIntegration;
    private final EventNode<Event> node = EventNode.all("simplecloud-api");

    private SimpleCloudMinestom(CloudApiImpl cloudApi) {
        this.cloudApi = cloudApi;

        String serverId = SimpleCloudRuntime.serverId();
        String groupName = SimpleCloudRuntime.groupName();
        this.adventureIntegration = new AdventureIntegration(
                cloudApi,
                new MinestomPlayerAudiences(),
                serverId,
                groupName == null || groupName.isBlank() ? serverId : groupName
        );
    }

    public static synchronized CloudApi init() {
        if (instance != null) {
            return instance.cloudApi;
        }
        if (MinecraftServer.process() == null) {
            throw new IllegalStateException("MinecraftServer.init() must be called before SimpleCloudMinestom.init()");
        }

        CloudApiImpl cloudApi = (CloudApiImpl) CloudApi.create();
        SimpleCloudMinestom provider = new SimpleCloudMinestom(cloudApi);
        try {
            provider.start();
        } catch (RuntimeException exception) {
            provider.stop();
            throw exception;
        }

        instance = provider;
        MinecraftServer.getSchedulerManager().buildShutdownTask(SimpleCloudMinestom::shutdown);
        LOGGER.info("SimpleCloud v3 API provider initialized!");
        return cloudApi;
    }

    public static synchronized void shutdown() {
        SimpleCloudMinestom provider = instance;
        if (provider == null) {
            return;
        }

        instance = null;
        provider.stop();
        LOGGER.info("SimpleCloud v3 API provider uninitialized!");
    }

    public static synchronized boolean isInitialized() {
        return instance != null;
    }

    public static synchronized CloudApi getApi() {
        if (instance == null) {
            throw new IllegalStateException("SimpleCloudMinestom.init() must be called before accessing the API");
        }
        return instance.cloudApi;
    }

    private void start() {
        adventureIntegration.start();
        node.addListener(PlayerDisconnectEvent.class,
                event -> adventureIntegration.forgetPlayer(event.getPlayer().getUuid()));
        MinecraftServer.getGlobalEventHandler().addChild(node);
    }

    private void stop() {
        MinecraftServer.getGlobalEventHandler().removeChild(node);
        adventureIntegration.stop();
        cloudApi.close();
    }
}
