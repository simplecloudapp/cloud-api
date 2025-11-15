package app.simplecloud.api.event.blueprint;

import app.simplecloud.api.event.Subscription;

import java.util.function.Consumer;

/**
 * API for subscribing to blueprint events.
 */
public interface BlueprintEventApi {

    /**
     * Subscribes to blueprint creation events.
     * 
     * @param handler the callback to invoke when a blueprint is created
     * @return a subscription that can be closed to stop receiving events
     */
    Subscription onCreated(Consumer<BlueprintCreatedEvent> handler);

    /**
     * Subscribes to blueprint update events.
     * 
     * @param handler the callback to invoke when a blueprint is updated
     * @return a subscription that can be closed to stop receiving events
     */
    Subscription onUpdated(Consumer<BlueprintUpdatedEvent> handler);

    /**
     * Subscribes to blueprint deletion events.
     * 
     * @param handler the callback to invoke when a blueprint is deleted
     * @return a subscription that can be closed to stop receiving events
     */
    Subscription onDeleted(Consumer<BlueprintDeletedEvent> handler);

}

