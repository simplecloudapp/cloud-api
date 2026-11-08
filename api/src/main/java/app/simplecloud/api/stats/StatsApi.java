package app.simplecloud.api.stats;

import java.util.concurrent.CompletableFuture;

/**
 * API for network wide statistics.
 */
public interface StatsApi {

    /**
     * Retrieves the current statistics of the network.
     *
     * @return a future that completes with the statistics
     */
    CompletableFuture<CloudStats> getStats();
}
