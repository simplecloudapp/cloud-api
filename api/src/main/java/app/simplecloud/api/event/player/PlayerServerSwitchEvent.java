package app.simplecloud.api.event.player;

/**
 * Event fired when a player switches servers within the network.
 */
public interface PlayerServerSwitchEvent {

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
     * Returns the ID of the server the player switched from.
     *
     * @return the previous server ID
     */
    String getPreviousServerId();

    /**
     * Returns the ID of the server the player switched to.
     *
     * @return the new server ID
     */
    String getNewServerId();

    /**
     * Returns the timestamp when this event occurred (ISO 8601 format).
     *
     * @return the timestamp
     */
    String getTimestamp();

}
