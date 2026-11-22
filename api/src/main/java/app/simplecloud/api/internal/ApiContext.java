package app.simplecloud.api.internal;

import app.simplecloud.api.CloudApiOptions;
import app.simplecloud.api.cache.QueryCache;
import app.simplecloud.api.cache.QueryKey;
import app.simplecloud.api.internal.web.ApiClientFactory;
import app.simplecloud.api.web.ApiClient;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public record ApiContext(CloudApiOptions options, QueryCache cache, ApiClient client, Executor executor) {

    private static final Duration TRANSIENT_MISS_RETRY_DELAY = Duration.ofMillis(200);
    private static final int TRANSIENT_MISS_MAX_RETRIES = 2;

    public static ApiContext create(CloudApiOptions options, QueryCache cache) {
        return new ApiContext(options, cache, ApiClientFactory.create(options), ForkJoinPool.commonPool());
    }

    public String networkId() {
        return options.getNetworkId();
    }

    public <T> T api(Function<ApiClient, T> factory) {
        return factory.apply(client);
    }

    public <T> CompletableFuture<T> fetchWithTransientMissRecovery(QueryKey key,
                                                                   supplier<CompletableFuture<T>> fetcher,
                                                                   Predicate<T> isTransientMiss) {
        return cache.fetchWithTransientMissRecovery(
                key,
                fetcher,
                isTransientMiss,
                TRANSIENT_MISS_RETRY_DELAY,
                TRANSIENT_MISS_MAX_RETRIES
        );
    }
}
