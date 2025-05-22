package app.simplecloud.api.platform.velocity

import app.simplecloud.api.platform.shared.PlayerSynchronizer
import app.simplecloud.controller.api.ControllerApi
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent
import com.velocitypowered.api.proxy.ProxyServer
import org.slf4j.Logger
import javax.inject.Inject

class VelocityApiProvider @Inject constructor(
    private val logger: Logger,
    private val proxyServer: ProxyServer
) {

    private val controllerApi = ControllerApi.createCoroutineApi()
    private val playerSynchronizer =
        PlayerSynchronizer(controllerApi) { proxyServer.playerCount.toLong() }

    @Subscribe
    fun onProxyInitialize(event: ProxyInitializeEvent) {
        logger.info("SimpleCloud v3 API provider initialized!")
        proxyServer.eventManager.register(this, PlayerConnectionListener(playerSynchronizer))

        playerSynchronizer.start()
    }

    @Subscribe
    fun onProxyShutdown(event: ProxyShutdownEvent) {
        logger.info("SimpleCloud v3 API provider uninitialized!")
        playerSynchronizer.stop()
    }

}