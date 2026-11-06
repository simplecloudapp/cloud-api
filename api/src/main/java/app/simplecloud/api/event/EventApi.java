package app.simplecloud.api.event;

import app.simplecloud.api.event.blueprint.BlueprintEventApi;
import app.simplecloud.api.event.group.GroupEventApi;
import app.simplecloud.api.event.persistentserver.PersistentServerEventApi;
import app.simplecloud.api.event.player.PlayerEventApi;
import app.simplecloud.api.event.server.ServerEventApi;

/**
 * API for subscribing to real-time system events.
 *
 * <p>All event subscriptions return a {@link Subscription} that must be
 * closed when no longer needed to prevent resource leaks.
 *
 * <p>Handlers are called on a background thread that is shared with other subscriptions, so move
 * long-running work to your own executor. Exceptions thrown by a handler are logged and don't end
 * the subscription.
 */
public interface EventApi {

    /**
     * Returns the event API for server group events.
     *
     * @return the group event API
     */
    GroupEventApi group();

    /**
     * Returns the event API for server instance events.
     *
     * @return the server event API
     */
    ServerEventApi server();

    /**
     * Returns the event API for persistent server events.
     *
     * @return the persistent server event API
     */
    PersistentServerEventApi persistentServer();

    /**
     * Returns the event API for blueprint events.
     *
     * @return the blueprint event API
     */
    BlueprintEventApi blueprint();

    /**
     * Returns the event API for player events.
     *
     * @return the player event API
     */
    PlayerEventApi player();
}
