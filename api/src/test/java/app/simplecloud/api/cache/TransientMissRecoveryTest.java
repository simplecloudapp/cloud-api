package app.simplecloud.api.cache;

import app.simplecloud.api.CloudApiException;
import app.simplecloud.api.internal.cache.NoOpQueryCache;
import app.simplecloud.api.internal.cache.QueryCacheImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Covers {@link QueryCache#fetchWithTransientMissRecovery}, which retries the lookup through the
 * cache when it briefly reports "not found".
 */
class TransientMissRecoveryTest {

    private static final QueryKey KEY = QueryKey.of("server", "server-1");
    private static final Duration RETRY_DELAY = Duration.ofMillis(10);
    private static final int MAX_RETRIES = 2;

    private final QueryCacheImpl cache = new QueryCacheImpl(CacheConfig.DEFAULT, Duration.ofSeconds(2));

    @AfterEach
    void shutdownCache() {
        cache.shutdown();
    }

    /**
     * Returns the given results in order, one per call; null stands for "not found".
     */
    private static final class ScriptedSource implements Supplier<CompletableFuture<String>> {
        private final List<String> results;
        private final AtomicInteger calls = new AtomicInteger();

        ScriptedSource(String... results) {
            this.results = Arrays.asList(results);
        }

        @Override
        public CompletableFuture<String> get() {
            int call = calls.getAndIncrement();
            return CompletableFuture.completedFuture(results.get(call));
        }
    }

    /**
     * Answers "not found" on the first call and returns {@link #retry} from the second call on.
     */
    private static final class MissThenPendingRetry implements Supplier<CompletableFuture<String>> {
        private final CompletableFuture<String> retry = new CompletableFuture<>();
        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public CompletableFuture<String> get() {
            int call = calls.incrementAndGet();
            if (call == 1) {
                return CompletableFuture.completedFuture(null);
            }
            return retry;
        }
    }

    private static CompletableFuture<String> startRecovery(
            Supplier<CompletableFuture<String>> source,
            QueryCache queryCache
    ) {
        return queryCache.fetchWithTransientMissRecovery(KEY, source, Objects::isNull, RETRY_DELAY, MAX_RETRIES);
    }

    private String recover(Supplier<CompletableFuture<String>> source) throws Exception {
        return startRecovery(source, cache).get(1, TimeUnit.SECONDS);
    }

    private static void awaitRetryStarted(MissThenPendingRetry source) throws InterruptedException {
        awaitTrue(() -> source.calls.get() >= 2, "retry never reached the source");
    }

    private void awaitMissCount(long expected) throws InterruptedException {
        awaitTrue(() -> cache.getStats().getMissCount() >= expected, "retry never looked up the key");
    }

    private static void awaitTrue(BooleanSupplier condition, String message) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 2_000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError(message);
            }
            Thread.sleep(5);
        }
    }

    private String cachedData() {
        CacheEntry<String> entry = cache.get(KEY);
        if (entry == null) {
            return null;
        }
        return entry.getData();
    }

    @Test
    void foundOnFirstFetch_doesNotRetry() throws Exception {
        ScriptedSource source = new ScriptedSource("server");

        String result = recover(source);

        assertEquals("server", result);
        assertEquals(1, source.calls.get());
    }

    @Test
    void foundOnRetry_returnsAndCachesIt() throws Exception {
        ScriptedSource source = new ScriptedSource(null, "server");

        String result = recover(source);

        assertEquals("server", result);
        assertEquals("server", cachedData(), "the result found on retry must be cached");
        assertEquals(2, source.calls.get());
    }

    @Test
    void missOnEveryAttempt_returnsMissAndDropsCachedMiss() throws Exception {
        ScriptedSource source = new ScriptedSource(null, null, null);

        String result = recover(source);

        assertNull(result);
        assertNull(cache.get(KEY), "a final miss must not stay cached as a negative result");
        assertEquals(1 + MAX_RETRIES, source.calls.get());
    }

    @Test
    void zeroRetries_dropsCachedMissWithoutRetrying() throws Exception {
        ScriptedSource source = new ScriptedSource((String) null);

        CompletableFuture<String> result = cache.fetchWithTransientMissRecovery(KEY, source, Objects::isNull, RETRY_DELAY, 0);

        assertNull(result.get(1, TimeUnit.SECONDS));
        assertNull(cache.get(KEY));
        assertEquals(1, source.calls.get());
    }

    @Test
    void cachedMissFromEarlierLookup_isRetriedAndReplaced() throws Exception {
        cache.set(KEY, null);
        ScriptedSource source = new ScriptedSource("server");

        String result = recover(source);

        assertEquals("server", result);
        assertEquals("server", cachedData());
    }

    @Test
    void retryFails_propagatesError() {
        AtomicInteger calls = new AtomicInteger();
        RuntimeException controllerDown = new RuntimeException("controller down");
        Supplier<CompletableFuture<String>> source = () -> {
            int call = calls.incrementAndGet();
            if (call == 1) {
                return CompletableFuture.completedFuture(null);
            }
            return CompletableFuture.failedFuture(controllerDown);
        };

        ExecutionException error = assertThrows(ExecutionException.class, () -> recover(source));

        assertSame(controllerDown, error.getCause());
    }

    @Test
    void customMissPredicate_retriesEmptyLists() throws Exception {
        Queue<List<String>> results = new ArrayDeque<>(List.of(List.of(), List.of("server")));
        Supplier<CompletableFuture<List<String>>> source = () -> CompletableFuture.completedFuture(results.remove());

        List<String> result = cache.fetchWithTransientMissRecovery(KEY, source, List::isEmpty, RETRY_DELAY, MAX_RETRIES)
                .get(1, TimeUnit.SECONDS);

        assertEquals(List.of("server"), result);
    }

    @Test
    void invalidArguments_areRejected() {
        Supplier<CompletableFuture<String>> source = () -> CompletableFuture.completedFuture("server");
        Duration negativeDelay = Duration.ofMillis(-1);

        assertThrows(IllegalArgumentException.class,
                () -> cache.fetchWithTransientMissRecovery(KEY, source, Objects::isNull, RETRY_DELAY, -1));
        assertThrows(IllegalArgumentException.class,
                () -> cache.fetchWithTransientMissRecovery(KEY, source, Objects::isNull, negativeDelay, 1));
        assertThrows(NullPointerException.class,
                () -> cache.fetchWithTransientMissRecovery(KEY, source, null, RETRY_DELAY, 1));
    }

    /**
     * Regression test: a retry used to store its result unconditionally, so a snapshot taken
     * before an invalidating event was cached after it.
     */
    @Test
    void invalidatedWhileRetryRuns_retryResultIsNotCached() throws Exception {
        MissThenPendingRetry source = new MissThenPendingRetry();

        CompletableFuture<String> result = startRecovery(source, cache);
        awaitRetryStarted(source);
        cache.invalidate(KEY);
        source.retry.complete("before-invalidation");

        assertEquals("before-invalidation", result.get(1, TimeUnit.SECONDS));
        assertNull(cache.get(KEY));
    }

    @Test
    void setWhileRetryRuns_keepsWrittenValue() throws Exception {
        MissThenPendingRetry source = new MissThenPendingRetry();

        CompletableFuture<String> result = startRecovery(source, cache);
        awaitRetryStarted(source);
        cache.set(KEY, "written");
        source.retry.complete("read-before-write");
        result.get(1, TimeUnit.SECONDS);

        assertEquals("written", cachedData());
    }

    /**
     * Regression test: running out of retries used to invalidate the key unconditionally,
     * deleting data written while the last retry ran.
     */
    @Test
    void retriesExhausted_keepsValueWrittenMeanwhile() throws Exception {
        MissThenPendingRetry source = new MissThenPendingRetry();

        CompletableFuture<String> result = cache.fetchWithTransientMissRecovery(KEY, source, Objects::isNull, RETRY_DELAY, 1);
        awaitRetryStarted(source);
        cache.set(KEY, "written");
        source.retry.complete(null);

        assertNull(result.get(1, TimeUnit.SECONDS));
        assertEquals("written", cachedData());
    }

    /**
     * Regression test: retries used to call the source directly, so a hanging retry never
     * hit the cache's in-flight timeout.
     */
    @Test
    void retryHangs_failsAfterInFlightTimeout() throws Exception {
        QueryCacheImpl shortTimeoutCache = new QueryCacheImpl(CacheConfig.DEFAULT, Duration.ofMillis(100));
        MissThenPendingRetry source = new MissThenPendingRetry();

        try {
            CompletableFuture<String> result = startRecovery(source, shortTimeoutCache);
            ExecutionException error = assertThrows(ExecutionException.class, () -> result.get(1, TimeUnit.SECONDS));

            assertInstanceOf(CloudApiException.class, error.getCause());
        } finally {
            shortTimeoutCache.shutdown();
        }
    }

    @Test
    void cachingDisabled_retriesWithoutCaching() throws Exception {
        NoOpQueryCache noOpCache = new NoOpQueryCache();
        ScriptedSource source = new ScriptedSource(null, "server");

        String result = startRecovery(source, noOpCache).get(1, TimeUnit.SECONDS);

        assertEquals("server", result);
        assertEquals(2, source.calls.get());
    }

    @Test
    void entityTypeDisabled_retriesWithoutCachingOrCounting() throws Exception {
        CacheConfig serversDisabled = CacheConfig.builder()
                .entityConfig(EntityType.SERVER, EntityCacheConfig.builder().enabled(false).build())
                .build();
        QueryCacheImpl serversDisabledCache = new QueryCacheImpl(serversDisabled, Duration.ofSeconds(2));
        ScriptedSource source = new ScriptedSource(null, "server");

        try {
            String result = startRecovery(source, serversDisabledCache).get(1, TimeUnit.SECONDS);
            CacheStats stats = serversDisabledCache.getStats();

            assertEquals("server", result);
            assertNull(serversDisabledCache.get(KEY));
            assertEquals(0, stats.getHitCount() + stats.getStaleHitCount() + stats.getMissCount());
        } finally {
            serversDisabledCache.shutdown();
        }
    }

    @Test
    void retry_joinsRequestAlreadyRunningForKey() throws Exception {
        ScriptedSource recoverySource = new ScriptedSource((String) null);
        CompletableFuture<String> runningRequest = new CompletableFuture<>();
        Duration retryDelay = Duration.ofMillis(500);

        CompletableFuture<String> result = cache.fetchWithTransientMissRecovery(
                KEY, recoverySource, Objects::isNull, retryDelay, MAX_RETRIES);
        cache.invalidate(KEY);
        cache.fetch(KEY, () -> runningRequest);
        // Misses: the first lookup, the running request, and the retry joining it while it still runs.
        awaitMissCount(3);
        runningRequest.complete("server");

        assertEquals("server", result.get(1, TimeUnit.SECONDS));
        assertEquals(1, recoverySource.calls.get(), "the retry must reuse the running request, not ask the source");
    }
}
