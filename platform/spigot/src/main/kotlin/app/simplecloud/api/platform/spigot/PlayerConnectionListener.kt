package app.simplecloud.api.platform.spigot

import app.simplecloud.api.platform.shared.PlayerSynchronizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent

class PlayerConnectionListener(
    private val playerSynchronizer: PlayerSynchronizer
) : Listener {

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        CoroutineScope(Dispatchers.Default).launch {
            playerSynchronizer.updatePlayerCount()
        }
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        CoroutineScope(Dispatchers.Default).launch {
            playerSynchronizer.updatePlayerCount()
        }
    }

}