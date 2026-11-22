package app.simplecloud.api.testing.internal.event;

import app.simplecloud.api.event.Subscription;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class EventBus implements AutoCloseable {

    private final List<Registration<?>> registrations = new CopyOnWriteArrayList<>();
    private final List<Throwable> asyncFailures = new CopyOnWriteArrayList<>();
    private volatile @Nullable ExecutorService asyncExecutor;

    public <E> Subscription subscribe(Class<E> type, Consumer<? super E> handler) {
        Registration<E> registration = new Registration<>(
                Objects.requireNonNull(type, "type"),
                Objects.requireNonNull(handler, "handler")
        );
        registrations.add(registration);
        return () -> registrations.remove(registration);
    }

    public void publish(Object event) {
        Objects.requireNonNull(event, "event");
        ExecutorService executor = asyncExecutor;
        if (executor != null) {
            executor.execute(() -> dispatch(event, asyncFailures));
            return;
        }
        List<Throwable> failures = new ArrayList<>();
        dispatch(event, failures);
        throwIfFailed(failures);
    }

    public synchronized void async(boolean async) {
        if (async && asyncExecutor == null) {
            asyncExecutor = Executors.newSingleThreadExecutor(Thread.ofVirtual().name("test-cloud-events").factory());
        } else if (!async && asyncExecutor != null) {
            drain(asyncExecutor);
            asyncExecutor = null;
        }
    }

    @Override
    public void close() {
        async(false);
        List<Throwable> failures = List.copyOf(asyncFailures);
        asyncFailures.clear();
        throwIfFailed(failures);
    }

    private void dispatch(Object event, List<Throwable> failures) {
        for (Registration<?> registration : registrations) {
            try {
                registration.deliver(event);
            } catch (Throwable throwable) {
                failures.add(throwable);
            }
        }
    }

    private static void drain(ExecutorService executor) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private static void throwIfFailed(List<Throwable> failures) {
        if (failures.isEmpty()) {
            return;
        }
        Throwable first = failures.getFirst();
        for (Throwable other : failures.subList(1, failures.size())) {
            first.addSuppressed(other);
        }
        if (first instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        if (first instanceof Error error) {
            throw error;
        }
        throw new IllegalStateException("Event handler failed", first);
    }

    private record Registration<E>(Class<E> type, Consumer<? super E> handler) {

        private void deliver(Object event) {
            if (type.isInstance(event)) {
                handler.accept(type.cast(event));
            }
        }
    }
}
