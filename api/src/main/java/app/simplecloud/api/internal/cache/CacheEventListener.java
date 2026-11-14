package app.simplecloud.api.internal.cache;

import app.simplecloud.api.cache.QueryCache;
import app.simplecloud.api.cache.QueryKey;
import app.simplecloud.api.event.EventApi;
import app.simplecloud.api.event.Subscription;
import app.simplecloud.api.event.group.GroupEventApi;
import app.simplecloud.api.event.persistentserver.PersistentServerEventApi;
import app.simplecloud.api.event.server.ServerEventApi;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Invalidates cache entries when NATS events arrive.
 *
 * <p>Invalidations are batched: the first event opens a window of {@code batchWindowMs}, events
 * arriving inside it join the batch, and the batch is applied when the window closes. Later
 * events never postpone a pending batch, so during a steady stream of events, such as a rolling
 * restart, each event is still applied no later than one window after it arrives.
 */
public class CacheEventListener {

    private final QueryCache cache;
    private final long batchWindowMs;
    private final List<Subscription> subscriptions = new ArrayList<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "CacheEventListener-Flush");
        thread.setDaemon(true);
        return thread;
    });

    // Guarded by this.
    private final Set<QueryKey> pendingKeys = new HashSet<>();
    private final Set<QueryKey> pendingPatterns = new HashSet<>();
    private ScheduledFuture<?> pendingFlush;
    private boolean shutdown;

    public CacheEventListener(QueryCache cache, EventApi eventApi, long batchWindowMs) {
        this.cache = cache;
        this.batchWindowMs = batchWindowMs;
        subscribeToServerEvents(eventApi.server());
        subscribeToGroupEvents(eventApi.group());
        subscribeToPersistentServerEvents(eventApi.persistentServer());
    }

    private void subscribeToServerEvents(ServerEventApi events) {
        subscriptions.add(events.onStarted(event -> queueServerInvalidation(event.getServerId())));
        subscriptions.add(events.onStopped(event -> queueServerInvalidation(event.getServerId())));
        subscriptions.add(events.onStateChanged(event -> queueServerInvalidation(event.getServerId())));
        subscriptions.add(events.onDeleted(event -> queueServerInvalidation(event.getServerId())));
        subscriptions.add(events.onUpdated(event -> queueServerInvalidation(event.getServerId())));
    }

    private void subscribeToGroupEvents(GroupEventApi events) {
        subscriptions.add(events.onCreated(event -> queueGroupInvalidation(event.getServerGroupId())));
        subscriptions.add(events.onUpdated(event -> queueGroupInvalidation(event.getServerGroupId())));
        subscriptions.add(events.onDeleted(event -> queueGroupInvalidation(event.getServerGroupId())));
    }

    private void subscribeToPersistentServerEvents(PersistentServerEventApi events) {
        subscriptions.add(events.onCreated(event -> queuePersistentServerInvalidation(event.getPersistentServerId())));
        subscriptions.add(events.onStarted(event -> queuePersistentServerInvalidation(event.getPersistentServerId())));
        subscriptions.add(events.onStopped(event -> queuePersistentServerInvalidation(event.getPersistentServerId())));
        subscriptions.add(events.onUpdated(event -> queuePersistentServerInvalidation(event.getPersistentServerId())));
        subscriptions.add(events.onDeleted(event -> queuePersistentServerInvalidation(event.getPersistentServerId())));
    }

    private void queueServerInvalidation(String serverId) {
        queueKey(QueryKey.of("server", serverId));
        queuePattern(QueryKey.of("servers"));
        queuePattern(QueryKey.of("server", "numerical"));
    }

    private void queueGroupInvalidation(String groupId) {
        queueKey(QueryKey.of("group", groupId));
        queuePattern(QueryKey.of("groups"));
        queuePattern(QueryKey.of("group", "name"));
    }

    private void queuePersistentServerInvalidation(String persistentServerId) {
        queueKey(QueryKey.of("persistentServer", persistentServerId));
        queuePattern(QueryKey.of("persistentServers"));
        queuePattern(QueryKey.of("persistentServer", "name"));
    }

    private synchronized void queueKey(QueryKey key) {
        if (shutdown) {
            return;
        }
        pendingKeys.add(key);
        scheduleFlushIfNoneScheduled();
    }

    private synchronized void queuePattern(QueryKey pattern) {
        if (shutdown) {
            return;
        }
        pendingPatterns.add(pattern);
        scheduleFlushIfNoneScheduled();
    }

    // Caller holds this.
    private void scheduleFlushIfNoneScheduled() {
        if (pendingFlush != null) {
            return;
        }
        pendingFlush = scheduler.schedule(this::flush, batchWindowMs, TimeUnit.MILLISECONDS);
    }

    private synchronized void flush() {
        pendingFlush = null;

        for (QueryKey pattern : pendingPatterns) {
            cache.invalidateAll(pattern);
        }
        pendingPatterns.clear();

        for (QueryKey key : pendingKeys) {
            cache.invalidate(key);
        }
        pendingKeys.clear();
    }

    /**
     * Unsubscribes from all events, stops the batch scheduler and applies the invalidations
     * that were still waiting for their window to close.
     */
    public void shutdown() {
        synchronized (this) {
            if (shutdown) {
                return;
            }
            shutdown = true;

            if (pendingFlush != null) {
                pendingFlush.cancel(false);
            }
        }

        subscriptions.forEach(Subscription::close);
        subscriptions.clear();

        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(1, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        flush();
    }
}
