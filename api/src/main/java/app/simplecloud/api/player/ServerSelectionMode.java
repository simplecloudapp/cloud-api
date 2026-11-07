package app.simplecloud.api.player;

/**
 * Strategy used to pick a server when connecting a player to a server group.
 *
 * <p>Only servers in state {@link app.simplecloud.api.server.ServerState#AVAILABLE} are considered; full
 * servers are not skipped. Player counts are the counts the servers last reported. If the proxy does not
 * know the chosen server, the controller tries other candidates.
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
