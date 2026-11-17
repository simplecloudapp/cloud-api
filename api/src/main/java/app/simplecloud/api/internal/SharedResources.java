package app.simplecloud.api.internal;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Resources of one kind that {@code CloudApi} instances share, one per key. The first handle on a key
 * opens its resource, and closing the last handle on it closes the resource. A handle taken after that
 * opens a new one.
 *
 * <p>Counting handles, instead of letting one instance own the resource, keeps it open for the other
 * instances whichever of them closes first, also when a plugin is disabled and enabled again.
 */
final class SharedResources<K, R> {

    interface Opener<R> {
        R open();
    }

    interface Closer<R> {
        void close(R resource);
    }

    private final Closer<R> closer;
    private final Map<K, Entry<R>> entries = new HashMap<>();

    SharedResources(Closer<R> closer) {
        this.closer = closer;
    }

    /**
     * Returns a handle on the resource for {@code key}. When no handle on it is open, {@code opener}
     * opens a new one; it runs under this registry's lock, so it must not block.
     */
    synchronized Handle<R> acquire(K key, Opener<R> opener) {
        Entry<R> entry = entries.computeIfAbsent(key, absentKey -> new Entry<>(opener.open()));
        entry.openHandles++;
        return new Handle<>(entry.resource, () -> release(key, entry));
    }

    private void release(K key, Entry<R> entry) {
        synchronized (this) {
            entry.openHandles--;
            if (entry.openHandles > 0) {
                return;
            }
            entries.remove(key);
        }
        // Closing can block, a NATS connection while it flushes, so it runs outside the lock.
        closer.close(entry.resource);
    }

    private static final class Entry<R> {
        private final R resource;
        private int openHandles;

        private Entry(R resource) {
            this.resource = resource;
        }
    }

    static final class Handle<R> implements AutoCloseable {
        private final R resource;
        private final Runnable release;
        private final AtomicBoolean closed = new AtomicBoolean(false);

        private Handle(R resource, Runnable release) {
            this.resource = resource;
            this.release = release;
        }

        R resource() {
            return resource;
        }

        /**
         * Closes the resource if this was its last open handle. Closing a handle twice counts once.
         */
        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                release.run();
            }
        }
    }
}
