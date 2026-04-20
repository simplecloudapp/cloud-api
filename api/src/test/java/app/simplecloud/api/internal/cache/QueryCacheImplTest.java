package app.simplecloud.api.internal.cache;

import app.simplecloud.api.cache.CacheConfig;
import app.simplecloud.api.cache.CacheEntry;
import app.simplecloud.api.cache.QueryKey;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueryCacheImplTest {

    private static final Duration LONG_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration SHORT_TIMEOUT = Duration.ofMillis(200);

    private CacheConfig baseConfig() {
        return CacheConfig.builder()
                .defaultStaleTime(Duration.ofSeconds(30))
                .defaultCacheTime(Duration.ofMinutes(1))
                .build();
    }

    @Test
    void fetch_cacheMiss_populatesCache() throws Exception {
        QueryCacheImpl cache = new QueryCacheImpl(baseConfig(), LONG_TIMEOUT);
        QueryKey key = QueryKey.of("test", "populate");

        String result = cache.fetch(key, () -> CompletableFuture.completedFuture("value"))
                .get(1, TimeUnit.SECONDS);

        assertEquals("value", result);

        CacheEntry<String> entry = cache.get(key);
        assertNotNull(entry, "cache should contain the entry after a successful miss");
        assertEquals("value", entry.getData());
    }

    @Test
    void fetch_cacheHit_doesNotInvokeFetcher() throws Exception {
        QueryCacheImpl cache = new QueryCacheImpl(baseConfig(), LONG_TIMEOUT);
        QueryKey key = QueryKey.of("test", "hit");
        AtomicInteger calls = new AtomicInteger();

        cache.fetch(key, () -> {
            calls.incrementAndGet();
            return CompletableFuture.completedFuture("v1");
        }).get(1, TimeUnit.SECONDS);

        String result = cache.fetch(key, () -> {
            calls.incrementAndGet();
            return CompletableFuture.completedFuture("v2");
        }).get(1, TimeUnit.SECONDS);

        assertEquals("v1", result);
        assertEquals(1, calls.get(), "second call must be served from cache");
    }

    @Test
    void fetch_concurrentCalls_deduplicateIntoSingleFetch() throws Exception {
        QueryCacheImpl cache = new QueryCacheImpl(baseConfig(), LONG_TIMEOUT);
        QueryKey key = QueryKey.of("test", "dedup");
        AtomicInteger calls = new AtomicInteger();
        CompletableFuture<String> gate = new CompletableFuture<>();

        CompletableFuture<String> a = cache.fetch(key, () -> {
            calls.incrementAndGet();
            return gate;
        });
        CompletableFuture<String> b = cache.fetch(key, () -> {
            calls.incrementAndGet();
            return gate;
        });

        gate.complete("shared");

        assertEquals("shared", a.get(1, TimeUnit.SECONDS));
        assertEquals("shared", b.get(1, TimeUnit.SECONDS));
        assertEquals(1, calls.get(), "concurrent callers must share one fetch");
    }

    /**
     * Regression test for the zombie in-flight bug.
     *
     * <p>Before the fix, if the upstream fetcher hung forever, the in-flight map entry
     * was never removed. Every subsequent call dedup'd into the hanging future and
     * the caller's .orTimeout() fired perpetually. The fix wraps the stored future in
     * orTimeout so the map self-cleans after {@code inFlightTimeout}.
     */
    @Test
    void fetch_hangingFetcher_storedFutureTimesOut() throws Exception {
        QueryCacheImpl cache = new QueryCacheImpl(baseConfig(), SHORT_TIMEOUT);
        QueryKey key = QueryKey.of("test", "hang");
        CompletableFuture<String> neverCompletes = new CompletableFuture<>();

        CompletableFuture<String> result = cache.fetch(key, () -> neverCompletes);

        ExecutionException ex = assertThrows(
                ExecutionException.class,
                () -> result.get(1, TimeUnit.SECONDS)
        );
        assertInstanceOf(TimeoutException.class, ex.getCause(),
                "hanging fetcher must time out via in-flight timeout");
    }

    /**
     * After the in-flight timeout fires, the next call must NOT see the zombie future.
     * This is the key behavioral guarantee the fix provides.
     */
    @Test
    void fetch_afterInFlightTimeout_subsequentCallGetsFreshFetch() throws Exception {
        QueryCacheImpl cache = new QueryCacheImpl(baseConfig(), SHORT_TIMEOUT);
        QueryKey key = QueryKey.of("test", "recover");
        CompletableFuture<String> neverCompletes = new CompletableFuture<>();

        CompletableFuture<String> firstCall = cache.fetch(key, () -> neverCompletes);
        assertThrows(ExecutionException.class, () -> firstCall.get(1, TimeUnit.SECONDS));

        AtomicInteger calls = new AtomicInteger();
        String recovered = cache.fetch(key, () -> {
            calls.incrementAndGet();
            return CompletableFuture.completedFuture("fresh");
        }).get(1, TimeUnit.SECONDS);

        assertEquals("fresh", recovered);
        assertEquals(1, calls.get(), "fetcher must be invoked fresh; zombie future must not be reused");
        assertNotNull(cache.get(key), "successful recovery should populate cache");
    }

    /**
     * Ensures cache.put does NOT run if the fetcher completes after the in-flight timeout.
     * JDK CompletableFuture.uniApply short-circuits when the downstream stage is already
     * completed exceptionally, so late data must not pollute the cache.
     */
    @Test
    void fetch_fetcherCompletesAfterTimeout_doesNotPolluteCache() throws Exception {
        QueryCacheImpl cache = new QueryCacheImpl(baseConfig(), SHORT_TIMEOUT);
        QueryKey key = QueryKey.of("test", "late");
        CompletableFuture<String> lateCompletion = new CompletableFuture<>();

        CompletableFuture<String> result = cache.fetch(key, () -> lateCompletion);
        assertThrows(ExecutionException.class, () -> result.get(1, TimeUnit.SECONDS));

        // Simulate the HTTP call finally returning after the caller already gave up.
        lateCompletion.complete("late-data");
        Thread.sleep(100);

        assertNull(cache.get(key),
                "cache must not be populated by a fetcher that completed after the timeout");
    }

    @Test
    void fetch_fetcherThrows_clearsInFlightAndAllowsRetry() throws Exception {
        QueryCacheImpl cache = new QueryCacheImpl(baseConfig(), LONG_TIMEOUT);
        QueryKey key = QueryKey.of("test", "throws");

        CompletableFuture<String> failing = new CompletableFuture<>();
        failing.completeExceptionally(new RuntimeException("boom"));

        CompletableFuture<String> firstCall = cache.fetch(key, () -> failing);
        assertThrows(ExecutionException.class, () -> firstCall.get(1, TimeUnit.SECONDS));

        String recovered = cache.fetch(key, () -> CompletableFuture.completedFuture("ok"))
                .get(1, TimeUnit.SECONDS);
        assertEquals("ok", recovered, "failed attempt must not leave a zombie in-flight entry");
    }

    /**
     * Directly exercises {@link QueryCacheImpl#fetchWithDeduplication} with a supplier
     * whose returned future never completes. The stored in-flight future must still
     * complete (exceptionally, via the in-flight timeout) so the map entry is cleared
     * and subsequent callers are not trapped in the zombie dedup slot.
     */
    @Test
    void fetchWithDeduplication_supplierReturnsNeverCompletingFuture_returnedFutureStillCompletes()
            throws Exception {
        QueryCacheImpl cache = new QueryCacheImpl(baseConfig(), SHORT_TIMEOUT);
        QueryKey key = QueryKey.of("test", "never-completes");
        CompletableFuture<String> neverCompletes = new CompletableFuture<>();

        CompletableFuture<String> returned = cache.fetchWithDeduplication(key, () -> neverCompletes);

        // The key guarantee: the returned future must not hang forever.
        ExecutionException ex = assertThrows(
                ExecutionException.class,
                () -> returned.get(1, TimeUnit.SECONDS),
                "fetchWithDeduplication must return a future that eventually completes"
        );
        assertInstanceOf(TimeoutException.class, ex.getCause(),
                "in-flight timeout must complete the future exceptionally with TimeoutException");
        assertTrue(returned.isDone(), "returned future must be marked done after timeout");
    }

    @Test
    void fetch_sharedHangingFuture_allCallersSeeTimeout() throws Exception {
        QueryCacheImpl cache = new QueryCacheImpl(baseConfig(), SHORT_TIMEOUT);
        QueryKey key = QueryKey.of("test", "shared-hang");
        CompletableFuture<String> neverCompletes = new CompletableFuture<>();

        CompletableFuture<String> a = cache.fetch(key, () -> neverCompletes);
        CompletableFuture<String> b = cache.fetch(key, () -> {
            throw new AssertionError("fetcher must not be invoked for dedup'd call");
        });

        assertThrows(ExecutionException.class, () -> a.get(1, TimeUnit.SECONDS));
        assertThrows(ExecutionException.class, () -> b.get(1, TimeUnit.SECONDS));
        assertTrue(a.isCompletedExceptionally());
        assertTrue(b.isCompletedExceptionally());
    }
}
