package app.simplecloud.api.internal.cache;

import app.simplecloud.api.cache.QueryCache;
import app.simplecloud.api.cache.QueryKey;
import app.simplecloud.api.event.EventApi;
import app.simplecloud.api.event.Subscription;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class CacheEventListener {

    private static final long MAX_WAIT_DEBOUNCE_FACTOR = 10;

    private final QueryCache cache;
    private final List<Subscription> subscriptions = new ArrayList<>();
    private final ScheduledExecutorService scheduler;
    private final long debounceMs;

    private final Set<QueryKey> pendingInvalidations = ConcurrentHashMap.newKeySet();
    private final Set<QueryKey> pendingPatternInvalidations = ConcurrentHashMap.newKeySet();
    private volatile ScheduledFuture<?> pendingFlush;
    private long firstPendingAtNanos = -1;
    private boolean shutdown;

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

    private synchronized void scheduleInvalidation(QueryKey key) {
        if (shutdown) {
            return;
        }
        pendingInvalidations.add(key);
        scheduleFlush();
    }

    private synchronized void schedulePatternInvalidation(QueryKey pattern) {
        if (shutdown) {
            return;
        }
        pendingPatternInvalidations.add(pattern);
        scheduleFlush();
    }

    private void scheduleServerInvalidation() {
        schedulePatternInvalidation(QueryKey.of("servers"));
        schedulePatternInvalidation(QueryKey.of("server"));
    }

    private void scheduleGroupStatsInvalidation() {
        schedulePatternInvalidation(QueryKey.of("groups"));
        schedulePatternInvalidation(QueryKey.of("group"));
    }

    private void schedulePersistentServerInvalidation() {
        schedulePatternInvalidation(QueryKey.of("persistentServers"));
        schedulePatternInvalidation(QueryKey.of("persistentServer"));
    }

    private void schedulePersistentServerInvalidation(String persistentServerId) {
        if (persistentServerId != null && !persistentServerId.isEmpty()) {
            schedulePersistentServerInvalidation();
        }
    }

    private void schedulePlayerCountInvalidation() {
        scheduleServerInvalidation();
        scheduleGroupStatsInvalidation();
        schedulePersistentServerInvalidation();
    }

    private void scheduleFlush() {
        long now = System.nanoTime();
        if (firstPendingAtNanos < 0) {
            firstPendingAtNanos = now;
        }
        if (pendingFlush != null && !pendingFlush.isDone()) {
            pendingFlush.cancel(false);
        }
        long maxWaitLeftMs = TimeUnit.NANOSECONDS.toMillis(
                firstPendingAtNanos + TimeUnit.MILLISECONDS.toNanos(debounceMs * MAX_WAIT_DEBOUNCE_FACTOR) - now);
        long delayMs = Math.clamp(maxWaitLeftMs, 0, debounceMs);
        pendingFlush = scheduler.schedule(this::flush, delayMs, TimeUnit.MILLISECONDS);
    }

    private synchronized void flush() {
        firstPendingAtNanos = -1;
        for (QueryKey pattern : pendingPatternInvalidations) {
            cache.invalidateAll(pattern);
        }
        pendingPatternInvalidations.clear();

        for (QueryKey key : pendingInvalidations) {
            cache.invalidate(key);
        }
        pendingInvalidations.clear();
    }

    private void setupListeners(EventApi eventApi) {
        // Server events
        subscriptions.add(eventApi.server().onStarted(event -> {
            schedulePatternInvalidation(QueryKey.of("servers"));
            schedulePatternInvalidation(QueryKey.of("server", "numerical"));
            scheduleGroupStatsInvalidation();
            schedulePersistentServerInvalidation(event.getPersistentServerId());
        }));

        subscriptions.add(eventApi.server().onStopped(event -> {
            scheduleInvalidation(QueryKey.of("server", event.getServerId()));
            schedulePatternInvalidation(QueryKey.of("servers"));
            schedulePatternInvalidation(QueryKey.of("server", "numerical"));
            scheduleGroupStatsInvalidation();
            schedulePersistentServerInvalidation(event.getPersistentServerId());
        }));

        subscriptions.add(eventApi.server().onStateChanged(event -> {
            scheduleInvalidation(QueryKey.of("server", event.getServerId()));
            schedulePatternInvalidation(QueryKey.of("servers"));
            schedulePatternInvalidation(QueryKey.of("server", "numerical"));
            scheduleGroupStatsInvalidation();
            schedulePersistentServerInvalidation(event.getPersistentServerId());
        }));

        subscriptions.add(eventApi.server().onDeleted(event -> {
            scheduleInvalidation(QueryKey.of("server", event.getServerId()));
            schedulePatternInvalidation(QueryKey.of("servers"));
            schedulePatternInvalidation(QueryKey.of("server", "numerical"));
            scheduleGroupStatsInvalidation();
            schedulePersistentServerInvalidation(event.getPersistentServerId());
        }));

        subscriptions.add(eventApi.server().onUpdated(event -> {
            scheduleInvalidation(QueryKey.of("server", event.getServerId()));
            schedulePatternInvalidation(QueryKey.of("servers"));
            schedulePatternInvalidation(QueryKey.of("server", "numerical"));
            schedulePersistentServerInvalidation(event.getPersistentServerId());
        }));

        subscriptions.add(eventApi.player().onLogin(event -> schedulePlayerCountInvalidation()));
        subscriptions.add(eventApi.player().onDisconnect(event -> schedulePlayerCountInvalidation()));
        subscriptions.add(eventApi.player().onServerSwitch(event -> schedulePlayerCountInvalidation()));

        // Group events
        subscriptions.add(eventApi.group().onCreated(event -> {
            schedulePatternInvalidation(QueryKey.of("groups"));
            schedulePatternInvalidation(QueryKey.of("group", "name"));
        }));

        subscriptions.add(eventApi.group().onUpdated(event -> {
            scheduleServerInvalidation();
            scheduleInvalidation(QueryKey.of("group", event.getServerGroupId()));
            schedulePatternInvalidation(QueryKey.of("groups"));
            schedulePatternInvalidation(QueryKey.of("group", "name"));
        }));

        subscriptions.add(eventApi.group().onDeleted(event -> {
            scheduleServerInvalidation();
            scheduleInvalidation(QueryKey.of("group", event.getServerGroupId()));
            schedulePatternInvalidation(QueryKey.of("groups"));
            schedulePatternInvalidation(QueryKey.of("group", "name"));
        }));

        // Persistent server events
        subscriptions.add(eventApi.persistentServer().onCreated(event -> {
            schedulePatternInvalidation(QueryKey.of("persistentServers"));
            schedulePatternInvalidation(QueryKey.of("persistentServer", "name"));
        }));

        subscriptions.add(eventApi.persistentServer().onStarted(event -> {
            scheduleInvalidation(QueryKey.of("persistentServer", event.getPersistentServerId()));
            schedulePatternInvalidation(QueryKey.of("persistentServers"));
            schedulePatternInvalidation(QueryKey.of("persistentServer", "name"));
        }));

        subscriptions.add(eventApi.persistentServer().onStopped(event -> {
            scheduleInvalidation(QueryKey.of("persistentServer", event.getPersistentServerId()));
            schedulePatternInvalidation(QueryKey.of("persistentServers"));
            schedulePatternInvalidation(QueryKey.of("persistentServer", "name"));
        }));

        subscriptions.add(eventApi.persistentServer().onUpdated(event -> {
            scheduleServerInvalidation();
            scheduleInvalidation(QueryKey.of("persistentServer", event.getPersistentServerId()));
            schedulePatternInvalidation(QueryKey.of("persistentServers"));
            schedulePatternInvalidation(QueryKey.of("persistentServer", "name"));
        }));

        subscriptions.add(eventApi.persistentServer().onDeleted(event -> {
            scheduleServerInvalidation();
            scheduleInvalidation(QueryKey.of("persistentServer", event.getPersistentServerId()));
            schedulePatternInvalidation(QueryKey.of("persistentServers"));
            schedulePatternInvalidation(QueryKey.of("persistentServer", "name"));
        }));

        // Blueprint events
        subscriptions.add(eventApi.blueprint().onCreated(event -> scheduleBlueprintInvalidation()));
        subscriptions.add(eventApi.blueprint().onUpdated(event -> {
            scheduleBlueprintInvalidation();
            scheduleServerInvalidation();
        }));
        subscriptions.add(eventApi.blueprint().onDeleted(event -> {
            scheduleBlueprintInvalidation();
            scheduleServerInvalidation();
        }));
    }

    private void scheduleBlueprintInvalidation() {
        schedulePatternInvalidation(QueryKey.of("blueprints"));
        schedulePatternInvalidation(QueryKey.of("blueprint"));
    }

    public void shutdown() {
        synchronized (this) {
            if (shutdown) {
                return;
            }
            shutdown = true;

            if (pendingFlush != null) {
                pendingFlush.cancel(false);
                pendingFlush = null;
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
