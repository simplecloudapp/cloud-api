package app.simplecloud.api.event.player;

import app.simplecloud.api.player.CloudPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

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
     * Returns the UUID of the player this event is about.
     *
     * @return the player's UUID
     */
    UUID getPlayerId();

    /**
     * Returns the name of the proxy the player was connected through.
     *
     * @return the proxy name, or null if unavailable
     */
    @Nullable
    String getProxyName();

    /**
     * Returns the name of the server the player was on when disconnecting.
     *
     * @return the server name, or null if unavailable
     */
    @Nullable
    String getServerName();

    /**
     * Returns how long the ended session lasted.
     *
     * @return the session duration in seconds, or 0 if unavailable
     */
    long getSessionDurationSeconds();

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
