package app.simplecloud.api.internal.cache;

import app.simplecloud.api.CloudApiException;
import app.simplecloud.api.cache.CacheConfig;
import app.simplecloud.api.cache.EntityType;
import app.simplecloud.api.cache.QueryKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static app.simplecloud.api.internal.cache.QueryCacheTestSupport.await;
import static app.simplecloud.api.internal.cache.QueryCacheTestSupport.awaitTrue;
import static app.simplecloud.api.internal.cache.QueryCacheTestSupport.cachedData;
import static app.simplecloud.api.internal.cache.QueryCacheTestSupport.countingFetcher;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the source requests that fetch() starts: sharing one request among callers, the
 * in-flight timeout, failures, and invalidate() or set() racing a running request.
 */
class QueryCacheImplInFlightTest {

    private static final Duration LONG_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration SHORT_TIMEOUT = Duration.ofMillis(200);
    private static final QueryKey SERVER_KEY = QueryKey.of("server", "server-1");
    private static final String COMPLETER_THREAD = "fetch-completer";

    private static void assertUnavailable(Throwable error, String message) {
        CloudApiException exception = assertInstanceOf(CloudApiException.class, error, message);
        assertEquals(CloudApiException.Reason.UNAVAILABLE, exception.getReason(), message);
    }

    private CacheConfig baseConfig() {
        return CacheConfig.builder()
                .defaultStaleTime(Duration.ofSeconds(30))
                .defaultCacheTime(Duration.ofMinutes(1))
                .build();
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
     * the caller's .orTimeout() fired perpetually. The fix fails the stored future after
     * {@code inFlightTimeout} so the map self-cleans.
     */
    @Test
    void fetch_hangingFetcher_callerFailsAfterInFlightTimeout() throws Exception {
        QueryCacheImpl cache = new QueryCacheImpl(baseConfig(), SHORT_TIMEOUT);
        QueryKey key = QueryKey.of("test", "hang");
        CompletableFuture<String> neverCompletes = new CompletableFuture<>();

        CompletableFuture<String> result = cache.fetch(key, () -> neverCompletes);

        ExecutionException ex = assertThrows(
                ExecutionException.class,
                () -> result.get(1, TimeUnit.SECONDS)
        );
        assertUnavailable(ex.getCause(),
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
     * Ensures late data does not reach the cache when the fetcher completes after the in-flight
     * timeout. The timeout already unregistered the request, so it no longer owns the key and
     * its result is not cached.
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

    @Test
    void fetch_sourceFails_fetchFromErrorHandlerAsksSourceAgain() throws Exception {
        QueryCacheImpl cache = new QueryCacheImpl(baseConfig(), LONG_TIMEOUT);
        CompletableFuture<String> failingSource = new CompletableFuture<>();
        AtomicInteger retryCalls = new AtomicInteger();
        Supplier<CompletableFuture<String>> retrySource = countingFetcher(retryCalls, "value");

        CompletableFuture<String> firstCall = cache.fetch(SERVER_KEY, () -> failingSource);
        CompletableFuture<String> retried = firstCall.exceptionallyCompose(error -> cache.fetch(SERVER_KEY, retrySource));
        failingSource.completeExceptionally(new RuntimeException("controller down"));

        assertEquals("value", await(retried));
        assertEquals(1, retryCalls.get());
    }

    @Test
    void fetch_inFlightTimeout_fetchFromErrorHandlerAsksSourceAgain() throws Exception {
        QueryCacheImpl cache = new QueryCacheImpl(baseConfig(), SHORT_TIMEOUT);
        CompletableFuture<String> neverCompletes = new CompletableFuture<>();
        AtomicInteger retryCalls = new AtomicInteger();
        Supplier<CompletableFuture<String>> retrySource = countingFetcher(retryCalls, "value");

        CompletableFuture<String> firstCall = cache.fetch(SERVER_KEY, () -> neverCompletes);
        CompletableFuture<String> retried = firstCall.exceptionallyCompose(error -> cache.fetch(SERVER_KEY, retrySource));

        assertEquals("value", await(retried));
        assertEquals(1, retryCalls.get());
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

    /**
     * Regression test: every caller used to get the one shared request, so a caller cancelling
     * its future, as kotlinx await() does when its coroutine is cancelled, cancelled the request
     * for all callers and nothing was cached.
     */
    @Test
    void fetch_firstCallerCancels_joinedCallerStillGetsDataAndItIsCached() throws Exception {
        QueryCacheImpl cache = new QueryCacheImpl(baseConfig(), LONG_TIMEOUT);
        CompletableFuture<String> source = new CompletableFuture<>();

        CompletableFuture<String> firstCaller = cache.fetch(SERVER_KEY, () -> source);
        CompletableFuture<String> joinedCaller = cache.fetch(SERVER_KEY, () -> source);
        firstCaller.cancel(false);
        source.complete("value");

        assertEquals("value", await(joinedCaller));
        assertEquals("value", cachedData(cache, SERVER_KEY));
    }

    @Test
    void fetch_joinedCallerCancels_firstCallerStillGetsDataAndItIsCached() throws Exception {
        QueryCacheImpl cache = new QueryCacheImpl(baseConfig(), LONG_TIMEOUT);
        CompletableFuture<String> source = new CompletableFuture<>();

        CompletableFuture<String> firstCaller = cache.fetch(SERVER_KEY, () -> source);
        CompletableFuture<String> joinedCaller = cache.fetch(SERVER_KEY, () -> source);
        joinedCaller.cancel(false);
        source.complete("value");

        assertEquals("value", await(firstCaller));
        assertEquals("value", cachedData(cache, SERVER_KEY));
    }

    @Test
    void fetch_sourceFails_callerSeesTheSourceErrorUnwrapped() throws Exception {
        QueryCacheImpl cache = new QueryCacheImpl(baseConfig(), LONG_TIMEOUT);
        RuntimeException controllerDown = new RuntimeException("controller down");
        Supplier<CompletableFuture<String>> failingSource = () -> CompletableFuture.failedFuture(controllerDown);

        CompletableFuture<String> result = cache.fetch(SERVER_KEY, failingSource);
        CompletableFuture<Throwable> seenError = result.handle((data, error) -> error);

        assertSame(controllerDown, await(seenError));
    }

    @Test
    void invalidate_whileFetchInFlight_callerGetsResultButCacheStaysEmpty() throws Exception {
        QueryCacheImpl cache = new QueryCacheImpl(baseConfig(), LONG_TIMEOUT);
        CompletableFuture<String> source = new CompletableFuture<>();

        CompletableFuture<String> result = cache.fetch(SERVER_KEY, () -> source);
        cache.invalidate(SERVER_KEY);
        source.complete("outdated");

        assertEquals("outdated", await(result));
        assertNull(cache.get(SERVER_KEY), "a result fetched before the invalidation must not be cached");
    }

    private static Stream<Arguments> invalidationsCoveringLobbyServers() {
        Consumer<QueryCacheImpl> invalidateAllServers = cache -> cache.invalidateAll(QueryKey.of("servers"));
        Consumer<QueryCacheImpl> invalidateServerType = cache -> cache.invalidateByType(EntityType.SERVER);
        Consumer<QueryCacheImpl> clear = QueryCacheImpl::clear;
        return Stream.of(
                Arguments.of("invalidateAll", invalidateAllServers),
                Arguments.of("invalidateByType", invalidateServerType),
                Arguments.of("clear", clear)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidationsCoveringLobbyServers")
    void invalidationCoveringKey_whileFetchInFlight_callerGetsResultButCacheStaysEmpty(
            String invalidationName,
            Consumer<QueryCacheImpl> invalidation
    ) throws Exception {
        QueryCacheImpl cache = new QueryCacheImpl(baseConfig(), LONG_TIMEOUT);
        QueryKey lobbyServersKey = QueryKey.of("servers", "group", "Lobby");
        CompletableFuture<String> source = new CompletableFuture<>();

        CompletableFuture<String> result = cache.fetch(lobbyServersKey, () -> source);
        invalidation.accept(cache);
        source.complete("outdated");

        assertEquals("outdated", await(result));
        assertNull(cache.get(lobbyServersKey), "a result fetched before " + invalidationName + " must not be cached");
    }

    @Test
    void invalidate_whileFetchInFlight_nextFetchStartsNewRequest() throws Exception {
        QueryCacheImpl cache = new QueryCacheImpl(baseConfig(), LONG_TIMEOUT);
        CompletableFuture<String> hanging = new CompletableFuture<>();
        AtomicInteger calls = new AtomicInteger();

        cache.fetch(SERVER_KEY, () -> hanging);
        cache.invalidate(SERVER_KEY);
        String result = await(cache.fetch(SERVER_KEY, countingFetcher(calls, "fresh")));

        assertEquals("fresh", result);
        assertEquals(1, calls.get());
    }

    @Test
    void olderRequestFinishingAfterInvalidation_doesNotDisplaceNewerRequest() throws Exception {
        QueryCacheImpl cache = new QueryCacheImpl(baseConfig(), LONG_TIMEOUT);
        CompletableFuture<String> olderSource = new CompletableFuture<>();
        CompletableFuture<String> newerSource = new CompletableFuture<>();

        cache.fetch(SERVER_KEY, () -> olderSource);
        cache.invalidate(SERVER_KEY);
        CompletableFuture<String> newerResult = cache.fetch(SERVER_KEY, () -> newerSource);
        olderSource.complete("before-invalidation");
        String cachedAfterOlder = cachedData(cache, SERVER_KEY);
        newerSource.complete("after-invalidation");
        await(newerResult);

        assertNull(cachedAfterOlder, "the request started before the invalidation must not cache");
        assertEquals("after-invalidation", cachedData(cache, SERVER_KEY), "the newer request must still own the key");
    }

    /**
     * Regression test: a finished fetch used to unregister itself and then cache its result in two
     * separate steps, so an invalidate() between them was overwritten. Building the entry reads the
     * key's entity type, so a key part whose toString() blocks pauses the fetch after its ownership
     * check and before its cache write.
     */
    @Test
    void invalidateWhileFetchIsCachingItsResult_removesTheResult() throws Exception {
        QueryCacheImpl cache = new QueryCacheImpl(baseConfig(), LONG_TIMEOUT);
        CountDownLatch cachingPaused = new CountDownLatch(1);
        CountDownLatch resumeCaching = new CountDownLatch(1);
        Object serverPrefix = new PausingKeyPart("server", COMPLETER_THREAD, cachingPaused, resumeCaching);
        QueryKey key = QueryKey.of(serverPrefix, "server-1");
        CompletableFuture<String> source = new CompletableFuture<>();
        CompletableFuture<String> result = cache.fetch(key, () -> source);

        Thread completer = new Thread(() -> source.complete("before-invalidation"), COMPLETER_THREAD);
        Thread invalidator = new Thread(() -> cache.invalidate(key), "invalidator");
        try {
            completer.start();
            assertTrue(cachingPaused.await(1, TimeUnit.SECONDS), "fetch never started caching");
            invalidator.start();
            awaitBlockedOrDone(invalidator);
        } finally {
            resumeCaching.countDown();
        }
        invalidator.join(1_000);
        completer.join(1_000);

        assertEquals("before-invalidation", await(result));
        assertNull(cache.get(key), "an invalidation during caching must win");
    }

    /**
     * Waits until the thread has finished or is blocked on a lock. The cache makes invalidate() wait
     * for the key's lock while a result is being cached; without that lock it finishes at once.
     */
    private static void awaitBlockedOrDone(Thread thread) throws InterruptedException {
        awaitTrue(
                () -> thread.getState() == Thread.State.BLOCKED || thread.getState() == Thread.State.TERMINATED,
                "invalidation neither finished nor waited for the key's lock"
        );
    }

    @Test
    void set_whileFetchInFlight_keepsSetValue() throws Exception {
        QueryCacheImpl cache = new QueryCacheImpl(baseConfig(), LONG_TIMEOUT);
        CompletableFuture<String> source = new CompletableFuture<>();

        CompletableFuture<String> result = cache.fetch(SERVER_KEY, () -> source);
        cache.set(SERVER_KEY, "written");
        source.complete("read-before-write");
        await(result);

        assertEquals("written", cachedData(cache, SERVER_KEY), "an older fetch must not overwrite set()");
    }

    /**
     * Regression test: a fetcher returning null used to throw out of fetch() while the
     * request stayed registered, so every caller for that key waited for the in-flight timeout.
     */
    @Test
    void fetch_fetcherReturnsNull_failsAtOnceAndAllowsRetry() throws Exception {
        QueryCacheImpl cache = new QueryCacheImpl(baseConfig(), LONG_TIMEOUT);

        CompletableFuture<String> result = cache.fetch(SERVER_KEY, () -> null);
        ExecutionException error = assertThrows(ExecutionException.class, () -> result.get(100, TimeUnit.MILLISECONDS));
        String retried = await(cache.fetch(SERVER_KEY, () -> CompletableFuture.completedFuture("value")));

        assertInstanceOf(NullPointerException.class, error.getCause());
        assertEquals("value", retried);
    }

    /**
     * A key part that reads as {@code name}. Read on the thread named {@code pausedThread}, it
     * signals {@code paused} and blocks until {@code resume} opens, failing if that takes over 2 s.
     */
    private record PausingKeyPart(String name, String pausedThread, CountDownLatch paused, CountDownLatch resume) {

        @Override
        public String toString() {
            if (Thread.currentThread().getName().equals(pausedThread)) {
                paused.countDown();
                awaitResume(resume);
            }
            return name;
        }

        private static void awaitResume(CountDownLatch resume) {
            boolean resumed;
            try {
                resumed = resume.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted while paused", e);
            }
            if (!resumed) {
                throw new IllegalStateException("test never resumed the paused thread");
            }
        }
    }
}
