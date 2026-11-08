package app.simplecloud.api.player;

import org.jetbrains.annotations.Nullable;

/**
 * Result of a connect request that went through the controller.
 *
 * @param result the connection result
 * @param serverName the cloud name of the server the controller last tried to send the player to, or null
 *                   when no server was picked (for example {@link CloudPlayer.ConnectResult#NO_SERVER_AVAILABLE}
 *                   or {@link CloudPlayer.ConnectResult#PLAYER_NOT_FOUND})
 */
public record ConnectResponse(CloudPlayer.ConnectResult result, @Nullable String serverName) {}
