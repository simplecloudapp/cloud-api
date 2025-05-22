package app.simplecloud.api.platform.bungeecord

import app.simplecloud.api.platform.shared.PlayerSynchronizer
import app.simplecloud.controller.api.ControllerApi
import net.md_5.bungee.api.plugin.Plugin

class BungeeCordApiProvider : Plugin() {

    private val controllerApi = ControllerApi.createCoroutineApi()
    private val playerSynchronizer = PlayerSynchronizer(controllerApi) { proxy.onlineCount.toLong() }

    override fun onEnable() {
        logger.info("SimpleCloud v3 API provider initialized!")
        proxy.pluginManager.registerListener( this, PlayerConnectionListener(playerSynchronizer))

        playerSynchronizer.start()
    }

    override fun onDisable() {
        logger.info("SimpleCloud v3 API provider uninitialized!")
        playerSynchronizer.stop()
    }

}