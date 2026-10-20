package app.simplecloud.api.event.player;

import app.simplecloud.api.player.CloudPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Event fired when a player switches to another server.
 */
public interface PlayerServerSwitchEvent {

    /**
     * Returns the network ID this event belongs to.
     *
     * @return the network ID
     */
    String getNetworkId();

    /**
     * Returns the UUID of the player this event is about.
     *
     * @return the player's UUID
     */
    UUID getPlayerId();

    /**
     * Returns the name of the server the player left.
     *
     * @return the previous server name, or null if the player had no previous server
     */
    @Nullable
    String getPreviousServerName();

    /**
     * Returns the name of the server the player joined.
     *
     * @return the new server name
     */
    String getNewServerName();

    /**
     * Returns the player as reported by the controller with this event.
     *
     * @return the player, or null if the controller sent no player data
     */
    @Nullable
    CloudPlayer getPlayer();

    /**
     * Returns the timestamp when this event occurred (ISO 8601 format).
     *
     * @return the timestamp
     */
    String getTimestamp();
}
