package app.simplecloud.api.player;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Represents a player connected to the network.
 *
 * <p>CloudPlayer extends Adventure's {@link Audience}, allowing you to send
 * messages, titles, sounds, and other content directly to the player regardless
 * of which server they are connected to. These calls are forwarded to the player's proxy.
 *
 */
public interface CloudPlayer extends Audience {

    /**
     * Returns the player's unique ID.
     *
     * @return the player's UUID
     */
    UUID getUniqueId();

    /**
     * Returns the player's username.
     *
     * @return the player's name
     */
    String getName();

    /**
     * Returns the player's display name.
     *
     * @return the display name
     */
    String getDisplayName();

    /**
     * Returns the name of the proxy the player is connected through.
     *
     * @return the proxy name, or null if unavailable
     */
    String getConnectedProxyName();

    /**
     * Returns the name of the server the player is currently on.
     *
     * @return the server name, or null if not connected to a backend server
     */
    String getConnectedServerName();

    /**
     * Returns whether the player is currently online.
     *
     * @return true if online
     */
    boolean isOnline();

    /**
     * Returns accumulated online time in seconds.
     *
     * @return total online time in seconds
     */
    long getOnlineTimeSeconds();

    /**
     * Returns the active session ID when online.
     *
     * @return session ID, or null if unavailable
     */
    String getSessionId();

    /**
     * Returns the first seen timestamp as returned by the controller.
     *
     * @return ISO-8601 timestamp string, or null if unavailable
     */
    String getFirstSeen();

    /**
     * Returns the last seen timestamp as returned by the controller.
     *
     * @return ISO-8601 timestamp string, or null if unavailable
     */
    String getLastSeen();

    /**
     * Returns custom player properties.
     *
     * <p>To read or change the current properties, use {@link PlayerApi#getPlayerProperties(UUID)} and the related methods.
     *
     * @return immutable map of properties (empty if none)
     */
    Map<String, String> getProperties();

    /**
     * Kicks the player from the network with the specified reason.
     *
     * @param reason the kick reason displayed to the player
     * @return a future that completes when the kick is processed, or completes exceptionally with a
     *         {@link app.simplecloud.api.CloudApiException} if the player could not be kicked
     */
    CompletableFuture<Void> kick(Component reason);

    /**
     * Connects the player through the controller, which resolves the target to one server.
     *
     * <p>See {@link PlayerApi#connectTo(UUID, ConnectTarget)} for when the future completes exceptionally.
     *
     * @param target where to send the player
     * @return a future containing the result and the server the controller last tried
     */
    CompletableFuture<ConnectResponse> connectTo(ConnectTarget target);

    /**
     * Connects the player to the specified server by its cloud name.
     *
     * <p>The name is a persistent server's name or {@code <group>-<numerical id>}, matched
     * case-insensitively. Server UUIDs are not accepted, use {@link #connectToServerById(String)}.
     * To reach servers that are not managed by the cloud, use {@link #connectDirect(String)}.
     * See {@link PlayerApi#connectTo(UUID, ConnectTarget)} for when the future completes exceptionally.
     *
     * @param serverName the cloud name of the server to connect to
     * @return a future containing the connection result
     */
    default CompletableFuture<ConnectResult> connectToServerByName(String serverName) {
        return connectTo(new ConnectTarget.ServerName(serverName)).thenApply(ConnectResponse::result);
    }

    /**
     * Connects the player to the specified server by its ID.
     *
     * <p>See {@link PlayerApi#connectTo(UUID, ConnectTarget)} for when the future completes exceptionally.
     *
     * @param serverId the UUID of the server to connect to
     * @return a future containing the connection result
     */
    default CompletableFuture<ConnectResult> connectToServerById(String serverId) {
        return connectTo(new ConnectTarget.ServerId(serverId)).thenApply(ConnectResponse::result);
    }

    /**
     * Connects the player to a server by the name the proxy registered it under.
     *
     * <p>This goes to the player's proxy directly, without the controller, and never completes
     * exceptionally.
     *
     * @param serverName the name of the server on the proxy
     * @return a future containing the connection result
     */
    CompletableFuture<ConnectResult> connectDirect(String serverName);

    /**
     * Connects the player to a server of the given server group.
     *
     * <p>See {@link PlayerApi#connectTo(UUID, ConnectTarget)} for when the future completes exceptionally.
     *
     * @param groupName the name of the target server group, matched case-insensitively
     * @param selectionMode how the target server is chosen, or null for {@link ServerSelectionMode#RANDOM}
     * @return a future containing the connection result
     */
    default CompletableFuture<ConnectResult> connectToGroupByName(
            String groupName, @Nullable ServerSelectionMode selectionMode) {
        return connectTo(new ConnectTarget.GroupName(groupName, selectionMode)).thenApply(ConnectResponse::result);
    }

    /**
     * Connects the player to a random server of the given server group.
     *
     * @param groupName the name of the target server group, matched case-insensitively
     * @return a future containing the connection result
     */
    default CompletableFuture<ConnectResult> connectToGroupByName(String groupName) {
        return connectToGroupByName(groupName, ServerSelectionMode.RANDOM);
    }

    /**
     * Connects the player to a persistent server.
     *
     * <p>To connect by the persistent server's name, use {@link #connectToServerByName(String)}.
     * See {@link PlayerApi#connectTo(UUID, ConnectTarget)} for when the future completes exceptionally.
     *
     * @param persistentServerId the UUID of the target persistent server
     * @return a future containing the connection result
     */
    default CompletableFuture<ConnectResult> connectToPersistentServerById(String persistentServerId) {
        return connectTo(new ConnectTarget.PersistentServerId(persistentServerId))
                .thenApply(ConnectResponse::result);
    }

    /**
     * Result of a player connection attempt.
     */
    enum ConnectResult {
        /**
         * The player was successfully connected to the server.
         */
        SUCCESS,

        /**
         * The target server was not found: the cloud does not know it, or the proxy has not registered it yet.
         */
        SERVER_NOT_FOUND,

        /**
         * The target exists, but has no server a player can join right now.
         */
        NO_SERVER_AVAILABLE,

        /**
         * The player is already connected to the target server.
         */
        ALREADY_CONNECTED,

        /**
         * The player was not found (may have disconnected).
         */
        PLAYER_NOT_FOUND,

        /**
         * The connection failed for an unknown reason, or the answer contained a result
         * this API version doesn't know.
         */
        CONNECTION_FAILED
    }
}
