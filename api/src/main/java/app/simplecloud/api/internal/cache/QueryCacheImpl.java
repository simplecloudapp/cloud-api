package app.simplecloud.api.internal.cache;

import app.simplecloud.api.cache.*;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Caffeine-backed implementation of QueryCache with stale-while-revalidate semantics.
 */
public class QueryCacheImpl implements QueryCache {

    private static final Logger LOGGER = Logger.getLogger(QueryCacheImpl.class.getName());
    private static final Duration DEFAULT_IN_FLIGHT_TIMEOUT = Duration.ofSeconds(5);

    private final CacheConfig config;
    private final Duration inFlightTimeout;
    private final Cache<QueryKey, CacheEntry<?>> cache;
    private final ConcurrentMap<QueryKey, CompletableFuture<?>> inFlightRequests;

    private final AtomicLong hitCount = new AtomicLong();
    private final AtomicLong missCount = new AtomicLong();
    private final AtomicLong staleHitCount = new AtomicLong();
    private final AtomicLong evictionCount = new AtomicLong();

    private final ExecutorService revalidationExecutor;

    public QueryCacheImpl(CacheConfig config) {
        this(config, DEFAULT_IN_FLIGHT_TIMEOUT);
    }

    QueryCacheImpl(CacheConfig config, Duration inFlightTimeout) {
        this.config = config;
        this.inFlightTimeout = inFlightTimeout;
        this.inFlightRequests = new ConcurrentHashMap<>();
        this.revalidationExecutor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "QueryCache-Revalidation");
            t.setDaemon(true);
            return t;
        });

        this.cache = Caffeine.newBuilder()
                .maximumSize(config.getMaxEntries())
                .expireAfterWrite(config.getDefaultCacheTime())
                .evictionListener((key, value, cause) -> evictionCount.incrementAndGet())
                .build();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> CompletableFuture<T> fetch(QueryKey key, Supplier<CompletableFuture<T>> fetcher) {
        if (!config.isEnabled()) {
            return fetcher.get();
        }

        // Check if caching is disabled for this entity type
        EntityType entityType = key.getEntityType();
        if (entityType != null && config.getEntityConfigs().containsKey(entityType)) {
            EntityCacheConfig entityConfig = config.getEntityConfigs().get(entityType);
            if (!entityConfig.isEnabled()) {
                return fetcher.get();
            }
        }

        CacheEntry<T> entry = (CacheEntry<T>) cache.getIfPresent(key);
        Duration staleTime = getStaleTime(key);

        if (entry != null) {
            if (entry.isExpired()) {
                cache.invalidate(key);
            } else {
                Instant staleThreshold = Instant.now().minus(staleTime);

                if (!entry.isStale(staleThreshold)) {
                    // Fresh data - return immediately
                    hitCount.incrementAndGet();
                    return CompletableFuture.completedFuture(entry.getData());
                } else {
                    // Stale data - return cached, revalidate in background
                    staleHitCount.incrementAndGet();
                    triggerBackgroundRevalidation(key, fetcher);
                    return CompletableFuture.completedFuture(entry.getData());
                }
            }
        }

        // No cached data - fetch with deduplication
        missCount.incrementAndGet();
        return fetchWithDeduplication(key, fetcher);
    }

    @SuppressWarnings("unchecked")
    <T> CompletableFuture<T> fetchWithDeduplication(QueryKey key, Supplier<CompletableFuture<T>> fetcher) {
        // Query deduplication: if there's already an in-flight request for this key, share it
        CompletableFuture<T> existing = (CompletableFuture<T>) inFlightRequests.get(key);
        if (existing != null) {
            return existing;
        }

        CompletableFuture<T> future = fetcher.get()
                .thenApply(data -> {
                    // Cache the result
                    Duration cacheTime = getCacheTime(key);
                    Instant now = Instant.now();
                    CacheEntry<T> newEntry = new CacheEntry<>(data, now, now.plus(cacheTime));
                    cache.put(key, newEntry);
                    return data;
                })
                .orTimeout(inFlightTimeout.toMillis(), TimeUnit.MILLISECONDS)
                .whenComplete((result, error) -> inFlightRequests.remove(key));

        // Use putIfAbsent to handle race conditions
        CompletableFuture<?> previousFuture = inFlightRequests.putIfAbsent(key, future);
        if (previousFuture != null) {
            // Another thread beat us to it, use their future
            return (CompletableFuture<T>) previousFuture;
        }

        // If the future already completed before putIfAbsent ran (e.g. the fetcher
        // returned a pre-completed future), whenComplete's remove was a no-op. Clean
        // up here so subsequent callers don't dedup into a stale completed entry.
        if (future.isDone()) {
            inFlightRequests.remove(key, future);
        }

        return future;
    }

    private <T> void triggerBackgroundRevalidation(QueryKey key, Supplier<CompletableFuture<T>> fetcher) {
        // Only revalidate if not already revalidating
        if (inFlightRequests.containsKey(key)) {
            return;
        }

        revalidationExecutor.submit(() -> {
            fetchWithDeduplication(key, fetcher).exceptionally(e -> {
                // Log but don't fail - we already returned stale data
                LOGGER.log(Level.WARNING, "Background revalidation failed for " + key, e);
                return null;
            });
        });
    }

    private Duration getStaleTime(QueryKey key) {
        EntityType type = key.getEntityType();
        if (type != null && config.getEntityConfigs().containsKey(type)) {
            EntityCacheConfig entityConfig = config.getEntityConfigs().get(type);
            if (entityConfig.getStaleTime() != null) {
                return entityConfig.getStaleTime();
            }
        }
        return config.getDefaultStaleTime();
    }

    private Duration getCacheTime(QueryKey key) {
        EntityType type = key.getEntityType();
        if (type != null && config.getEntityConfigs().containsKey(type)) {
            EntityCacheConfig entityConfig = config.getEntityConfigs().get(type);
            if (entityConfig.getCacheTime() != null) {
                return entityConfig.getCacheTime();
            }
        }
        return config.getDefaultCacheTime();
    }

    @Override
    public void invalidate(QueryKey key) {
        cache.invalidate(key);
    }

    @Override
    public void invalidateAll(QueryKey keyPattern) {
        cache.asMap().keySet().stream()
                .filter(keyPattern::matches)
                .forEach(cache::invalidate);
    }

    @Override
    public void invalidateByType(EntityType type) {
        cache.asMap().keySet().stream()
                .filter(key -> key.getEntityType() == type)
                .forEach(cache::invalidate);
    }

    @Override
    public void clear() {
        cache.invalidateAll();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> CacheEntry<T> get(QueryKey key) {
        return (CacheEntry<T>) cache.getIfPresent(key);
    }

    @Override
    public <T> void set(QueryKey key, T data) {
        Duration cacheTime = getCacheTime(key);
        Instant now = Instant.now();
        cache.put(key, new CacheEntry<>(data, now, now.plus(cacheTime)));
    }

    @Override
    public CacheStats getStats() {
        long hits = hitCount.get();
        long staleHits = staleHitCount.get();
        long misses = missCount.get();
        long total = hits + staleHits + misses;
        double hitRate = total > 0 ? (double) (hits + staleHits) / total : 0.0;

        return new CacheStats(
                hits,
                misses,
                staleHits,
                evictionCount.get(),
                cache.estimatedSize(),
                hitRate
        );
    }

    @Override
    public Set<QueryKey> getKeys() {
        return cache.asMap().keySet();
    }

    /**
     * Shuts down the background revalidation executor.
     * Call this when the CloudApi is being shut down.
     */
    public void shutdown() {
        revalidationExecutor.shutdown();
        try {
            if (!revalidationExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                revalidationExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            revalidationExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
