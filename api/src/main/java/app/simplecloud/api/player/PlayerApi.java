package app.simplecloud.api.player;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

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
