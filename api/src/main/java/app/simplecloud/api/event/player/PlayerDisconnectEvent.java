package app.simplecloud.api.event.player;

/**
 * Event fired when a player disconnects from the network.
 */
public interface PlayerDisconnectEvent {

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
     * Returns the ID of the proxy server the player was connected through.
     *
     * @return the proxy server ID
     */
    String getProxyServerId();

    /**
     * Returns the ID of the server the player was on when they disconnected.
     *
     * @return the server ID
     */
    String getServerId();

    /**
     * Returns the duration of the session in seconds.
     *
     * @return the session duration in seconds
     */
    long getSessionDurationSeconds();

    /**
     * Returns the timestamp when this event occurred (ISO 8601 format).
     *
     * @return the timestamp
     */
    String getTimestamp();

}
