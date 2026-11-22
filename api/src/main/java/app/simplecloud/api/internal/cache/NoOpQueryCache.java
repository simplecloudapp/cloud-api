package app.simplecloud.api.internal.cache;

import app.simplecloud.api.cache.*;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * A no-op cache implementation used when caching is disabled.
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
    }

    @Override
    public void invalidate(QueryKey key) {
    }

    @Override
    public void invalidateAll(QueryKey keyPattern) {
    }

    @Override
    public void invalidateByType(EntityType type) {
    }

    @Override
    public void clear() {
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
