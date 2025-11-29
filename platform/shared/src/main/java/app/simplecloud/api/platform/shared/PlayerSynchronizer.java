package app.simplecloud.api.platform.shared;

import app.simplecloud.api.CloudApi;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public class PlayerSynchronizer {

    private final CloudApi cloudApi;
    private final Supplier<Long> getCurrentOnlineCount;
    private final String currentServerId;
    
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> periodicTask;
    private ScheduledFuture<?> debounceTask;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Object debounceLock = new Object();

    public PlayerSynchronizer(CloudApi cloudApi, Supplier<Long> getCurrentOnlineCount) {
        this.cloudApi = cloudApi;
        this.getCurrentOnlineCount = getCurrentOnlineCount;
        this.currentServerId = System.getenv("SIMPLECLOUD_UNIQUE_ID");
    }

    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
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
        synchronized (debounceLock) {
            if (debounceTask != null && !debounceTask.isDone()) {
                debounceTask.cancel(false);
            }

            debounceTask = scheduler.schedule(() -> {
                try {
                    Map<String, Object> properties = new HashMap<>();
                    properties.put("player-count", getCurrentOnlineCount.get().intValue());
                    cloudApi.server().updateServerProperties(currentServerId, properties).join();
                } catch (Exception e) {
                    System.err.println("Error updating player count: " + e.getMessage());
                    e.printStackTrace();
                }
            }, 800, TimeUnit.MILLISECONDS);
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

