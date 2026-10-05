package app.simplecloud.api.player;

import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.LongUnaryOperator;

/**
 * API for querying and managing players connected to the cloud network.
 *
 * <p>Example usage:
 * <pre>{@code
 * PlayerApi players = api.player();
 *
 * // Get a specific player by UUID
 * players.get(uuid).thenAccept(player -> {
 *     if (player != null) {
 *         player.sendMessage(Component.text("Hello!"));
 *     }
 * });
 *
 * // Get all online players
 * players.getOnlinePlayers().thenAccept(allPlayers -> {
 *     System.out.println("Online players: " + allPlayers.size());
 * });
 *
 * // Get players on a specific server
 * players.getOnlinePlayers("lobby-1").thenAccept(lobbyPlayers -> {
 *     lobbyPlayers.forEach(p -> p.sendMessage(Component.text("Welcome to lobby!")));
 * });
 *
 * // Top 10 players by online time
 * players.list(PlayerQuery.create()
 *         .sortBy(PlayerQuery.SortField.ONLINE_TIME)
 *         .sortOrder(PlayerQuery.SortOrder.DESC)
 *         .limit(10))
 *     .thenAccept(top -> top.forEach(p -> System.out.println(p.getName())));
 * }</pre>
 */
public interface PlayerApi {

    /**
     * Gets a player by their unique ID.
     *
     * @param uniqueId the player's UUID
     * @return a future containing the player, or null if not found
     */
    CompletableFuture<CloudPlayer> get(UUID uniqueId);

    /**
     * Gets a player by their username.
     *
     * @param name the player's username (case-insensitive)
     * @return a future containing the player, or null if not found
     */
    CompletableFuture<CloudPlayer> get(String name);

    /**
     * Gets all players currently online on the network.
     *
     * @return a future containing a list of all online players
     */
    CompletableFuture<List<CloudPlayer>> getOnlinePlayers();

    /**
     * Gets all players currently connected to the given server.
     *
     * @param serverName the server name, or null for all online players
     * @return a future containing a list of online players on that server
     */
    CompletableFuture<List<CloudPlayer>> getOnlinePlayers(@Nullable String serverName);

    /**
     * Lists known players (online and offline) with filtering, sorting and pagination.
     *
     * <p>Without an explicit {@link PlayerQuery#limit(int)} the controller returns at most 50 players.
     *
     * @param query the query, or null for the controller defaults
     * @return a future containing the matching players
     */
    CompletableFuture<List<CloudPlayer>> list(@Nullable PlayerQuery query);

    /**
     * Gets the total number of players online on the network.
     *
     * @return a future containing the online player count
     */
    CompletableFuture<Integer> getOnlinePlayerCount();

    /**
     * Gets the accumulated online time for a player in seconds.
     *
     * @param uniqueId the player's UUID
     * @return a future containing the accumulated online time in seconds
     */
    CompletableFuture<Long> getOnlineTimeSeconds(UUID uniqueId);

    /**
     * Sets the accumulated online time for a player.
     *
     * @param uniqueId the player's UUID
     * @param seconds the new accumulated online time in seconds
     * @return a future containing the updated player
     */
    CompletableFuture<CloudPlayer> setOnlineTimeSeconds(UUID uniqueId, long seconds);

    /**
     * Resets the accumulated online time for a player.
     *
     * @param uniqueId the player's UUID
     * @return a future containing the updated player
     */
    default CompletableFuture<CloudPlayer> resetOnlineTime(UUID uniqueId) {
        return setOnlineTimeSeconds(uniqueId, 0L);
    }

    /**
     * Updates the accumulated online time by reading the current value, applying
     * the updater, and writing the resulting value.
     *
     * <p>This is a client-side read-modify-write helper. Callers that need
     * strict atomicity should prefer a controller-side delta endpoint when one
     * is available.
     *
     * @param uniqueId the player's UUID
     * @param updater function receiving the current online time in seconds
     * @return a future containing the updated player
     */
    default CompletableFuture<CloudPlayer> updateOnlineTimeSeconds(UUID uniqueId, LongUnaryOperator updater) {
        if (updater == null) {
            throw new IllegalArgumentException("updater must not be null");
        }
        return getOnlineTimeSeconds(uniqueId).thenCompose(current -> setOnlineTimeSeconds(uniqueId, updater.applyAsLong(current)));
    }

    /**
     * Adds seconds to the accumulated online time for a player.
     *
     * @param uniqueId the player's UUID
     * @param seconds the seconds to add
     * @return a future containing the updated player
     */
    default CompletableFuture<CloudPlayer> addOnlineTimeSeconds(UUID uniqueId, long seconds) {
        if (seconds < 0) {
            throw new IllegalArgumentException("seconds must be >= 0");
        }
        return updateOnlineTimeSeconds(uniqueId, current -> Math.addExact(current, seconds));
    }

    /**
     * Removes seconds from the accumulated online time for a player.
     *
     * <p>The resulting online time is clamped to zero.
     *
     * @param uniqueId the player's UUID
     * @param seconds the seconds to remove
     * @return a future containing the updated player
     */
    default CompletableFuture<CloudPlayer> removeOnlineTimeSeconds(UUID uniqueId, long seconds) {
        if (seconds < 0) {
            throw new IllegalArgumentException("seconds must be >= 0");
        }
        return updateOnlineTimeSeconds(uniqueId, current -> current <= seconds ? 0L : current - seconds);
    }

    /**
     * Resets the first seen timestamp of a player to now.
     *
     * @param uniqueId the player's UUID
     * @return a future containing the updated player
     */
    CompletableFuture<CloudPlayer> resetFirstSeen(UUID uniqueId);

    /**
     * Resets the last seen timestamp of a player to now.
     *
     * @param uniqueId the player's UUID
     * @return a future containing the updated player
     */
    CompletableFuture<CloudPlayer> resetLastSeen(UUID uniqueId);

    /**
     * Gets all custom properties of a player.
     *
     * @param uniqueId the player's UUID
     * @return a future containing the properties (empty if none)
     */
    CompletableFuture<Map<String, String>> getPlayerProperties(UUID uniqueId);

    /**
     * Replaces all custom properties of a player.
     *
     * <p>Keys not contained in {@code properties} are removed. Use
     * {@link #updatePlayerProperties(UUID, Map)} to merge instead.
     *
     * @param uniqueId the player's UUID
     * @param properties the new properties
     * @return a future containing the stored properties
     */
    CompletableFuture<Map<String, String>> setPlayerProperties(UUID uniqueId, Map<String, String> properties);

    /**
     * Updates player properties by merging with existing properties.
     *
     * @param uniqueId the player's UUID
     * @param properties the properties to merge
     * @return a future containing the updated properties
     */
    CompletableFuture<Map<String, String>> updatePlayerProperties(UUID uniqueId, Map<String, String> properties);

    /**
     * Updates a single player property by merging it with existing properties.
     *
     * @param uniqueId the player's UUID
     * @param key the property key to update
     * @param value the property value to set
     * @return a future containing the updated properties
     */
    default CompletableFuture<Map<String, String>> updatePlayerProperty(UUID uniqueId, String key, String value) {
        return updatePlayerProperties(uniqueId, Map.of(key, value));
    }

    /**
     * Deletes specific property keys from a player.
     *
     * @param uniqueId the player's UUID
     * @param keys the property keys to delete
     * @return a future containing the remaining properties
     */
    CompletableFuture<Map<String, String>> deletePlayerProperties(UUID uniqueId, List<String> keys);

    /**
     * Deletes a single property key from a player.
     *
     * @param uniqueId the player's UUID
     * @param key the property key to delete
     * @return a future containing the remaining properties
     */
    default CompletableFuture<Map<String, String>> deletePlayerProperty(UUID uniqueId, String key) {
        return deletePlayerProperties(uniqueId, List.of(key));
    }

    /**
     * Lists the recorded profiles (name and skin history) of a player, newest first.
     *
     * @param uniqueId the player's UUID
     * @param limit the page size
     * @param offset the number of profiles to skip
     * @return a future containing the requested profiles
     */
    CompletableFuture<List<PlayerProfile>> getProfiles(UUID uniqueId, int limit, int offset);

    /**
     * Lists the first 50 recorded profiles of a player, newest first.
     *
     * @param uniqueId the player's UUID
     * @return a future containing the profiles
     */
    default CompletableFuture<List<PlayerProfile>> getProfiles(UUID uniqueId) {
        return getProfiles(uniqueId, 50, 0);
    }

    /**
     * Gets the most recent profile of a player.
     *
     * @param uniqueId the player's UUID
     * @return a future containing the profile, or null if none was recorded
     */
    CompletableFuture<PlayerProfile> getLatestProfile(UUID uniqueId);

    /**
     * Lists the sessions of a player, newest first.
     *
     * @param uniqueId the player's UUID
     * @param limit the page size
     * @param offset the number of sessions to skip
     * @return a future containing the requested sessions
     */
    CompletableFuture<List<PlayerSession>> getSessions(UUID uniqueId, int limit, int offset);

    /**
     * Lists the first 50 sessions of a player, newest first.
     *
     * @param uniqueId the player's UUID
     * @return a future containing the sessions
     */
    default CompletableFuture<List<PlayerSession>> getSessions(UUID uniqueId) {
        return getSessions(uniqueId, 50, 0);
    }

    /**
     * Gets the active session of a player including its client settings.
     *
     * @param uniqueId the player's UUID
     * @return a future containing the session, or null if the player is offline
     */
    CompletableFuture<PlayerSession> getCurrentSession(UUID uniqueId);

    /**
     * Gets the client settings of a player's active session.
     *
     * @param uniqueId the player's UUID
     * @return a future containing the settings, or null if unavailable
     */
    CompletableFuture<PlayerClientSettings> getClientSettings(UUID uniqueId);

    /**
     * Updates the stored client settings of a player's active session.
     *
     * <p>This only changes what the cloud stores; it does not change the settings of the client itself.
     *
     * @param uniqueId the player's UUID
     * @param request the fields to change
     * @return a future containing the updated settings
     */
    CompletableFuture<PlayerClientSettings> updateClientSettings(UUID uniqueId, UpdateClientSettingsRequest request);

    /**
     * Kicks a player from the network.
     *
     * <p>The request is routed through the controller.
     *
     * @param uniqueId the player's UUID
     * @param reason the kick reason, or null for none
     * @return a future containing true if the proxy kicked the player, false if the
     *         player is not online or the kick failed
     */
    CompletableFuture<Boolean> kick(UUID uniqueId, @Nullable Component reason);

    /**
     * Connects a player to a specific server.
     *
     * @param uniqueId the player's UUID
     * @param serverName the name of the target server
     * @return a future containing the connection result
     */
    CompletableFuture<CloudPlayer.ConnectResult> connect(UUID uniqueId, String serverName);

    /**
     * Connects a player to a server of the given server group.
     *
     * @param uniqueId the player's UUID
     * @param groupName the name of the target server group
     * @param selectionMode how the target server is chosen
     * @return a future containing the connection result
     */
    CompletableFuture<CloudPlayer.ConnectResult> connectToGroup(UUID uniqueId, String groupName, ServerSelectionMode selectionMode);

    /**
     * Connects a player to a random server of the given server group.
     *
     * @param uniqueId the player's UUID
     * @param groupName the name of the target server group
     * @return a future containing the connection result
     */
    default CompletableFuture<CloudPlayer.ConnectResult> connectToGroup(UUID uniqueId, String groupName) {
        return connectToGroup(uniqueId, groupName, ServerSelectionMode.RANDOM);
    }

    /**
     * Connects a player to a persistent server.
     *
     * @param uniqueId the player's UUID
     * @param persistentServerId the ID of the target persistent server
     * @return a future containing the connection result
     */
    CompletableFuture<CloudPlayer.ConnectResult> connectToPersistentServer(UUID uniqueId, String persistentServerId);
}
