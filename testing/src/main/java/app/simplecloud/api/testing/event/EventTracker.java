package app.simplecloud.api.testing.event;

import java.time.Duration;
import java.util.List;

/**
 * Records events of one type so a test can assert on them.
 *
 * <pre>{@code
 * var stopped = cloud.track(ServerStoppedEvent.class);
 * server.crash(137, "out of memory");
 * assertTrue(stopped.single().getCrashed());
 * }</pre>
 *
 * <p>Closing the tracker stops recording.
 */
public interface EventTracker<E> extends AutoCloseable {

    /**
     * Timeout used by the await methods without an explicit timeout.
     */
    Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);

    /**
     * Returns all recorded events in order.
     */
    List<E> events();

    /**
     * Returns the number of recorded events.
     */
    int count();

    /**
     * Returns the first event.
     *
     * @throws AssertionError if nothing was recorded
     */
    E first();

    /**
     * Returns the last event.
     *
     * @throws AssertionError if nothing was recorded
     */
    E last();

    /**
     * Returns the only event.
     *
     * @throws AssertionError if not exactly one event was recorded
     */
    E single();

    /**
     * Fails if any event was recorded.
     */
    void assertEmpty();

    /**
     * Waits for the first event, useful with async events.
     */
    default E awaitFirst() {
        return awaitFirst(DEFAULT_TIMEOUT);
    }

    /**
     * Waits for the first event.
     *
     * @throws AssertionError if no event arrived in time
     */
    E awaitFirst(Duration timeout);

    /**
     * Waits until at least {@code count} events were recorded.
     */
    default List<E> await(int count) {
        return await(count, DEFAULT_TIMEOUT);
    }

    /**
     * Waits until at least {@code count} events were recorded.
     *
     * @throws AssertionError if not enough events arrived in time
     */
    List<E> await(int count, Duration timeout);

    /**
     * Forgets all recorded events.
     */
    void clear();

    @Override
    void close();
}
