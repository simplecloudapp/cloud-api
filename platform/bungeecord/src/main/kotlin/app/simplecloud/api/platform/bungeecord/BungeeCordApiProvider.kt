package app.simplecloud.api.platform.bungeecord

import net.md_5.bungee.api.plugin.Plugin

class BungeeCordApiProvider : Plugin() {
    override fun onEnable() {
        logger.info("SimpleCloud v3 API provider initialized!")
    }

    override fun onDisable() {
        logger.info("SimpleCloud v3 API provider uninitialized!")
    }

}