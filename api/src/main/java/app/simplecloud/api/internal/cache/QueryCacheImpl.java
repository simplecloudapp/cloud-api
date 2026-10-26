package app.simplecloud.api.internal.cache;

import app.simplecloud.api.CloudApiException;
import app.simplecloud.api.cache.*;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;

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
    private static final Duration DEFAULT_IN_FLIGHT_TIMEOUT = Duration.ofSeconds(30);

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

    public QueryCacheImpl(CacheConfig config, Duration inFlightTimeout) {
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
                .expireAfter(new Expiry<QueryKey, CacheEntry<?>>() {
                    @Override
                    public long expireAfterCreate(QueryKey key, CacheEntry<?> value, long currentTime) {
                        return remainingNanos(value);
                    }

                    @Override
                    public long expireAfterUpdate(QueryKey key, CacheEntry<?> value, long currentTime, long currentDuration) {
                        return remainingNanos(value);
                    }

                    @Override
                    public long expireAfterRead(QueryKey key, CacheEntry<?> value, long currentTime, long currentDuration) {
                        return currentDuration;
                    }
                })
                .evictionListener((key, value, cause) -> evictionCount.incrementAndGet())
                .build();
    }

    private static long remainingNanos(CacheEntry<?> entry) {
        return Math.max(0, Duration.between(Instant.now(), entry.getExpiresAt()).toNanos());
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
        CompletableFuture<T> result = new CompletableFuture<>();
        CompletableFuture<?> existing = inFlightRequests.putIfAbsent(key, result);
        if (existing != null) {
            return (CompletableFuture<T>) existing;
        }

        result.whenComplete((data, error) -> inFlightRequests.remove(key, result));
        CompletableFuture.delayedExecutor(inFlightTimeout.toMillis(), TimeUnit.MILLISECONDS)
                .execute(() -> result.completeExceptionally(new CloudApiException(
                        CloudApiException.Reason.UNAVAILABLE,
                        "Controller did not respond within " + inFlightTimeout.toMillis() + " ms"
                )));

        CompletableFuture<T> source;
        try {
            source = fetcher.get();
        } catch (Throwable t) {
            result.completeExceptionally(t);
            return result;
        }

        source.whenComplete((data, error) -> {
            if (error != null) {
                result.completeExceptionally(error);
                return;
            }
            if (inFlightRequests.remove(key, result)) {
                Instant now = Instant.now();
                cache.put(key, new CacheEntry<>(data, now, now.plus(getCacheTime(key))));
            }
            result.complete(data);
        });

        return result;
    }

    private <T> void triggerBackgroundRevalidation(QueryKey key, Supplier<CompletableFuture<T>> fetcher) {
        // Only revalidate if not already revalidating
        if (inFlightRequests.containsKey(key)) {
            return;
        }

        try {
            revalidationExecutor.execute(() -> fetchWithDeduplication(key, fetcher).exceptionally(e -> {
                LOGGER.log(Level.WARNING, "Background revalidation failed for " + key, e);
                return null;
            }));
        } catch (RejectedExecutionException e) {
            LOGGER.log(Level.FINE, "Skipping background revalidation for " + key + " after shutdown", e);
        }
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
        inFlightRequests.remove(key);
        cache.invalidate(key);
    }

    @Override
    public void invalidateAll(QueryKey keyPattern) {
        inFlightRequests.keySet().removeIf(keyPattern::matches);
        cache.asMap().keySet().removeIf(keyPattern::matches);
    }

    @Override
    public void invalidateByType(EntityType type) {
        inFlightRequests.keySet().removeIf(key -> key.getEntityType() == type);
        cache.asMap().keySet().removeIf(key -> key.getEntityType() == type);
    }

    @Override
    public void clear() {
        inFlightRequests.clear();
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
        inFlightRequests.remove(key);
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
