package app.simplecloud.api.internal.cache;

import app.simplecloud.api.CloudApiException;
import app.simplecloud.api.cache.*;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
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
    private final ConcurrentMap<QueryKey, CompletableFuture<?>> inFlightRequests = new ConcurrentHashMap<>();

    private final AtomicLong hitCount = new AtomicLong();
    private final AtomicLong missCount = new AtomicLong();
    private final AtomicLong staleHitCount = new AtomicLong();
    private final AtomicLong evictionCount = new AtomicLong();

    private final ExecutorService revalidationExecutor = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "QueryCache-Revalidation");
        thread.setDaemon(true);
        return thread;
    });

    public QueryCacheImpl(CacheConfig config) {
        this(config, DEFAULT_IN_FLIGHT_TIMEOUT);
    }

    public QueryCacheImpl(CacheConfig config, Duration inFlightTimeout) {
        this.config = config;
        this.inFlightTimeout = inFlightTimeout;
        this.cache = Caffeine.newBuilder()
                .maximumSize(config.getMaxEntries())
                .expireAfter(new ExpireAtEntryExpiresAt())
                .evictionListener((key, value, cause) -> evictionCount.incrementAndGet())
                .build();
    }

    @Override
    public <T> CompletableFuture<T> fetch(QueryKey key, Supplier<CompletableFuture<T>> fetcher) {
        if (!isCachingEnabledFor(key)) {
            return fetcher.get();
        }

        CacheEntry<T> entry = get(key);
        if (entry == null) {
            missCount.incrementAndGet();
            return fetchWithDeduplication(key, fetcher);
        }

        if (isStale(key, entry)) {
            staleHitCount.incrementAndGet();
            triggerBackgroundRevalidation(key, fetcher);
        } else {
            hitCount.incrementAndGet();
        }
        return CompletableFuture.completedFuture(entry.getData());
    }

    /**
     * Fetches from the source, sharing one source request among all callers for the same key.
     *
     * <p>The shared future fails after {@code inFlightTimeout} even if the source never answers,
     * so a hanging request cannot block the key forever. A result that arrives after the key was
     * invalidated or timed out is returned to its callers but not cached.
     */
    @SuppressWarnings("unchecked")
    <T> CompletableFuture<T> fetchWithDeduplication(QueryKey key, Supplier<CompletableFuture<T>> fetcher) {
        CompletableFuture<T> request = new CompletableFuture<>();
        CompletableFuture<?> existingRequest = inFlightRequests.putIfAbsent(key, request);
        if (existingRequest != null) {
            return (CompletableFuture<T>) existingRequest;
        }

        request.whenComplete((data, error) -> inFlightRequests.remove(key, request));
        failAfterInFlightTimeout(request);

        // A fetcher that throws or returns null fails the shared request instead of leaving it to the timeout.
        try {
            CompletableFuture<T> sourceResponse = Objects.requireNonNull(fetcher.get(), "fetcher returned null");
            sourceResponse.whenComplete((data, error) -> completeRequest(key, request, data, error));
        } catch (Throwable error) {
            request.completeExceptionally(error);
        }
        return request;
    }

    private void failAfterInFlightTimeout(CompletableFuture<?> request) {
        Executor afterTimeout = CompletableFuture.delayedExecutor(inFlightTimeout.toMillis(), TimeUnit.MILLISECONDS);
        afterTimeout.execute(() -> request.completeExceptionally(newTimeoutException()));
    }

    private CloudApiException newTimeoutException() {
        return new CloudApiException(
                CloudApiException.Reason.UNAVAILABLE,
                "Controller did not respond within " + inFlightTimeout.toMillis() + " ms"
        );
    }

    private <T> void completeRequest(QueryKey key, CompletableFuture<T> request, T data, Throwable error) {
        if (error != null) {
            request.completeExceptionally(error);
            return;
        }

        // Only the request still registered for the key may cache; invalidate() or the timeout may have dropped it.
        boolean stillCurrent = inFlightRequests.remove(key, request);
        if (stillCurrent) {
            cache.put(key, newEntry(key, data));
        }
        request.complete(data);
    }

    private <T> void triggerBackgroundRevalidation(QueryKey key, Supplier<CompletableFuture<T>> fetcher) {
        if (inFlightRequests.containsKey(key)) {
            return;
        }

        try {
            revalidationExecutor.execute(() -> revalidate(key, fetcher));
        } catch (RejectedExecutionException e) {
            LOGGER.log(Level.FINE, "Skipping background revalidation for " + key + " after shutdown", e);
        }
    }

    private <T> void revalidate(QueryKey key, Supplier<CompletableFuture<T>> fetcher) {
        CompletableFuture<T> request = fetchWithDeduplication(key, fetcher);
        request.exceptionally(error -> {
            LOGGER.log(Level.WARNING, "Background revalidation failed for " + key, error);
            return null;
        });
    }

    private boolean isCachingEnabledFor(QueryKey key) {
        if (!config.isEnabled()) {
            return false;
        }
        EntityCacheConfig entityConfig = entityConfigFor(key);
        return entityConfig == null || entityConfig.isEnabled();
    }

    private boolean isStale(QueryKey key, CacheEntry<?> entry) {
        Instant staleThreshold = Instant.now().minus(staleTimeFor(key));
        return entry.isStale(staleThreshold);
    }

    private <T> CacheEntry<T> newEntry(QueryKey key, T data) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(cacheTimeFor(key));
        return new CacheEntry<>(data, now, expiresAt);
    }

    private Duration staleTimeFor(QueryKey key) {
        EntityCacheConfig entityConfig = entityConfigFor(key);
        if (entityConfig == null || entityConfig.getStaleTime() == null) {
            return config.getDefaultStaleTime();
        }
        return entityConfig.getStaleTime();
    }

    private Duration cacheTimeFor(QueryKey key) {
        EntityCacheConfig entityConfig = entityConfigFor(key);
        if (entityConfig == null || entityConfig.getCacheTime() == null) {
            return config.getDefaultCacheTime();
        }
        return entityConfig.getCacheTime();
    }

    /**
     * Returns the overrides for the key's entity type, or null when the key has no known type
     * or its type has no overrides.
     */
    private EntityCacheConfig entityConfigFor(QueryKey key) {
        EntityType type = key.getEntityType();
        if (type == null) {
            return null;
        }
        return config.getEntityConfigs().get(type);
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
        inFlightRequests.remove(key);
        cache.put(key, newEntry(key, data));
    }

    @Override
    public CacheStats getStats() {
        long hits = hitCount.get();
        long staleHits = staleHitCount.get();
        long misses = missCount.get();
        long lookups = hits + staleHits + misses;
        double hitRate = lookups > 0 ? (double) (hits + staleHits) / lookups : 0.0;

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
        return Set.copyOf(cache.asMap().keySet());
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

    /**
     * Makes Caffeine drop an entry at its {@link CacheEntry#getExpiresAt()}, which {@link #set}
     * and a finished fetch reset on every write. Reads do not extend it.
     */
    private static final class ExpireAtEntryExpiresAt implements Expiry<QueryKey, CacheEntry<?>> {

        @Override
        public long expireAfterCreate(QueryKey key, CacheEntry<?> entry, long currentTime) {
            return nanosUntilExpiry(entry);
        }

        @Override
        public long expireAfterUpdate(QueryKey key, CacheEntry<?> entry, long currentTime, long currentDuration) {
            return nanosUntilExpiry(entry);
        }

        @Override
        public long expireAfterRead(QueryKey key, CacheEntry<?> entry, long currentTime, long currentDuration) {
            return currentDuration;
        }

        private static long nanosUntilExpiry(CacheEntry<?> entry) {
            Duration remaining = Duration.between(Instant.now(), entry.getExpiresAt());
            return Math.max(0, remaining.toNanos());
        }
    }
}
