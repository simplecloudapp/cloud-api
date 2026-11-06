package app.simplecloud.api.event;

/**
 * Represents an active event subscription.
 */
public interface Subscription extends AutoCloseable {

    /**
     * Unsubscribes from the event stream.
     */
    void unsubscribe();

    /**
     * Closes the subscription by calling {@link #unsubscribe()}.
     */
    @Override
    default void close() {
        unsubscribe();
    }
}
