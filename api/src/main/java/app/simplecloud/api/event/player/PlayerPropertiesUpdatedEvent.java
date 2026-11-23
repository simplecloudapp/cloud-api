package app.simplecloud.api.event.player;

import app.simplecloud.api.player.CloudPlayer;
import java.util.Map;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * Event fired when a player's properties are replaced, merged or deleted.
 */
public interface PlayerPropertiesUpdatedEvent {

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
     * Returns the player's properties before the change.
     *
     * @return immutable map of the previous properties (empty if none)
     */
    Map<String, String> getOldProperties();

    /**
     * Returns the player's properties after the change.
     *
     * @return immutable map of the current properties (empty if none)
     */
    Map<String, String> getProperties();

    /**
     * Returns the player as reported by the controller with this event.
     *
     * @return the player, or null if the controller sent no player data
     */
    @Nullable CloudPlayer getPlayer();

    /**
     * Returns the timestamp when this event occurred (ISO 8601 format).
     *
     * @return the timestamp
     */
    String getTimestamp();
}
