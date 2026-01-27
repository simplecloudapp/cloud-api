package app.simplecloud.api.internal.cache;

import app.simplecloud.api.cache.*;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * A no-op cache implementation used when caching is disabled.
 * All operations pass through to the underlying fetcher without caching.
 */
public class NoOpQueryCache implements QueryCache {

    @Override
    public <T> CompletableFuture<T> fetch(QueryKey key, Supplier<CompletableFuture<T>> fetcher) {
        return fetcher.get();
    }

    @Override
    public <T> CacheEntry<T> get(QueryKey key) {
        return null;
    }

    @Override
    public <T> void set(QueryKey key, T data) {
        // No-op
    }

    @Override
    public void invalidate(QueryKey key) {
        // No-op
    }

    @Override
    public void invalidateAll(QueryKey keyPattern) {
        // No-op
    }

    @Override
    public void invalidateByType(EntityType type) {
        // No-op
    }

    @Override
    public void clear() {
        // No-op
    }

    @Override
    public CacheStats getStats() {
        return new CacheStats(0, 0, 0, 0, 0, 0.0);
    }

    @Override
    public Set<QueryKey> getKeys() {
        return Collections.emptySet();
    }
}
