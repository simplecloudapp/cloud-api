package app.simplecloud.api.event.player;

import app.simplecloud.api.player.CloudPlayer;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Event fired when a player is kicked from the network through the cloud
 * ({@link app.simplecloud.api.player.PlayerApi#kick} or {@link CloudPlayer#kick}).
 *
 * <p>Kicks made by a proxy or server plugin directly do not fire this event. The event is published
 * after the proxy confirmed the kick, so it can arrive after the player's disconnect event.
 */
public interface PlayerKickEvent {

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
     * Returns the name of the server the player was on when kicked.
     *
     * @return the server name, or null if unavailable
     */
    @Nullable
    String getServerName();

    /**
     * Returns the kick reason.
     *
     * @return the reason, or null if none was given
     */
    @Nullable
    Component getReason();

    /**
     * Returns the player as reported by the controller with this event.
     *
     * <p>This is a minimal snapshot: only the ID, name, display name, online time and the server the
     * player was on are set. The connected proxy, properties, session and first/last seen are missing,
     * so load the player through {@link app.simplecloud.api.player.PlayerApi} when you need them.
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
