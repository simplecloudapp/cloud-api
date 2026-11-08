package app.simplecloud.api.cache;

/**
 * Cache statistics for monitoring and debugging.
 *
 * @param hitCount lookups answered from a fresh entry
 * @param missCount lookups that had to fetch from the controller
 * @param staleHitCount lookups answered from a stale entry while it was refreshed
 * @param evictionCount entries removed from the cache
 * @param entryCount entries currently in the cache
 * @param hitRate share of lookups answered from the cache, from 0 to 1
 */
public record CacheStats(
        long hitCount, long missCount, long staleHitCount, long evictionCount, long entryCount, double hitRate) {

    /** @deprecated use {@link #hitCount()} */
    @Deprecated
    public long getHitCount() {
        return hitCount;
    }

    /** @deprecated use {@link #missCount()} */
    @Deprecated
    public long getMissCount() {
        return missCount;
    }

    /** @deprecated use {@link #staleHitCount()} */
    @Deprecated
    public long getStaleHitCount() {
        return staleHitCount;
    }

    /** @deprecated use {@link #evictionCount()} */
    @Deprecated
    public long getEvictionCount() {
        return evictionCount;
    }

    /** @deprecated use {@link #entryCount()} */
    @Deprecated
    public long getEntryCount() {
        return entryCount;
    }

    /** @deprecated use {@link #hitRate()} */
    @Deprecated
    public double getHitRate() {
        return hitRate;
    }

    @Override
    public String toString() {
        return String.format(
                "CacheStats[hits=%d, misses=%d, staleHits=%d, evictions=%d, entries=%d, hitRate=%.2f%%]",
                hitCount, missCount, staleHitCount, evictionCount, entryCount, hitRate * 100);
    }
}
