package app.simplecloud.api.platform.shared;

import app.simplecloud.api.CloudApi;
import app.simplecloud.api.runtime.SimpleCloudRuntime;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class PlayerSynchronizer {

    private final CloudApi cloudApi;
    private final OnlinePlayerCount onlinePlayers;
    private final String currentServerId;
    
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> periodicTask;
    private ScheduledFuture<?> debounceTask;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Object debounceLock = new Object();
    private final Object publishLock = new Object();
    private Integer lastPublishedCount;

    public PlayerSynchronizer(CloudApi cloudApi, OnlinePlayerCount onlinePlayers) {
        this.cloudApi = cloudApi;
        this.onlinePlayers = onlinePlayers;
        this.currentServerId = SimpleCloudRuntime.serverId();
    }

    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }

        synchronized (publishLock) {
            lastPublishedCount = null;
        }
        scheduler = Executors.newScheduledThreadPool(2);

        scheduler.execute(() -> {
            try {
                Map<String, Object> properties = new HashMap<>();
                properties.put("player-count-ping", "skip");
                cloudApi.server().updateServerProperties(currentServerId, properties).join();
            } catch (Exception e) {
                System.err.println("Error updating server property: " + e.getMessage());
                e.printStackTrace();
            }
        });

        periodicTask = scheduler.scheduleAtFixedRate(
            this::updatePlayerCount,
            15,
            15,
            TimeUnit.SECONDS
        );
    }

    public void updatePlayerCount() {
        if (!running.get()) {
            return;
        }

        synchronized (debounceLock) {
            if (!running.get()) {
                return;
            }

            if (debounceTask != null && !debounceTask.isDone()) {
                debounceTask.cancel(false);
            }

            debounceTask = scheduler.schedule(this::publishPlayerCount, 800, TimeUnit.MILLISECONDS);
        }
    }

    void publishPlayerCount() {
        synchronized (publishLock) {
            try {
                int playerCount = onlinePlayers.onlinePlayerCount();
                // Skip unchanged counts: every property patch is a controller write plus a ServerUpdated event.
                if (lastPublishedCount != null && lastPublishedCount == playerCount) {
                    return;
                }

                Map<String, Object> properties = new HashMap<>();
                properties.put("player-count", playerCount);
                cloudApi.server().updateServerProperties(currentServerId, properties).join();
                lastPublishedCount = playerCount;
            } catch (Exception e) {
                System.err.println("Error updating player count: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }

        if (periodicTask != null) {
            periodicTask.cancel(false);
        }

        synchronized (debounceLock) {
            if (debounceTask != null) {
                debounceTask.cancel(false);
            }
        }

        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}
