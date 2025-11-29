package app.simplecloud.api.platform.velocity;

import app.simplecloud.api.CloudApi;
import app.simplecloud.api.platform.shared.PlayerSynchronizer;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;

import javax.inject.Inject;

public class VelocityApiProvider {

    private final Logger logger;
    private final ProxyServer proxyServer;
    private final CloudApi cloudApi;
    private final PlayerSynchronizer playerSynchronizer;

    @Inject
    public VelocityApiProvider(Logger logger, ProxyServer proxyServer) {
        this.logger = logger;
        this.proxyServer = proxyServer;
        this.cloudApi = CloudApi.create();
        this.playerSynchronizer = new PlayerSynchronizer(
            cloudApi,
            () -> (long) proxyServer.getPlayerCount()
        );
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        logger.info("SimpleCloud v3 API provider initialized!");
        proxyServer.getEventManager().register(this, new PlayerConnectionListener(playerSynchronizer));

        playerSynchronizer.start();
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        logger.info("SimpleCloud v3 API provider uninitialized!");
        playerSynchronizer.stop();
    }
}

