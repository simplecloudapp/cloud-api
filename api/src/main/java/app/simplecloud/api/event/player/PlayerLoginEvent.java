package app.simplecloud.api.event.player;

import app.simplecloud.api.player.CloudPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

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
     * Returns the UUID of the player this event is about.
     *
     * @return the player's UUID
     */
    UUID getPlayerId();

    /**
     * Returns the name of the proxy the player logged in through.
     *
     * @return the proxy name, or null if unavailable
     */
    @Nullable
    String getProxyName();

    /**
     * Returns the ID of the session started by this login.
     *
     * @return the session ID, or null if unavailable
     */
    @Nullable
    String getSessionId();

    /**
     * Returns whether this is the player's first login on this network.
     *
     * @return true for a new player
     */
    boolean isNewPlayer();

    /**
     * Returns whether the player's name changed since their last login.
     *
     * @return true if the name changed
     */
    boolean isNameChanged();

    /**
     * Returns the player as reported by the controller with this event.
     *
     * <p>Contains the full player state at login.
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
