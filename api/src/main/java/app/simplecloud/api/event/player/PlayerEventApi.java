package app.simplecloud.api.event.player;

import app.simplecloud.api.event.Subscription;

import java.util.function.Consumer;

/**
 * API for subscribing to player events.
 */
public interface PlayerEventApi {

    /**
     * Subscribes to player login events.
     *
     * @param handler the callback to invoke when a player logs in
     * @return a subscription that can be closed to stop receiving events
     */
    Subscription onLogin(Consumer<PlayerLoginEvent> handler);

    /**
     * Subscribes to player disconnect events.
     *
     * @param handler the callback to invoke when a player disconnects
     * @return a subscription that can be closed to stop receiving events
     */
    Subscription onDisconnect(Consumer<PlayerDisconnectEvent> handler);

    /**
     * Subscribes to player server switch events.
     *
     * @param handler the callback to invoke when a player switches servers
     * @return a subscription that can be closed to stop receiving events
     */
    Subscription onServerSwitch(Consumer<PlayerServerSwitchEvent> handler);

    /**
     * Subscribes to player kick events.
     *
     * @param handler the callback to invoke when a player is kicked
     * @return a subscription that can be closed to stop receiving events
     */
    Subscription onKick(Consumer<PlayerKickEvent> handler);

}
