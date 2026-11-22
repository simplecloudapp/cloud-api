package app.simplecloud.api.internal.cache;

import app.simplecloud.api.cache.QueryCache;
import app.simplecloud.api.cache.QueryKey;
import app.simplecloud.api.event.EventApi;
import app.simplecloud.api.event.Subscription;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;

public class CacheEventListener {

    private final QueryCache cache;
    private final List<Subscription> subscriptions = new ArrayList<>();
    private final ScheduledExecutorService scheduler;
    private final long debounceMs;

    private final Set<QueryKey> pendingInvalidations = ConcurrentHashMap.newKeySet();
    private final Set<QueryKey> pendingPatternInvalidations = ConcurrentHashMap.newKeySet();
    private volatile ScheduledFuture<?> pendingFlush;
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

    private void scheduleFlush() {
        if (pendingFlush != null && !pendingFlush.isDone()) {
            pendingFlush.cancel(false);
        }
        pendingFlush = scheduler.schedule(this::flush, debounceMs, TimeUnit.MILLISECONDS);
    }

    private synchronized void flush() {
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
        }));

        subscriptions.add(eventApi.server().onStopped(event -> {
            scheduleInvalidation(QueryKey.of("server", event.getServerId()));
            schedulePatternInvalidation(QueryKey.of("servers"));
            schedulePatternInvalidation(QueryKey.of("server", "numerical"));
        }));

        subscriptions.add(eventApi.server().onStateChanged(event -> {
            scheduleInvalidation(QueryKey.of("server", event.getServerId()));
            schedulePatternInvalidation(QueryKey.of("servers"));
            schedulePatternInvalidation(QueryKey.of("server", "numerical"));
        }));

        subscriptions.add(eventApi.server().onDeleted(event -> {
            scheduleInvalidation(QueryKey.of("server", event.getServerId()));
            schedulePatternInvalidation(QueryKey.of("servers"));
            schedulePatternInvalidation(QueryKey.of("server", "numerical"));
        }));

        subscriptions.add(eventApi.server().onUpdated(event -> {
            scheduleInvalidation(QueryKey.of("server", event.getServerId()));
            schedulePatternInvalidation(QueryKey.of("servers"));
            schedulePatternInvalidation(QueryKey.of("server", "numerical"));
        }));

        // Group events
        subscriptions.add(eventApi.group().onCreated(event -> {
            schedulePatternInvalidation(QueryKey.of("groups"));
            schedulePatternInvalidation(QueryKey.of("group", "name"));
        }));

        subscriptions.add(eventApi.group().onUpdated(event -> {
            scheduleInvalidation(QueryKey.of("group", event.getServerGroupId()));
            schedulePatternInvalidation(QueryKey.of("groups"));
            schedulePatternInvalidation(QueryKey.of("group", "name"));
            schedulePatternInvalidation(QueryKey.of("servers"));
            schedulePatternInvalidation(QueryKey.of("server"));
        }));

        subscriptions.add(eventApi.group().onDeleted(event -> {
            scheduleInvalidation(QueryKey.of("group", event.getServerGroupId()));
            schedulePatternInvalidation(QueryKey.of("groups"));
            schedulePatternInvalidation(QueryKey.of("group", "name"));
        }));

        // Blueprint events
        subscriptions.add(eventApi.blueprint().onCreated(event -> {
            schedulePatternInvalidation(QueryKey.of("blueprints"));
            schedulePatternInvalidation(QueryKey.of("blueprint", "name"));
        }));

        subscriptions.add(eventApi.blueprint().onUpdated(event -> {
            scheduleInvalidation(QueryKey.of("blueprint", event.getBlueprintId()));
            schedulePatternInvalidation(QueryKey.of("blueprints"));
            schedulePatternInvalidation(QueryKey.of("blueprint", "name"));
            schedulePatternInvalidation(QueryKey.of("servers"));
            schedulePatternInvalidation(QueryKey.of("server"));
        }));

        subscriptions.add(eventApi.blueprint().onDeleted(event -> {
            scheduleInvalidation(QueryKey.of("blueprint", event.getBlueprintId()));
            schedulePatternInvalidation(QueryKey.of("blueprints"));
            schedulePatternInvalidation(QueryKey.of("blueprint", "name"));
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
            scheduleInvalidation(QueryKey.of("persistentServer", event.getPersistentServerId()));
            schedulePatternInvalidation(QueryKey.of("persistentServers"));
            schedulePatternInvalidation(QueryKey.of("persistentServer", "name"));
            schedulePatternInvalidation(QueryKey.of("servers"));
            schedulePatternInvalidation(QueryKey.of("server"));
        }));

        subscriptions.add(eventApi.persistentServer().onDeleted(event -> {
            scheduleInvalidation(QueryKey.of("persistentServer", event.getPersistentServerId()));
            schedulePatternInvalidation(QueryKey.of("persistentServers"));
            schedulePatternInvalidation(QueryKey.of("persistentServer", "name"));
        }));
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
