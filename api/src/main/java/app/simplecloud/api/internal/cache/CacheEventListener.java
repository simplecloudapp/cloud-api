package app.simplecloud.api.internal.cache;

import app.simplecloud.api.cache.QueryCache;
import app.simplecloud.api.cache.QueryKey;
import app.simplecloud.api.event.EventApi;
import app.simplecloud.api.event.Subscription;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;

/**
 * Automatically invalidates cache entries when NATS events arrive.
 * This provides automatic cache coherence without manual intervention.
 *
 * <p>Uses debouncing to coalesce multiple events within a short time window
 * into a single invalidation. This is particularly useful during rolling restarts
 * or bulk operations where many events may arrive in rapid succession.
 */
public class CacheEventListener {

    private static final long DEFAULT_DEBOUNCE_MS = 50;

    private final QueryCache cache;
    private final List<Subscription> subscriptions = new ArrayList<>();
    private final ScheduledExecutorService scheduler;
    private final long debounceMs;

    // Pending invalidations - using concurrent sets for thread safety
    private final Set<QueryKey> pendingInvalidations = ConcurrentHashMap.newKeySet();
    private final Set<QueryKey> pendingPatternInvalidations = ConcurrentHashMap.newKeySet();
    private volatile ScheduledFuture<?> pendingFlush;

    public CacheEventListener(QueryCache cache, EventApi eventApi) {
        this(cache, eventApi, DEFAULT_DEBOUNCE_MS);
    }

    public CacheEventListener(QueryCache cache, EventApi eventApi, long debounceMs) {
        this.cache = cache;
        this.debounceMs = debounceMs;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "CacheEventListener-Debounce");
            t.setDaemon(true);
            return t;
        });
        setupListeners(eventApi);
    }

    /**
     * Schedules a debounced invalidation for a specific key.
     */
    private void scheduleInvalidation(QueryKey key) {
        pendingInvalidations.add(key);
        scheduleFlush();
    }

    /**
     * Schedules a debounced pattern invalidation (invalidateAll).
     */
    private void schedulePatternInvalidation(QueryKey pattern) {
        pendingPatternInvalidations.add(pattern);
        scheduleFlush();
    }

    /**
     * Schedules or reschedules the flush operation.
     * Uses debouncing - if called multiple times within the debounce window,
     * only one flush will occur after the window expires.
     */
    private synchronized void scheduleFlush() {
        if (pendingFlush != null && !pendingFlush.isDone()) {
            pendingFlush.cancel(false);
        }
        pendingFlush = scheduler.schedule(this::flush, debounceMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Flushes all pending invalidations.
     */
    private void flush() {
        // Process pattern invalidations first (they may cover individual keys)
        for (QueryKey pattern : pendingPatternInvalidations) {
            cache.invalidateAll(pattern);
        }
        pendingPatternInvalidations.clear();

        // Process individual key invalidations
        for (QueryKey key : pendingInvalidations) {
            cache.invalidate(key);
        }
        pendingInvalidations.clear();
    }

    private void setupListeners(EventApi eventApi) {
        // Server events
        subscriptions.add(eventApi.server().onStarted(event -> {
            schedulePatternInvalidation(QueryKey.of("servers"));
        }));

        subscriptions.add(eventApi.server().onStopped(event -> {
            scheduleInvalidation(QueryKey.of("server", event.getServerId()));
            schedulePatternInvalidation(QueryKey.of("servers"));
        }));

        subscriptions.add(eventApi.server().onStateChanged(event -> {
            scheduleInvalidation(QueryKey.of("server", event.getServerId()));
            schedulePatternInvalidation(QueryKey.of("servers"));
        }));

        subscriptions.add(eventApi.server().onDeleted(event -> {
            scheduleInvalidation(QueryKey.of("server", event.getServerId()));
            schedulePatternInvalidation(QueryKey.of("servers"));
        }));

        subscriptions.add(eventApi.server().onUpdated(event -> {
            scheduleInvalidation(QueryKey.of("server", event.getServerId()));
            schedulePatternInvalidation(QueryKey.of("servers"));
        }));

        // Group events
        subscriptions.add(eventApi.group().onCreated(event -> {
            schedulePatternInvalidation(QueryKey.of("groups"));
        }));

        subscriptions.add(eventApi.group().onUpdated(event -> {
            scheduleInvalidation(QueryKey.of("group", event.getServerGroupId()));
            schedulePatternInvalidation(QueryKey.of("groups"));
        }));

        subscriptions.add(eventApi.group().onDeleted(event -> {
            scheduleInvalidation(QueryKey.of("group", event.getServerGroupId()));
            schedulePatternInvalidation(QueryKey.of("groups"));
        }));

        // Persistent server events
        subscriptions.add(eventApi.persistentServer().onCreated(event -> {
            schedulePatternInvalidation(QueryKey.of("persistentServers"));
        }));

        subscriptions.add(eventApi.persistentServer().onStarted(event -> {
            scheduleInvalidation(QueryKey.of("persistentServer", event.getPersistentServerId()));
            schedulePatternInvalidation(QueryKey.of("persistentServers"));
        }));

        subscriptions.add(eventApi.persistentServer().onStopped(event -> {
            scheduleInvalidation(QueryKey.of("persistentServer", event.getPersistentServerId()));
            schedulePatternInvalidation(QueryKey.of("persistentServers"));
        }));

        subscriptions.add(eventApi.persistentServer().onUpdated(event -> {
            scheduleInvalidation(QueryKey.of("persistentServer", event.getPersistentServerId()));
            schedulePatternInvalidation(QueryKey.of("persistentServers"));
        }));

        subscriptions.add(eventApi.persistentServer().onDeleted(event -> {
            scheduleInvalidation(QueryKey.of("persistentServer", event.getPersistentServerId()));
            schedulePatternInvalidation(QueryKey.of("persistentServers"));
        }));
    }

    /**
     * Shuts down the event listener by unsubscribing from all events.
     */
    public void shutdown() {
        subscriptions.forEach(Subscription::close);
        subscriptions.clear();

        // Flush any pending invalidations immediately
        if (pendingFlush != null) {
            pendingFlush.cancel(false);
        }
        flush();

        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(1, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
