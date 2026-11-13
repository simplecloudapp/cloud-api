package app.simplecloud.api.internal.cache;

import app.simplecloud.api.cache.CacheConfig;
import app.simplecloud.api.cache.CacheEntry;
import app.simplecloud.api.cache.CacheStats;
import app.simplecloud.api.cache.EntityCacheConfig;
import app.simplecloud.api.cache.EntityType;
import app.simplecloud.api.cache.QueryKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers stale-while-revalidate, per-entity configuration, invalidation racing in-flight
 * requests, expiry and statistics.
 */
class QueryCacheImplBehaviorTest {

    private static final Duration IN_FLIGHT_TIMEOUT = Duration.ofSeconds(2);
    private static final QueryKey SERVER_KEY = QueryKey.of("server", "server-1");
    private static final QueryKey GROUP_KEY = QueryKey.of("group", "group-1");
    private static final String COMPLETER_THREAD = "fetch-completer";

    private QueryCacheImpl cache;

    @AfterEach
    void shutdownCache() {
        if (cache != null) {
            cache.shutdown();
        }
    }

    private QueryCacheImpl newCache(CacheConfig config) {
        cache = new QueryCacheImpl(config, IN_FLIGHT_TIMEOUT);
        return cache;
    }

    private static CacheConfig longLivedConfig() {
        return CacheConfig.builder()
                .defaultStaleTime(Duration.ofMinutes(1))
                .defaultCacheTime(Duration.ofMinutes(1))
                .entityConfig(EntityType.SERVER, EntityCacheConfig.builder().build())
                .entityConfig(EntityType.GROUP, EntityCacheConfig.builder().build())
                .build();
    }

    private static <T> T await(CompletableFuture<T> future) throws Exception {
        return future.get(1, TimeUnit.SECONDS);
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

    private static Supplier<CompletableFuture<String>> countingFetcher(AtomicInteger calls, String value) {
        return () -> {
            calls.incrementAndGet();
            return CompletableFuture.completedFuture(value);
        };
    }

    private static String cachedData(QueryCacheImpl cache, QueryKey key) {
        CacheEntry<String> entry = cache.get(key);
        if (entry == null) {
            return null;
        }
        return entry.getData();
    }

    @Test
    void fetch_staleEntry_returnsCachedValueAndRefreshesInBackground() throws Exception {
        QueryCacheImpl cache = newCache(longLivedConfig());
        cache.set(SERVER_KEY, "old");
        cache.get(SERVER_KEY).markStale();

        String result = await(cache.fetch(SERVER_KEY, () -> CompletableFuture.completedFuture("new")));

        assertEquals("old", result, "a stale entry is served immediately");
        awaitTrue(() -> "new".equals(cachedData(cache, SERVER_KEY)), "background refresh must replace the entry");
    }

    @Test
    void fetch_staleEntryReadTwiceDuringRefresh_startsOneRefresh() throws Exception {
        QueryCacheImpl cache = newCache(longLivedConfig());
        cache.set(SERVER_KEY, "old");
        cache.get(SERVER_KEY).markStale();
        AtomicInteger calls = new AtomicInteger();
        CompletableFuture<String> refresh = new CompletableFuture<>();
        Supplier<CompletableFuture<String>> slowFetcher = () -> {
            calls.incrementAndGet();
            return refresh;
        };

        await(cache.fetch(SERVER_KEY, slowFetcher));
        awaitTrue(() -> calls.get() == 1, "first stale read must start a refresh");
        await(cache.fetch(SERVER_KEY, slowFetcher));
        refresh.complete("new");

        awaitTrue(() -> "new".equals(cachedData(cache, SERVER_KEY)), "refresh must replace the entry");
        assertEquals(1, calls.get(), "a second stale read must join the running refresh");
    }

    @Test
    void fetch_backgroundRefreshFails_keepsServingStaleValue() throws Exception {
        QueryCacheImpl cache = newCache(longLivedConfig());
        cache.set(SERVER_KEY, "old");
        cache.get(SERVER_KEY).markStale();
        AtomicInteger calls = new AtomicInteger();
        Supplier<CompletableFuture<String>> failingFetcher = () -> {
            calls.incrementAndGet();
            return CompletableFuture.failedFuture(new RuntimeException("controller down"));
        };

        String first = await(cache.fetch(SERVER_KEY, failingFetcher));
        awaitTrue(() -> calls.get() == 1, "refresh must have been attempted");
        String second = await(cache.fetch(SERVER_KEY, failingFetcher));

        assertEquals("old", first);
        assertEquals("old", second);
    }

    @Test
    void fetch_cachingDisabled_callsSourceEveryTimeAndStoresNothing() throws Exception {
        QueryCacheImpl cache = newCache(CacheConfig.DISABLED);
        AtomicInteger calls = new AtomicInteger();

        await(cache.fetch(SERVER_KEY, countingFetcher(calls, "value")));
        await(cache.fetch(SERVER_KEY, countingFetcher(calls, "value")));

        assertEquals(2, calls.get());
        assertNull(cache.get(SERVER_KEY));
    }

    @Test
    void fetch_entityTypeDisabled_bypassesCacheForThatTypeOnly() throws Exception {
        CacheConfig config = CacheConfig.builder()
                .entityConfig(EntityType.SERVER, EntityCacheConfig.builder().enabled(false).build())
                .entityConfig(EntityType.GROUP, EntityCacheConfig.builder().build())
                .build();
        QueryCacheImpl cache = newCache(config);
        AtomicInteger serverCalls = new AtomicInteger();
        AtomicInteger groupCalls = new AtomicInteger();

        await(cache.fetch(SERVER_KEY, countingFetcher(serverCalls, "server")));
        await(cache.fetch(SERVER_KEY, countingFetcher(serverCalls, "server")));
        await(cache.fetch(GROUP_KEY, countingFetcher(groupCalls, "group")));
        await(cache.fetch(GROUP_KEY, countingFetcher(groupCalls, "group")));

        assertEquals(2, serverCalls.get(), "disabled entity type must reach the source every time");
        assertEquals(1, groupCalls.get(), "other entity types stay cached");
    }

    @Test
    void fetch_entityStaleTimeOverride_appliesToThatTypeOnly() throws Exception {
        CacheConfig config = CacheConfig.builder()
                .defaultStaleTime(Duration.ofMinutes(1))
                .entityConfig(EntityType.SERVER, EntityCacheConfig.builder().staleTime(Duration.ZERO).build())
                .entityConfig(EntityType.GROUP, EntityCacheConfig.builder().build())
                .build();
        QueryCacheImpl cache = newCache(config);
        AtomicInteger serverCalls = new AtomicInteger();
        AtomicInteger groupCalls = new AtomicInteger();

        await(cache.fetch(SERVER_KEY, countingFetcher(serverCalls, "server")));
        await(cache.fetch(GROUP_KEY, countingFetcher(groupCalls, "group")));
        Thread.sleep(5);
        await(cache.fetch(SERVER_KEY, countingFetcher(serverCalls, "server")));
        await(cache.fetch(GROUP_KEY, countingFetcher(groupCalls, "group")));

        awaitTrue(() -> serverCalls.get() == 2, "server entry is stale at once and must be refreshed");
        assertEquals(1, groupCalls.get(), "group entry uses the one-minute default and stays fresh");
    }

    @Test
    void entry_isDroppedAfterCacheTime() throws Exception {
        CacheConfig config = CacheConfig.builder()
                .defaultCacheTime(Duration.ofMillis(50))
                .entityConfig(EntityType.SERVER, EntityCacheConfig.builder().build())
                .build();
        QueryCacheImpl cache = newCache(config);

        cache.set(SERVER_KEY, "value");

        assertNotNull(cache.get(SERVER_KEY));
        awaitTrue(() -> cache.get(SERVER_KEY) == null, "entry must expire after its cache time");
    }

    @Test
    void entry_usesEntityCacheTimeOverride() throws Exception {
        CacheConfig config = CacheConfig.builder()
                .defaultCacheTime(Duration.ofMinutes(1))
                .entityConfig(EntityType.SERVER, EntityCacheConfig.builder().cacheTime(Duration.ofMillis(50)).build())
                .entityConfig(EntityType.GROUP, EntityCacheConfig.builder().build())
                .build();
        QueryCacheImpl cache = newCache(config);

        cache.set(SERVER_KEY, "server");
        cache.set(GROUP_KEY, "group");

        awaitTrue(() -> cache.get(SERVER_KEY) == null, "server entry must expire after 50 ms");
        assertNotNull(cache.get(GROUP_KEY), "group entry uses the one-minute default");
    }

    @Test
    void invalidate_whileFetchInFlight_callerGetsResultButCacheStaysEmpty() throws Exception {
        QueryCacheImpl cache = newCache(longLivedConfig());
        CompletableFuture<String> source = new CompletableFuture<>();

        CompletableFuture<String> result = cache.fetch(SERVER_KEY, () -> source);
        cache.invalidate(SERVER_KEY);
        source.complete("outdated");

        assertEquals("outdated", await(result));
        assertNull(cache.get(SERVER_KEY), "a result fetched before the invalidation must not be cached");
    }

    @Test
    void invalidate_whileFetchInFlight_nextFetchStartsNewRequest() throws Exception {
        QueryCacheImpl cache = newCache(longLivedConfig());
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
        QueryCacheImpl cache = newCache(longLivedConfig());
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
        QueryCacheImpl cache = newCache(longLivedConfig());
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
        QueryCacheImpl cache = newCache(longLivedConfig());
        CompletableFuture<String> source = new CompletableFuture<>();

        CompletableFuture<String> result = cache.fetch(SERVER_KEY, () -> source);
        cache.set(SERVER_KEY, "written");
        source.complete("read-before-write");
        await(result);

        assertEquals("written", cachedData(cache, SERVER_KEY), "an older fetch must not overwrite set()");
    }

    @Test
    void invalidateAll_removesKeysWithThePrefixOnly() {
        QueryCacheImpl cache = newCache(longLivedConfig());
        QueryKey allServers = QueryKey.of("servers");
        QueryKey lobbyServers = QueryKey.of("servers", "group", "Lobby");
        QueryKey numericalServer = QueryKey.of("server", "numerical", "Lobby", 1);
        cache.set(allServers, "all");
        cache.set(lobbyServers, "lobby");
        cache.set(numericalServer, "lobby-1");

        cache.invalidateAll(QueryKey.of("servers"));

        assertNull(cache.get(allServers));
        assertNull(cache.get(lobbyServers));
        assertNotNull(cache.get(numericalServer), "\"server\" is not a prefix match for \"servers\"");
    }

    @Test
    void invalidateByType_removesThatTypeOnly() {
        QueryCacheImpl cache = newCache(longLivedConfig());
        QueryKey serverList = QueryKey.of("servers");
        cache.set(SERVER_KEY, "server");
        cache.set(serverList, "servers");
        cache.set(GROUP_KEY, "group");

        cache.invalidateByType(EntityType.SERVER);

        assertNull(cache.get(SERVER_KEY));
        assertNull(cache.get(serverList));
        assertNotNull(cache.get(GROUP_KEY));
    }

    @Test
    void clear_removesEverything() {
        QueryCacheImpl cache = newCache(longLivedConfig());
        cache.set(SERVER_KEY, "server");
        cache.set(GROUP_KEY, "group");

        cache.clear();

        assertEquals(Set.of(), cache.getKeys());
    }

    @Test
    void getKeys_returnsUnmodifiableSnapshot() {
        QueryCacheImpl cache = newCache(longLivedConfig());
        cache.set(SERVER_KEY, "server");

        Set<QueryKey> keys = cache.getKeys();
        cache.set(GROUP_KEY, "group");

        assertEquals(Set.of(SERVER_KEY), keys, "later writes must not show up in an earlier snapshot");
        assertThrows(UnsupportedOperationException.class, keys::clear);
        assertNotNull(cache.get(SERVER_KEY));
    }

    @Test
    void getStats_countsHitsStaleHitsAndMisses() throws Exception {
        QueryCacheImpl cache = newCache(longLivedConfig());
        AtomicInteger calls = new AtomicInteger();

        await(cache.fetch(SERVER_KEY, countingFetcher(calls, "value")));
        await(cache.fetch(SERVER_KEY, countingFetcher(calls, "value")));
        await(cache.fetch(SERVER_KEY, countingFetcher(calls, "value")));
        cache.get(SERVER_KEY).markStale();
        await(cache.fetch(SERVER_KEY, countingFetcher(calls, "value")));
        CacheStats stats = cache.getStats();

        assertEquals(1, stats.getMissCount());
        assertEquals(2, stats.getHitCount());
        assertEquals(1, stats.getStaleHitCount());
        assertEquals(0.75, stats.getHitRate(), 1e-9);
        assertEquals(1, stats.getEntryCount());
    }

    @Test
    void getStats_withoutLookups_reportsZeroHitRate() {
        QueryCacheImpl cache = newCache(longLivedConfig());

        CacheStats stats = cache.getStats();

        assertEquals(0.0, stats.getHitRate());
    }

    @Test
    void maxEntries_evictsAndCountsEvictions() throws Exception {
        CacheConfig config = CacheConfig.builder()
                .maxEntries(1)
                .build();
        QueryCacheImpl cache = newCache(config);

        cache.set(QueryKey.of("servers", 1), "a");
        cache.set(QueryKey.of("servers", 2), "b");
        cache.set(QueryKey.of("servers", 3), "c");

        awaitTrue(() -> cache.getStats().getEvictionCount() >= 2, "entries beyond maxEntries must be evicted");
        awaitTrue(() -> cache.getStats().getEntryCount() <= 1, "cache must shrink to maxEntries");
    }

    /**
     * Regression test: a fetcher returning null used to throw out of fetch() while the
     * request stayed registered, so every caller for that key waited for the in-flight timeout.
     */
    @Test
    void fetch_fetcherReturnsNull_failsAtOnceAndAllowsRetry() throws Exception {
        QueryCacheImpl cache = newCache(longLivedConfig());

        CompletableFuture<String> result = cache.fetch(SERVER_KEY, () -> null);
        ExecutionException error = assertThrows(ExecutionException.class, () -> result.get(100, TimeUnit.MILLISECONDS));
        String retried = await(cache.fetch(SERVER_KEY, () -> CompletableFuture.completedFuture("value")));

        assertInstanceOf(NullPointerException.class, error.getCause());
        assertEquals("value", retried);
    }

    @Test
    void fetch_nullResult_isCachedLikeAnyOtherValue() throws Exception {
        QueryCacheImpl cache = newCache(longLivedConfig());
        AtomicInteger calls = new AtomicInteger();

        await(cache.fetch(SERVER_KEY, countingFetcher(calls, null)));
        String second = await(cache.fetch(SERVER_KEY, countingFetcher(calls, null)));

        assertNull(second);
        assertEquals(1, calls.get(), "a cached null is a hit, not a miss");
    }

    @Test
    void shutdown_staleReadAfterShutdown_stillReturnsCachedValue() throws Exception {
        QueryCacheImpl cache = newCache(longLivedConfig());
        cache.set(SERVER_KEY, "old");
        cache.get(SERVER_KEY).markStale();

        cache.shutdown();
        String result = await(cache.fetch(SERVER_KEY, () -> CompletableFuture.completedFuture("new")));

        assertEquals("old", result);
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
