package app.simplecloud.api.internal.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import app.simplecloud.api.CloudApiException;
import app.simplecloud.api.cache.CacheConfig;
import app.simplecloud.api.cache.EntityCacheConfig;
import app.simplecloud.api.cache.EntityType;
import app.simplecloud.api.cache.QueryKey;
import app.simplecloud.api.support.Await;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class QueryCacheImplTest {

    private static final QueryKey KEY = QueryKey.of("test", "key");

    private final AtomicInteger fetches = new AtomicInteger();
    private QueryCacheImpl cache = new QueryCacheImpl(CacheConfig.builder()
            .defaultStaleTime(Duration.ofMinutes(1))
            .defaultCacheTime(Duration.ofMinutes(5))
            .build());

    @AfterEach
    void shutdown() {
        cache.shutdown();
    }

    @Test
    void servesCachedValueWithoutFetchingAgain() {
        assertEquals("first", cache.fetch(KEY, () -> fetch("first")).join());
        assertEquals("first", cache.fetch(KEY, () -> fetch("second")).join());

        assertEquals(1, fetches.get());
    }

    @Test
    void concurrentMissesShareOneFetch() {
        CompletableFuture<String> pending = new CompletableFuture<>();

        CompletableFuture<String> first = cache.fetch(KEY, () -> {
            fetches.incrementAndGet();
            return pending;
        });
        CompletableFuture<String> second = cache.fetch(KEY, () -> fetch("other"));
        pending.complete("value");

        assertEquals("value", first.join());
        assertEquals("value", second.join());
        assertEquals(1, fetches.get());
    }

    @Test
    void doesNotCacheFailures() {
        CompletableFuture<String> failed =
                cache.fetch(KEY, () -> CompletableFuture.failedFuture(new IllegalStateException("down")));

        assertThrows(CompletionException.class, failed::join);
        assertEquals("value", cache.fetch(KEY, () -> fetch("value")).join());
    }

    @Test
    void returnsStaleValueAndRefreshesItInBackground() throws InterruptedException {
        cache.fetch(KEY, () -> fetch("old")).join();
        cache.get(KEY).markStale();

        assertEquals("old", cache.fetch(KEY, () -> fetch("new")).join());

        Await.until(() -> "new".equals(cache.get(KEY).getData()), "the stale entry was refreshed");
    }

    @Test
    void failsRequestThatNeverCompletes() {
        cache.shutdown();
        cache = new QueryCacheImpl(CacheConfig.DEFAULT, Duration.ofMillis(100));

        CompletionException failure = assertThrows(
                CompletionException.class,
                () -> cache.fetch(KEY, CompletableFuture::new).join());

        CloudApiException exception = assertInstanceOf(CloudApiException.class, failure.getCause());
        assertEquals(CloudApiException.Reason.UNAVAILABLE, exception.getReason());
    }

    @Test
    void invalidateAllRemovesOnlyKeysWithThePrefix() {
        QueryKey byName = QueryKey.of("group", "name", "Lobby");
        QueryKey byId = QueryKey.of("group", "8f2c4b1e-0000-0000-0000-000000000000");
        cache.set(byName, "by-name");
        cache.set(byId, "by-id");

        cache.invalidateAll(QueryKey.of("group", "name"));

        assertNull(cache.get(byName));
        assertEquals("by-id", cache.get(byId).getData());
    }

    @Test
    void invalidatingAKeyDropsTheRunningFetch() {
        CompletableFuture<String> pending = new CompletableFuture<>();
        cache.fetch(KEY, () -> pending);

        cache.invalidate(KEY);
        pending.complete("outdated");

        assertNull(cache.get(KEY));
    }

    @Test
    void entityTypeWithDisabledCachingAlwaysFetches() {
        cache.shutdown();
        cache = new QueryCacheImpl(CacheConfig.builder()
                .entityConfig(
                        EntityType.GROUP,
                        EntityCacheConfig.builder().enabled(false).build())
                .build());
        QueryKey key = QueryKey.of("groups");

        cache.fetch(key, () -> fetch("first")).join();
        cache.fetch(key, () -> fetch("second")).join();

        assertEquals(2, fetches.get());
        assertNull(cache.get(key));
    }

    @Test
    void retriesTransientMissUntilValueAppears() {
        AtomicInteger calls = new AtomicInteger();

        String result = cache.fetchWithTransientMissRecovery(
                        KEY,
                        () -> CompletableFuture.completedFuture(calls.incrementAndGet() < 3 ? null : "value"),
                        Objects::isNull,
                        Duration.ZERO,
                        2)
                .join();

        assertEquals("value", result);
        assertEquals("value", cache.get(KEY).getData());
    }

    @Test
    void doesNotKeepMissAfterRetriesAreUsedUp() {
        String result = cache.fetchWithTransientMissRecovery(
                        KEY, () -> CompletableFuture.<String>completedFuture(null), Objects::isNull, Duration.ZERO, 1)
                .join();

        assertNull(result);
        assertNull(cache.get(KEY));
    }

    @Test
    void setReplacesCachedValue() {
        cache.fetch(KEY, () -> fetch("fetched")).join();
        String updated = "updated";

        cache.set(KEY, updated);

        assertSame(updated, cache.fetch(KEY, () -> fetch("other")).join());
    }

    private CompletableFuture<String> fetch(String value) {
        fetches.incrementAndGet();
        return CompletableFuture.completedFuture(value);
    }
}
