package app.simplecloud.api.internal.cache;

import app.simplecloud.api.cache.CacheEntry;
import app.simplecloud.api.cache.QueryKey;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Helpers shared by the {@link QueryCacheImpl} test classes.
 */
final class QueryCacheTestSupport {

    private QueryCacheTestSupport() {
    }

    static <T> T await(CompletableFuture<T> future) throws Exception {
        return future.get(1, TimeUnit.SECONDS);
    }

    static void awaitTrue(BooleanSupplier condition, String message) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 2_000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError(message);
            }
            Thread.sleep(5);
        }
    }

    static Supplier<CompletableFuture<String>> countingFetcher(AtomicInteger calls, String value) {
        return () -> {
            calls.incrementAndGet();
            return CompletableFuture.completedFuture(value);
        };
    }

    static String cachedData(QueryCacheImpl cache, QueryKey key) {
        CacheEntry<String> entry = cache.get(key);
        if (entry == null) {
            return null;
        }
        return entry.getData();
    }
}
