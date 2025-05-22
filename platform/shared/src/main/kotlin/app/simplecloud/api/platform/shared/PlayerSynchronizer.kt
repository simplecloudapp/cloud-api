package app.simplecloud.api.platform.shared

import app.simplecloud.controller.api.ControllerApi
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.time.Duration.Companion.seconds

class PlayerSynchronizer(
    private val controllerApi: ControllerApi.Coroutine,
    private val getCurrentOnlineCount: () -> Long,
) {

    private val currentServerId = System.getenv("SIMPLECLOUD_UNIQUE_ID")

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val updateFlow = MutableSharedFlow<Unit>()

    @OptIn(FlowPreview::class)
    fun start() {
        scope.launch {
            controllerApi.getServers().updateServerProperty(currentServerId, "player-count-ping", "skip")
        }

        scope.launch {
            updateFlow
                .debounce(800)
                .collect {
                    val currentServer = controllerApi.getServers().getCurrentServer().copy(
                        playerCount = getCurrentOnlineCount()
                    )
                    controllerApi.getServers().updateServer(currentServer)
                    println("Updated server player count to ${currentServer.playerCount}")
                }
        }

        scope.launch {
            while (isActive) {
                updatePlayerCount()
                delay(15.seconds)
            }
        }
    }

    suspend fun updatePlayerCount() {
        updateFlow.emit(Unit)
    }

    fun stop() {
        scope.cancel()
    }

}
