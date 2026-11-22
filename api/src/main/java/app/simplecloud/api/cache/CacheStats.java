package app.simplecloud.api.cache;

/**
 * Cache statistics for monitoring and debugging.
 */
public record CacheStats(
        long hitCount,
        long missCount,
        long staleHitCount,
        long evictionCount,
        long entryCount,
        double hitRate
) {

    /**
     * @deprecated use {@link #hitCount()}; will be removed in 1.0.0
     */
    @Deprecated(forRemoval = true)
    public long getHitCount() {
        return hitCount;
    }

    /**
     * @deprecated use {@link #missCount()}; will be removed in 1.0.0
     */
    @Deprecated(forRemoval = true)
    public long getMissCount() {
        return missCount;
    }

    /**
     * @deprecated use {@link #staleHitCount()}; will be removed in 1.0.0
     */
    @Deprecated(forRemoval = true)
    public long getStaleHitCount() {
        return staleHitCount;
    }

    /**
     * @deprecated use {@link #evictionCount()}; will be removed in 1.0.0
     */
    @Deprecated(forRemoval = true)
    public long getEvictionCount() {
        return evictionCount;
    }

    /**
     * @deprecated use {@link #entryCount()}; will be removed in 1.0.0
     */
    @Deprecated(forRemoval = true)
    public long getEntryCount() {
        return entryCount;
    }

    /**
     * @deprecated use {@link #hitRate()}; will be removed in 1.0.0
     */
    @Deprecated(forRemoval = true)
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
