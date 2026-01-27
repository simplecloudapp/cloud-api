package app.simplecloud.api.cache;

/**
 * Cache statistics for monitoring and debugging.
 *
 * <p>Example usage:
 * <pre>{@code
 * CacheStats stats = cloudApi.cache().getStats();
 * System.out.println(stats);
 * // Output: CacheStats[hits=42, misses=5, staleHits=3, evictions=0, entries=12, hitRate=89.36%]
 * }</pre>
 */
public final class CacheStats {

    private final long hitCount;
    private final long missCount;
    private final long staleHitCount;
    private final long evictionCount;
    private final long entryCount;
    private final double hitRate;

    public CacheStats(long hitCount, long missCount, long staleHitCount,
                      long evictionCount, long entryCount, double hitRate) {
        this.hitCount = hitCount;
        this.missCount = missCount;
        this.staleHitCount = staleHitCount;
        this.evictionCount = evictionCount;
        this.entryCount = entryCount;
        this.hitRate = hitRate;
    }

    /**
     * Returns the number of cache hits (fresh data returned).
     */
    public long getHitCount() {
        return hitCount;
    }

    /**
     * Returns the number of cache misses (network request required).
     */
    public long getMissCount() {
        return missCount;
    }

    /**
     * Returns the number of stale hits (stale data returned with background refresh).
     */
    public long getStaleHitCount() {
        return staleHitCount;
    }

    /**
     * Returns the number of evictions.
     */
    public long getEvictionCount() {
        return evictionCount;
    }

    /**
     * Returns the current number of entries in the cache.
     */
    public long getEntryCount() {
        return entryCount;
    }

    /**
     * Returns the cache hit rate (0.0 to 1.0).
     */
    public double getHitRate() {
        return hitRate;
    }

    @Override
    public String toString() {
        return String.format(
                "CacheStats[hits=%d, misses=%d, staleHits=%d, evictions=%d, entries=%d, hitRate=%.2f%%]",
                hitCount, missCount, staleHitCount, evictionCount, entryCount, hitRate * 100
        );
    }
}
