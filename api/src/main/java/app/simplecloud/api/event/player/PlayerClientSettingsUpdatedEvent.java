package app.simplecloud.api.event.player;

import app.simplecloud.api.player.PlayerClientSettings;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Event fired when a player's client settings are updated.
 */
public interface PlayerClientSettingsUpdatedEvent {

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
     * Returns the ID of the session the settings belong to.
     *
     * @return the session ID, or null if unavailable
     */
    @Nullable
    String getSessionId();

    /**
     * Returns the updated client settings.
     *
     * @return the settings, or null if unavailable
     */
    @Nullable
    PlayerClientSettings getSettings();

    /**
     * Returns the timestamp when this event occurred (ISO 8601 format).
     *
     * @return the timestamp
     */
    String getTimestamp();
}
