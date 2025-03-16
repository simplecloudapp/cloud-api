package app.simplecloud.api.provider.paper

import org.bukkit.plugin.java.JavaPlugin

class PaperApiProvider : JavaPlugin() {
    override fun onEnable() {
        logger.info("SimpleCloud paper API provider enabled")
    }

    override fun onDisable() {
        logger.warning("SimpleCloud paper API provider disabled")
    }
}