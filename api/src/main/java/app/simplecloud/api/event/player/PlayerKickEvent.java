package app.simplecloud.api.event.player;

import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Event fired when a player is kicked from the network.
 */
public interface PlayerKickEvent {

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
     * Returns the ID of the server the player was kicked from.
     *
     * @return the server ID
     */
    String getServerId();

    /**
     * Returns the kick reason displayed to the player.
     *
     * @return the kick reason, or null if none was provided
     */
    @Nullable Component getKickReason();

    /**
     * Returns the timestamp when this event occurred (ISO 8601 format).
     *
     * @return the timestamp
     */
    String getTimestamp();

}
