package app.simplecloud.api.event.persistentserver;

import app.simplecloud.api.event.Subscription;

import java.util.function.Consumer;

/**
 * API for subscribing to persistent server events.
 *
 * <p>Persistent servers are long-lived server instances that maintain state across restarts.
 */
public interface PersistentServerEventApi {

    /**
     * Subscribes to persistent server creation events.
     *
     * @param handler the callback to invoke when a persistent server is created
     * @return a subscription that can be closed to stop receiving events
     */
    Subscription onCreated(Consumer<PersistentServerCreatedEvent> handler);

    /**
     * Subscribes to persistent server start events.
     *
     * @param handler the callback to invoke when a persistent server starts
     * @return a subscription that can be closed to stop receiving events
     */
    Subscription onStarted(Consumer<PersistentServerStartedEvent> handler);

    /**
     * Subscribes to persistent server stop events.
     *
     * @param handler the callback to invoke when a persistent server stops
     * @return a subscription that can be closed to stop receiving events
     */
    Subscription onStopped(Consumer<PersistentServerStoppedEvent> handler);

    /**
     * Subscribes to persistent server update events.
     *
     * @param handler the callback to invoke when a persistent server is updated
     * @return a subscription that can be closed to stop receiving events
     */
    Subscription onUpdated(Consumer<PersistentServerUpdatedEvent> handler);

    /**
     * Subscribes to persistent server deletion events.
     *
     * @param handler the callback to invoke when a persistent server is deleted
     * @return a subscription that can be closed to stop receiving events
     */
    Subscription onDeleted(Consumer<PersistentServerDeletedEvent> handler);

}

