package app.simplecloud.api.cache;

import java.time.Duration;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Query cache interface providing TanStack Query-like caching semantics.
 *
 * <p>Key features:
 * <ul>
 *   <li><b>Stale-while-revalidate</b>: Returns cached data immediately, refetches in background if stale</li>
 *   <li><b>Query deduplication</b>: Multiple simultaneous identical requests share one network call</li>
 *   <li><b>Automatic garbage collection</b>: Unused cache entries are evicted after cache time</li>
 * </ul>
 *
 * <p>Example usage:
 * <pre>{@code
 * // Manual cache invalidation
 * cloudApi.cache().invalidate(QueryKey.of("server", serverId));
 * cloudApi.cache().invalidateAll(QueryKey.of("servers"));
 *
 * // Check cache state
 * CacheEntry<Server> entry = cloudApi.cache().get(QueryKey.of("server", serverId));
 * if (entry != null) {
 *     System.out.println("Cached at: " + entry.getFetchedAt());
 * }
 *
 * // Get statistics
 * System.out.println(cloudApi.cache().getStats());
 * }</pre>
 */
public interface QueryCache {

    /**
     * Fetches data using the cache with stale-while-revalidate semantics.
     *
     * <p>Behavior:
     * <ol>
     *   <li>If fresh data exists in cache, returns it immediately</li>
     *   <li>If stale data exists, returns it and triggers background refetch</li>
     *   <li>If no data exists, fetches from source and caches result</li>
     * </ol>
     *
     * <p>Query deduplication: If multiple calls with the same key are made simultaneously,
     * they share a single network request.
     *
     * @param key the query key
     * @param fetcher the function to fetch fresh data from the network
     * @param <T> the data type
     * @return a CompletableFuture with the data (from cache or network)
     */
    <T> CompletableFuture<T> fetch(QueryKey key, Supplier<CompletableFuture<T>> fetcher);

    /**
     * Fetches data and retries direct source fetches when the result matches a transient-miss predicate.
     *
     * <p>This is intended for exact lookups where eventual consistency can briefly return
     * "not found" even though the resource exists a moment later. If all retries still match
     * the transient-miss predicate, the cache entry is invalidated to avoid negative caching.
     *
     * @param key the query key
     * @param fetcher the function to fetch fresh data from the source
     * @param isTransientMiss returns true when the result should trigger a direct retry
     * @param retryDelay delay between retries
     * @param maxRetries maximum number of direct retries after the initial fetch
     * @param <T> the data type
     * @return a CompletableFuture with the resolved data
     */
    default <T> CompletableFuture<T> fetchWithTransientMissRecovery(
            QueryKey key,
            Supplier<CompletableFuture<T>> fetcher,
            Predicate<T> isTransientMiss,
            Duration retryDelay,
            int maxRetries
    ) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(fetcher, "fetcher");
        Objects.requireNonNull(isTransientMiss, "isTransientMiss");
        Objects.requireNonNull(retryDelay, "retryDelay");

        if (maxRetries < 0) {
            throw new IllegalArgumentException("maxRetries must be >= 0");
        }
        if (retryDelay.isNegative()) {
            throw new IllegalArgumentException("retryDelay must be >= 0");
        }

        return fetch(key, fetcher)
                .thenCompose(result -> recoverTransientMiss(
                        key,
                        fetcher,
                        isTransientMiss,
                        retryDelay,
                        maxRetries,
                        result
                ));
    }

    /**
     * Gets the cached entry for a key, or null if not cached.
     *
     * @param key the query key
     * @param <T> the data type
     * @return the cache entry, or null if not in cache
     */
    <T> CacheEntry<T> get(QueryKey key);

    /**
     * Manually sets a cache entry.
     *
     * @param key the query key
     * @param data the data to cache
     * @param <T> the data type
     */
    <T> void set(QueryKey key, T data);

    /**
     * Invalidates a specific cache entry.
     *
     * @param key the exact key to invalidate
     */
    void invalidate(QueryKey key);

    /**
     * Invalidates all entries matching the given key pattern.
     *
     * <p>A key pattern matches all keys that start with the same parts.
     * For example, {@code invalidateAll(QueryKey.of("servers"))} invalidates:
     * <ul>
     *   <li>{@code QueryKey.of("servers")}</li>
     *   <li>{@code QueryKey.of("servers", "group", "Lobby")}</li>
     *   <li>{@code QueryKey.of("servers", "query", ...)}</li>
     * </ul>
     *
     * @param keyPattern the pattern to match
     */
    void invalidateAll(QueryKey keyPattern);

    /**
     * Invalidates all entries for a specific entity type.
     *
     * @param type the entity type to invalidate
     */
    void invalidateByType(EntityType type);

    /**
     * Clears all cache entries.
     */
    void clear();

    /**
     * Returns cache statistics for debugging and monitoring.
     *
     * @return the cache statistics
     */
    CacheStats getStats();

    /**
     * Returns all current cache keys (for debugging).
     *
     * @return the set of cached keys
     */
    Set<QueryKey> getKeys();

    private <T> CompletableFuture<T> recoverTransientMiss(
            QueryKey key,
            Supplier<CompletableFuture<T>> fetcher,
            Predicate<T> isTransientMiss,
            Duration retryDelay,
            int retriesRemaining,
            T result
    ) {
        if (!isTransientMiss.test(result)) {
            return CompletableFuture.completedFuture(result);
        }

        if (retriesRemaining == 0) {
            invalidate(key);
            return CompletableFuture.completedFuture(result);
        }

        return CompletableFuture
                .supplyAsync(
                        () -> null,
                        CompletableFuture.delayedExecutor(retryDelay.toMillis(), TimeUnit.MILLISECONDS)
                )
                .thenCompose(ignored -> fetcher.get())
                .thenCompose(retriedResult -> {
                    if (!isTransientMiss.test(retriedResult)) {
                        set(key, retriedResult);
                        return CompletableFuture.completedFuture(retriedResult);
                    }

                    return recoverTransientMiss(
                            key,
                            fetcher,
                            isTransientMiss,
                            retryDelay,
                            retriesRemaining - 1,
                            retriedResult
                    );
                });
    }
}
