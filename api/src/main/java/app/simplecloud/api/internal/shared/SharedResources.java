package app.simplecloud.api.internal.shared;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Reference-counted resources shared by every {@code CloudApi} instance loaded by the same class loader.
 *
 * <p>Plugins that load the API from the platform plugin, on any server or proxy, share these
 * resources instead of each opening its own NATS connection, HTTP pool and cache. The last lease
 * to close a resource closes it.
 */
public final class SharedResources {

    private static final Map<Object, Entry<?>> ENTRIES = new HashMap<>();

    private SharedResources() {
    }

    /**
     * Returns a lease on the resource for {@code key}, creating it when no open lease exists.
     */
    @SuppressWarnings("unchecked")
    public static <T> Lease<T> acquire(Object key, Supplier<T> factory, Consumer<T> closer) {
        Entry<T> entry;
        synchronized (ENTRIES) {
            entry = (Entry<T>) ENTRIES.get(key);
            if (entry == null) {
                entry = new Entry<>(factory.get(), closer);
                ENTRIES.put(key, entry);
            }
            entry.leases++;
        }
        Entry<T> acquired = entry;
        return new Lease<>(acquired.value, () -> release(key, acquired));
    }

    /**
     * Returns a lease on a resource that is never shared with other instances.
     */
    public static <T> Lease<T> dedicated(T value, Consumer<T> closer) {
        return new Lease<>(value, () -> closer.accept(value));
    }

    static int openLeases(Object key) {
        synchronized (ENTRIES) {
            Entry<?> entry = ENTRIES.get(key);
            return entry == null ? 0 : entry.leases;
        }
    }

    private static <T> void release(Object key, Entry<T> entry) {
        synchronized (ENTRIES) {
            if (--entry.leases > 0) {
                return;
            }
            ENTRIES.remove(key, entry);
        }
        // Closing may block, for example while NATS flushes, so it runs outside the lock.
        entry.closer.accept(entry.value);
    }

    private static final class Entry<T> {
        private final T value;
        private final Consumer<T> closer;
        private int leases;

        private Entry(T value, Consumer<T> closer) {
            this.value = value;
            this.closer = closer;
        }
    }

    public static final class Lease<T> implements AutoCloseable {
        private final T value;
        private final Runnable release;
        private final AtomicBoolean closed = new AtomicBoolean(false);

        private Lease(T value, Runnable release) {
            this.value = value;
            this.release = release;
        }

        public T get() {
            return value;
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                release.run();
            }
        }
    }
}
