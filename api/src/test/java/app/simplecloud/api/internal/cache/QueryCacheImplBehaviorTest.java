package app.simplecloud.api.internal.cache;

import app.simplecloud.api.cache.CacheConfig;
import app.simplecloud.api.cache.CacheStats;
import app.simplecloud.api.cache.EntityCacheConfig;
import app.simplecloud.api.cache.EntityType;
import app.simplecloud.api.cache.QueryKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static app.simplecloud.api.internal.cache.QueryCacheTestSupport.await;
import static app.simplecloud.api.internal.cache.QueryCacheTestSupport.awaitTrue;
import static app.simplecloud.api.internal.cache.QueryCacheTestSupport.cachedData;
import static app.simplecloud.api.internal.cache.QueryCacheTestSupport.countingFetcher;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Covers stale-while-revalidate, per-entity configuration, expiry, invalidation of cached entries
 * and statistics.
 */
class QueryCacheImplBehaviorTest {

    private static final Duration IN_FLIGHT_TIMEOUT = Duration.ofSeconds(2);
    private static final QueryKey SERVER_KEY = QueryKey.of("server", "server-1");
    private static final QueryKey GROUP_KEY = QueryKey.of("group", "group-1");

    private QueryCacheImpl cacheToShutDown;

    @AfterEach
    void shutdownCache() {
        if (cacheToShutDown != null) {
            cacheToShutDown.shutdown();
        }
    }

    private QueryCacheImpl newCache(CacheConfig config) {
        cacheToShutDown = new QueryCacheImpl(config, IN_FLIGHT_TIMEOUT);
        return cacheToShutDown;
    }

    private static CacheConfig longLivedConfig() {
        return CacheConfig.builder()
                .defaultStaleTime(Duration.ofMinutes(1))
                .defaultCacheTime(Duration.ofMinutes(1))
                .entityConfig(EntityType.SERVER, EntityCacheConfig.builder().build())
                .entityConfig(EntityType.GROUP, EntityCacheConfig.builder().build())
                .build();
    }

    @Test
    void fetch_cacheMiss_returnsAndCachesSourceData() throws Exception {
        QueryCacheImpl cache = newCache(longLivedConfig());

        String result = await(cache.fetch(SERVER_KEY, () -> CompletableFuture.completedFuture("value")));

        assertEquals("value", result);
        assertEquals("value", cachedData(cache, SERVER_KEY));
    }

    @Test
    void fetch_freshEntry_isServedWithoutAskingSource() throws Exception {
        QueryCacheImpl cache = newCache(longLivedConfig());
        AtomicInteger calls = new AtomicInteger();
        await(cache.fetch(SERVER_KEY, countingFetcher(calls, "first")));

        String result = await(cache.fetch(SERVER_KEY, countingFetcher(calls, "second")));

        assertEquals("first", result);
        assertEquals(1, calls.get(), "a fresh entry must be served from the cache");
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
                .defaultCacheTime(Duration.ofMillis(500))
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
    void getKeys_showsKeysWrittenAfterTheCall() {
        QueryCacheImpl cache = newCache(longLivedConfig());
        cache.set(SERVER_KEY, "server");

        Set<QueryKey> keys = cache.getKeys();
        cache.set(GROUP_KEY, "group");

        assertEquals(Set.of(SERVER_KEY, GROUP_KEY), keys, "getKeys() is a live view of the cache's keys");
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
}
