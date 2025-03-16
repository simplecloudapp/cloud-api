package app.simplecloud.api.platform.spigot

import org.bukkit.plugin.java.JavaPlugin

class SpigotApiProvider : JavaPlugin() {

    override fun onEnable() {
        logger.info("SimpleCloud v3 API provider initialized!")
    }

    override fun onDisable() {
        logger.info("SimpleCloud v3 API provider uninitialized!")
    }

}