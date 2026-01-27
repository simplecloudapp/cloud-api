package app.simplecloud.api.cache;

import java.time.Instant;

/**
 * A cache entry with metadata for staleness tracking.
 *
 * @param <T> the type of cached data
 */
public final class CacheEntry<T> {

    private final T data;
    private final Instant fetchedAt;
    private final Instant expiresAt;
    private volatile boolean markedStale;

    public CacheEntry(T data, Instant fetchedAt, Instant expiresAt) {
        this.data = data;
        this.fetchedAt = fetchedAt;
        this.expiresAt = expiresAt;
        this.markedStale = false;
    }

    /**
     * Returns the cached data.
     */
    public T getData() {
        return data;
    }

    /**
     * Returns when this data was fetched.
     */
    public Instant getFetchedAt() {
        return fetchedAt;
    }

    /**
     * Returns when this entry expires and should be evicted.
     */
    public Instant getExpiresAt() {
        return expiresAt;
    }

    /**
     * Checks if this entry is stale based on the given threshold.
     *
     * @param staleThreshold data fetched before this instant is considered stale
     * @return true if the data is stale
     */
    public boolean isStale(Instant staleThreshold) {
        return markedStale || fetchedAt.isBefore(staleThreshold);
    }

    /**
     * Marks this entry as stale, triggering a background refresh on next access.
     */
    public void markStale() {
        this.markedStale = true;
    }

    /**
     * Checks if this entry has expired and should be evicted.
     */
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    @Override
    public String toString() {
        return "CacheEntry{" +
                "fetchedAt=" + fetchedAt +
                ", expiresAt=" + expiresAt +
                ", markedStale=" + markedStale +
                '}';
    }
}
