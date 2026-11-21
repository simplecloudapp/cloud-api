package app.simplecloud.api.testing.internal.event;

import app.simplecloud.api.event.Subscription;
import app.simplecloud.api.testing.event.EventTracker;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public final class EventTrackerImpl<E> implements EventTracker<E> {

    private final Class<E> type;
    private final List<E> events = new ArrayList<>();
    private Subscription subscription = () -> {
    };

    private EventTrackerImpl(Class<E> type) {
        this.type = type;
    }

    public static <E> EventTrackerImpl<E> subscribe(EventBus bus, Class<E> type, Predicate<? super E> filter) {
        EventTrackerImpl<E> tracker = new EventTrackerImpl<>(type);
        tracker.subscription = bus.subscribe(type, event -> {
            if (filter.test(event)) {
                tracker.add(event);
            }
        });
        return tracker;
    }

    private synchronized void add(E event) {
        events.add(event);
        notifyAll();
    }

    @Override
    public synchronized List<E> events() {
        return List.copyOf(events);
    }

    @Override
    public synchronized int count() {
        return events.size();
    }

    @Override
    public synchronized E first() {
        if (events.isEmpty()) {
            throw new AssertionError("Expected at least one " + name() + " but none was published");
        }
        return events.getFirst();
    }

    @Override
    public synchronized E last() {
        if (events.isEmpty()) {
            throw new AssertionError("Expected at least one " + name() + " but none was published");
        }
        return events.getLast();
    }

    @Override
    public synchronized E single() {
        if (events.size() != 1) {
            throw new AssertionError("Expected exactly one " + name() + " but got " + events.size() + ": " + events);
        }
        return events.getFirst();
    }

    @Override
    public synchronized void assertEmpty() {
        if (!events.isEmpty()) {
            throw new AssertionError("Expected no " + name() + " but got " + events.size() + ": " + events);
        }
    }

    @Override
    public E awaitFirst(Duration timeout) {
        return await(1, timeout).getFirst();
    }

    @Override
    public synchronized List<E> await(int count, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (events.size() < count) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) {
                throw new AssertionError("Expected at least " + count + " " + name() + " within " + timeout
                        + " but got " + events.size());
            }
            try {
                wait(Math.max(1, remaining / 1_000_000));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while waiting for " + name(), exception);
            }
        }
        return List.copyOf(events);
    }

    @Override
    public synchronized void clear() {
        events.clear();
    }

    @Override
    public void close() {
        subscription.unsubscribe();
    }

    private String name() {
        return type.getSimpleName();
    }
}
