package app.simplecloud.api.player;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

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
 * player.connectTo(ConnectTarget.groupName("lobby")).thenAccept(outcome -> {
 *     if (outcome.isConnected()) {
 *         System.out.println("Player is on " + outcome.getServerName());
 *     }
 * });
 *
 * // Kick the player
 * player.kick(Component.text("Goodbye!"));
 * }</pre>
 */
@ApiStatus.NonExtendable
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
     * Moves the player to a server.
     *
     * <p>Every target except {@link ConnectTarget#registeredServerName(String)} needs a plugin on the
     * player's proxy that maps server IDs to its server names, such as server-connection {@code 1.2.0} or
     * newer. See {@link PlayerApi#connectTo(UUID, ConnectTarget)} for how each target is sent and how the
     * future fails.
     *
     * @param target where to send the player
     * @return a future containing the outcome
     * @throws NullPointerException if the target is null
     */
    CompletableFuture<ConnectOutcome> connectTo(ConnectTarget target);

    /**
     * Connects the player to a server by the name the proxy registered it under, without the controller.
     *
     * @param serverName the name of the server on the proxy
     * @return a future containing the connection result
     * @throws NullPointerException if the server name is null
     * @deprecated Use {@link #connectTo(ConnectTarget)} with {@link ConnectTarget#registeredServerName(String)}.
     */
    @Deprecated
    CompletableFuture<ConnectResult> connectDirect(String serverName);

    /**
     * Connects the player to a server by the name the proxy registered it under, exactly like
     * {@link #connectDirect(String)}.
     *
     * @param serverName the name of the server on the proxy
     * @return a future containing the connection result
     * @throws NullPointerException if the server name is null
     * @deprecated Use {@link #connectTo(ConnectTarget)} with {@link ConnectTarget#registeredServerName(String)}
     * for the proxy's name, or with {@link ConnectTarget#serverName(String)} for the cloud name.
     */
    @Deprecated
    CompletableFuture<ConnectResult> connect(String serverName);

    /**
     * Connects the player to a server of the group with the given name, through the controller.
     *
     * @param groupName the name of the target server group
     * @param selectionMode how the target server is chosen, or null for {@link ServerSelectionMode#RANDOM}
     * @return a future containing the connection result
     * @throws NullPointerException if the group name is null
     * @deprecated Use {@link #connectTo(ConnectTarget)} with {@link ConnectTarget#groupName(String, ServerSelectionMode)}.
     */
    @Deprecated
    CompletableFuture<ConnectResult> connectToGroup(String groupName, @Nullable ServerSelectionMode selectionMode);

    /**
     * Connects the player to a random server of the group with the given name, through the controller.
     *
     * @param groupName the name of the target server group
     * @return a future containing the connection result
     * @throws NullPointerException if the group name is null
     * @deprecated Use {@link #connectTo(ConnectTarget)} with {@link ConnectTarget#groupName(String)}.
     */
    @Deprecated
    default CompletableFuture<ConnectResult> connectToGroup(String groupName) {
        return connectToGroup(groupName, ServerSelectionMode.RANDOM);
    }

    /**
     * Connects the player to the running server of a persistent server, through the controller.
     *
     * @param persistentServerId the UUID of the target persistent server
     * @return a future containing the connection result
     * @throws NullPointerException if the persistent server ID is null
     * @deprecated Use {@link #connectTo(ConnectTarget)} with {@link ConnectTarget#persistentServerId(String)}.
     */
    @Deprecated
    CompletableFuture<ConnectResult> connectToPersistentServer(String persistentServerId);

    /**
     * Result of a connect from the deprecated connect methods. {@link #connectTo(ConnectTarget)} returns a
     * {@link ConnectOutcome} with a {@link ConnectStatus} instead. This enum gets no new values.
     */
    enum ConnectResult {
        /**
         * The player was successfully connected to the server.
         */
        SUCCESS,

        /**
         * The target server was not found. The deprecated methods also return it when the target has no
         * available server, or when the player's proxy doesn't know the server.
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
        CONNECTION_FAILED
    }
}
