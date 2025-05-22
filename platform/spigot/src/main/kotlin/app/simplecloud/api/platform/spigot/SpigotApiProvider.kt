package app.simplecloud.api.platform.spigot

import app.simplecloud.api.platform.shared.PlayerSynchronizer
import app.simplecloud.controller.api.ControllerApi
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin

class SpigotApiProvider : JavaPlugin() {

    private val controllerApi = ControllerApi.createCoroutineApi()
    private val playerSynchronizer = PlayerSynchronizer(controllerApi) { Bukkit.getOnlinePlayers().size.toLong() }

    override fun onEnable() {
        logger.info("SimpleCloud v3 API provider initialized!")
        Bukkit.getPluginManager().registerEvents(PlayerConnectionListener(playerSynchronizer), this)

        playerSynchronizer.start()
    }

    override fun onDisable() {
        logger.info("SimpleCloud v3 API provider uninitialized!")
        playerSynchronizer.stop()
    }

}