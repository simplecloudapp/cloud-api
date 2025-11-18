package app.simplecloud.api.event;

/**
 * Represents an active event subscription.
 *
 * <p>Subscriptions must be closed when no longer needed to release resources.
 * This interface implements {@link AutoCloseable} for use in try-with-resources.
 */
public interface Subscription extends AutoCloseable {

    /**
     * Unsubscribes from the event stream.
     *
     * <p>After calling this method, the handler will no longer receive events.
     * This method is idempotent and can be called multiple times safely.
     */
    void unsubscribe();

    /**
     * Closes the subscription by calling {@link #unsubscribe()}.
     *
     * <p>This method allows subscriptions to be used in try-with-resources statements.
     */
    @Override
    default void close() {
        unsubscribe();
    }

}

