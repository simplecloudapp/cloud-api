package app.simplecloud.api.provider.spigot

import org.bukkit.plugin.java.JavaPlugin

class SpigotApiProvider : JavaPlugin() {

    override fun onEnable() {
        logger.info("SimpleCloud spigot API provider enabled")
    }

    override fun onDisable() {
        logger.warning("SimpleCloud spigot API provider disabled")
    }

}