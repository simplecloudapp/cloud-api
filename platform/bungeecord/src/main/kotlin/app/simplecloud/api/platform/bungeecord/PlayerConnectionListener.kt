package app.simplecloud.api.platform.bungeecord

import app.simplecloud.api.platform.shared.PlayerSynchronizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.md_5.bungee.api.event.PlayerDisconnectEvent
import net.md_5.bungee.api.event.PostLoginEvent
import net.md_5.bungee.api.plugin.Listener
import net.md_5.bungee.event.EventHandler

class PlayerConnectionListener(
    private val playerSynchronizer: PlayerSynchronizer
) : Listener {

    @EventHandler
    fun onPlayerJoin(event: PostLoginEvent) {
        CoroutineScope(Dispatchers.Default).launch {
            playerSynchronizer.updatePlayerCount()
        }
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerDisconnectEvent) {
        CoroutineScope(Dispatchers.Default).launch {
            playerSynchronizer.updatePlayerCount()
        }
    }

}