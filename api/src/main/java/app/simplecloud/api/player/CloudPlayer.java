package app.simplecloud.api.player;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Represents a player connected to the cloud network.
 *
 * <p>CloudPlayer extends Adventure's {@link Audience}, allowing you to send
 * messages, titles, sounds, and other content directly to the player regardless
 * of which server they are connected to.
 *
 * <p>Example usage:
 * <pre>{@code
 * CloudPlayer player = api.player().get(uuid).join();
 *
 * // Send a message (inherited from Audience)
 * player.sendMessage(Component.text("Hello!"));
 *
 * // Show a title
 * player.showTitle(Title.title(
 *     Component.text("Welcome"),
 *     Component.text("to the server")
 * ));
 *
 * // Connect to another server
 * player.connect("lobby-1").thenAccept(result -> {
 *     if (result == ConnectResult.SUCCESS) {
 *         System.out.println("Player connected!");
 *     }
 * });
 *
 * // Kick the player
 * player.kick(Component.text("Goodbye!"));
 * }</pre>
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
     * @return immutable map of properties (empty if none)
     */
    Map<String, String> getProperties();

    /**
     * Kicks the player from the network with the specified reason.
     *
     * @param reason the kick reason displayed to the player
     * @return a future that completes when the kick is processed
     */
    CompletableFuture<Void> kick(Component reason);

    /**
     * Connects the player to a server by its cloud name, through the controller.
     *
     * <p>See {@link PlayerApi#connect(UUID, String)} for the name, the results and the failures.
     * To reach servers that are not managed by the cloud, use {@link #connectDirect(String)}.
     *
     * @param serverName the cloud name of the target server
     * @return a future containing the connection result
     */
    CompletableFuture<ConnectResult> connect(String serverName);

    /**
     * Connects the player to a server by its ID, through the controller.
     *
     * <p>See {@link PlayerApi#connectByServerId(UUID, String)}.
     *
     * @param serverId the UUID of the target server
     * @return a future containing the connection result
     */
    CompletableFuture<ConnectResult> connectByServerId(String serverId);

    /**
     * Connects the player to a server by the name the proxy registered it under.
     *
     * <p>The request goes straight to the player's proxy, without the controller. It works for any server
     * the proxy knows, including servers that are not managed by the cloud.
     *
     * @param serverName the name of the server on the proxy
     * @return a future containing the connection result
     */
    CompletableFuture<ConnectResult> connectDirect(String serverName);

    /**
     * Connects the player to a server of the given server group, through the controller.
     *
     * <p>See {@link PlayerApi#connectToGroup(UUID, String, ServerSelectionMode)}.
     *
     * @param groupName the name of the target server group, matched case-insensitively
     * @param selectionMode how the target server is chosen, or null for {@link ServerSelectionMode#RANDOM}
     * @return a future containing the connection result
     */
    CompletableFuture<ConnectResult> connectToGroup(String groupName, ServerSelectionMode selectionMode);

    /**
     * Connects the player to a random server of the given server group.
     *
     * @param groupName the name of the target server group
     * @return a future containing the connection result
     */
    default CompletableFuture<ConnectResult> connectToGroup(String groupName) {
        return connectToGroup(groupName, ServerSelectionMode.RANDOM);
    }

    /**
     * Connects the player to a persistent server by its ID, through the controller.
     *
     * <p>See {@link PlayerApi#connectToPersistentServer(UUID, String)}.
     *
     * @param persistentServerId the UUID of the target persistent server
     * @return a future containing the connection result
     */
    CompletableFuture<ConnectResult> connectToPersistentServer(String persistentServerId);

    /**
     * Result of a player connection attempt.
     */
    enum ConnectResult {
        /**
         * The player was successfully connected to the server.
         */
        SUCCESS,

        /**
         * The target does not exist, or the proxy does not know the server. A proxy does not know a
         * server it has not registered yet. For connects through the controller, it knows no server at
         * all when no plugin maps server IDs to its server names; see {@link PlayerApi#connect(UUID, String)}.
         */
        SERVER_NOT_FOUND,

        /**
         * The player is already connected to the target server.
         */
        ALREADY_CONNECTED,

        /**
         * The player was not found (may have disconnected).
         */
        PLAYER_NOT_FOUND,

        /**
         * The connection failed for an unknown reason.
         */
        CONNECTION_FAILED,

        /**
         * The target exists, but has no game server in state {@code AVAILABLE}, for example a server
         * that is still starting. The player was not sent anywhere. Only connects through the controller
         * answer this; see {@link PlayerApi#connect(UUID, String)}.
         */
        NO_SERVER_AVAILABLE
    }
}
