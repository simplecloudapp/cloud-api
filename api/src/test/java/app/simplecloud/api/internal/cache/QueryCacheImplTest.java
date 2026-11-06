package app.simplecloud.api.internal.cache;

import app.simplecloud.api.cache.CacheConfig;
import app.simplecloud.api.cache.EntityCacheConfig;
import app.simplecloud.api.cache.EntityType;
import app.simplecloud.api.cache.QueryKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueryCacheImplTest {

    private static final QueryKey KEY = QueryKey.of("server", "a");

    private QueryCacheImpl cache = new QueryCacheImpl(CacheConfig.DEFAULT);

    @AfterEach
    void tearDown() {
        cache.shutdown();
    }

    @Test
    void freshValueIsServedFromCache() {
        AtomicInteger calls = new AtomicInteger();

        cache.fetch(KEY, () -> completed(calls, "value")).join();
        String second = cache.fetch(KEY, () -> completed(calls, "other")).join();

        assertEquals("value", second);
        assertEquals(1, calls.get());
    }

    @Test
    void concurrentFetchesShareOneRequest() {
        AtomicInteger calls = new AtomicInteger();
        CompletableFuture<String> pending = new CompletableFuture<>();

        CompletableFuture<String> first = cache.fetch(KEY, () -> {
            calls.incrementAndGet();
            return pending;
        });
        CompletableFuture<String> second = cache.fetch(KEY, () -> completed(calls, "other"));
        pending.complete("value");

        assertEquals("value", first.join());
        assertEquals("value", second.join());
        assertEquals(1, calls.get());
    }

    @Test
    void staleValueIsReturnedWhileItIsRefreshed() throws InterruptedException {
        cache.fetch(KEY, () -> CompletableFuture.completedFuture("old")).join();
        cache.get(KEY).markStale();
        CountDownLatch refreshed = new CountDownLatch(1);

        String value = cache.fetch(KEY, () -> {
                    refreshed.countDown();
                    return CompletableFuture.completedFuture("new");
                })
                .join();

        assertEquals("old", value);
        assertTrue(refreshed.await(5, TimeUnit.SECONDS));
    }

    @Test
    void failedFetchIsNotCached() {
        AtomicInteger calls = new AtomicInteger();

        CompletableFuture<String> failed = cache.fetch(KEY, () -> {
            calls.incrementAndGet();
            return CompletableFuture.failedFuture(new IllegalStateException("controller down"));
        });
        assertThrows(CompletionException.class, failed::join);

        assertEquals("value", cache.fetch(KEY, () -> completed(calls, "value")).join());
        assertEquals(2, calls.get());
    }

    @Test
    void invalidateAllRemovesEveryKeyWithThePrefix() {
        cache.set(QueryKey.of("servers"), "all");
        cache.set(QueryKey.of("servers", "group", "lobby"), "lobby");
        cache.set(QueryKey.of("groups"), "groups");

        cache.invalidateAll(QueryKey.of("servers"));

        assertNull(cache.get(QueryKey.of("servers")));
        assertNull(cache.get(QueryKey.of("servers", "group", "lobby")));
        assertNotNull(cache.get(QueryKey.of("groups")));
    }

    @Test
    void disabledEntityTypeIsNeverCached() {
        cache.shutdown();
        cache = new QueryCacheImpl(CacheConfig.builder()
                .entityConfig(
                        EntityType.SERVER, EntityCacheConfig.builder().enabled(false).build())
                .build());
        AtomicInteger calls = new AtomicInteger();

        cache.fetch(KEY, () -> completed(calls, "value")).join();
        cache.fetch(KEY, () -> completed(calls, "value")).join();

        assertEquals(2, calls.get());
        assertNull(cache.get(KEY));
    }

    @Test
    void transientMissIsRetriedAndTheHitIsCached() {
        AtomicInteger calls = new AtomicInteger();

        String value = cache.fetchWithTransientMissRecovery(
                        KEY,
                        () -> completed(calls, calls.get() == 0 ? null : "value"),
                        Objects::isNull,
                        Duration.ZERO,
                        2)
                .join();

        assertEquals("value", value);
        assertEquals(2, calls.get());
        assertEquals("value", cache.get(KEY).getData());
    }

    @Test
    void persistentMissIsNotCached() {
        AtomicInteger calls = new AtomicInteger();

        String value = cache.<String>fetchWithTransientMissRecovery(
                        KEY, () -> completed(calls, null), Objects::isNull, Duration.ZERO, 1)
                .join();

        assertNull(value);
        assertEquals(2, calls.get());
        assertNull(cache.get(KEY));
    }

    @Test
    void setReplacesCachedValue() {
        cache.fetch(KEY, () -> CompletableFuture.completedFuture("old")).join();

        cache.set(KEY, "new");

        assertSame("new", cache.fetch(KEY, () -> CompletableFuture.completedFuture("fetched")).join());
    }

    private static <T> CompletableFuture<T> completed(AtomicInteger calls, T value) {
        calls.incrementAndGet();
        return CompletableFuture.completedFuture(value);
    }
}
