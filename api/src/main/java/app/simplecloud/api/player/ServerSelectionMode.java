package app.simplecloud.api.player;

/**
 * Strategy used to pick a server when connecting a player to a server group.
 */
public enum ServerSelectionMode {
    /**
     * Picks a random server of the group.
     */
    RANDOM,

    /**
     * Picks the server with the fewest players.
     */
    MIN_PLAYERS,

    /**
     * Picks the server with the most players.
     */
    MAX_PLAYERS,

    /**
     * Picks the first available server.
     */
    FIRST
}
