package app.simplecloud.api.event.player;

import app.simplecloud.api.player.PlayerProfile;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Event fired when a player's Minecraft profile (name or skin) changes.
 */
public interface PlayerProfileChangedEvent {

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
     * Returns the profile before the change.
     *
     * @return the old profile, or null if the player had none
     */
    @Nullable
    PlayerProfile getOldProfile();

    /**
     * Returns the profile after the change.
     *
     * @return the new profile, or null if unavailable
     */
    @Nullable
    PlayerProfile getNewProfile();

    /**
     * Returns the timestamp when this event occurred (ISO 8601 format).
     *
     * @return the timestamp
     */
    String getTimestamp();
}
