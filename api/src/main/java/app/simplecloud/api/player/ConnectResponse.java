package app.simplecloud.api.player;

import org.jetbrains.annotations.Nullable;

/**
 * How a connect through the controller ended, see {@link PlayerApi#connectTo(java.util.UUID, ConnectTarget)}.
 *
 * @param result how the connect ended
 * @param serverName the cloud name of the server the controller last tried to send the player to. It is null
 *                   when the controller tried no server: when the target has no available server
 *                   ({@link CloudPlayer.ConnectResult#NO_SERVER_AVAILABLE}), is unknown, or the player is not
 *                   online.
 */
public record ConnectResponse(CloudPlayer.ConnectResult result, @Nullable String serverName) {
}
