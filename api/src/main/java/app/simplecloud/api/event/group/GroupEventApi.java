package app.simplecloud.api.event.group;

import app.simplecloud.api.event.Subscription;

import java.util.function.Consumer;

/**
 * API for subscribing to server group events.
 */
public interface GroupEventApi {

    /**
     * Subscribes to server group creation events.
     *
     * @param handler the callback to invoke when a group is created
     * @return a subscription that can be closed to stop receiving events
     */
    Subscription onCreated(Consumer<GroupCreatedEvent> handler);

    /**
     * Subscribes to server group update events.
     *
     * @param handler the callback to invoke when a group is updated
     * @return a subscription that can be closed to stop receiving events
     */
    Subscription onUpdated(Consumer<GroupUpdatedEvent> handler);

    /**
     * Subscribes to server group deletion events.
     *
     * @param handler the callback to invoke when a group is deleted
     * @return a subscription that can be closed to stop receiving events
     */
    Subscription onDeleted(Consumer<GroupDeletedEvent> handler);

}

