package app.simplecloud.api.platform.velocity

import app.simplecloud.api.platform.shared.PlayerSynchronizer
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.DisconnectEvent
import com.velocitypowered.api.event.connection.PostLoginEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PlayerConnectionListener(
    private val playerSynchronizer: PlayerSynchronizer
) {

    @Subscribe
    fun onPlayerJoin(event: PostLoginEvent) {
        CoroutineScope(Dispatchers.IO).launch {
            playerSynchronizer.updatePlayerCount()
        }
    }

    @Subscribe
    fun onPlayerQuit(event: DisconnectEvent) {
        CoroutineScope(Dispatchers.IO).launch {
            playerSynchronizer.updatePlayerCount()
        }
    }

}