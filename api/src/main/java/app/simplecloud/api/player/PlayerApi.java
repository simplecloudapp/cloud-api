package app.simplecloud.api.player;

import java.util.List;
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
}
