package app.simplecloud.api.player;

/**
 * Strategy used to pick a server when connecting a player to a server group.
 *
 * <p>Only servers a player can join are considered. Player counts are the counts the servers last reported.
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
     * Picks the server with the lowest numerical ID.
     */
    FIRST
}
