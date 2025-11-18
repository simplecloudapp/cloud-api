package app.simplecloud.api.event.server;

import app.simplecloud.api.event.Subscription;

import java.util.function.Consumer;

/**
 * API for subscribing to server instance events.
 */
public interface ServerEventApi {

    /**
     * Subscribes to server start events.
     *
     * @param handler the callback to invoke when a server starts
     * @return a subscription that can be closed to stop receiving events
     */
    Subscription onStarted(Consumer<ServerStartedEvent> handler);

    /**
     * Subscribes to server stop events.
     *
     * @param handler the callback to invoke when a server stops
     * @return a subscription that can be closed to stop receiving events
     */
    Subscription onStopped(Consumer<ServerStoppedEvent> handler);

    /**
     * Subscribes to server state change events.
     *
     * @param handler the callback to invoke when a server's state changes
     * @return a subscription that can be closed to stop receiving events
     */
    Subscription onStateChanged(Consumer<ServerStateChangedEvent> handler);

    /**
     * Subscribes to server deletion events.
     *
     * @param handler the callback to invoke when a server is deleted
     * @return a subscription that can be closed to stop receiving events
     */
    Subscription onDeleted(Consumer<ServerDeletedEvent> handler);

}

