package app.simplecloud.api.player;

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
 * players.getAll().thenAccept(allPlayers -> {
 *     System.out.println("Online players: " + allPlayers.size());
 * });
 *
 * // Get players on a specific server
 * players.getOnServer("lobby-1").thenAccept(lobbyPlayers -> {
 *     lobbyPlayers.forEach(p -> p.sendMessage(Component.text("Welcome to lobby!")));
 * });
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
}
