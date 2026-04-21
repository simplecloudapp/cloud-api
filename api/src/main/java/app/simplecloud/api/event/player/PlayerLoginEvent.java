package app.simplecloud.api.event.player;

/**
 * Event fired when a player logs into the network.
 */
public interface PlayerLoginEvent {

    /**
     * Returns the network ID this event belongs to.
     *
     * @return the network ID
     */
    String getNetworkId();

    /**
     * Returns the unique ID of the player.
     *
     * @return the player's UUID
     */
    String getPlayerId();

    /**
     * Returns the ID of the proxy server the player connected through.
     *
     * @return the proxy server ID
     */
    String getProxyServerId();

    /**
     * Returns the session ID for this login.
     *
     * @return the session ID
     */
    String getSessionId();

    /**
     * Returns whether this is the player's first time on this network.
     *
     * @return true if the player is new to this network
     */
    boolean isNewPlayer();

    /**
     * Returns whether the player's name changed since their last login.
     *
     * @return true if the player's name changed
     */
    boolean isNameChanged();

    /**
     * Returns the timestamp when this event occurred (ISO 8601 format).
     *
     * @return the timestamp
     */
    String getTimestamp();

}
