package app.simplecloud.api.internal.stats;

import app.simplecloud.api.internal.ApiContext;
import app.simplecloud.api.internal.web.ApiCalls;
import app.simplecloud.api.stats.CloudStats;
import app.simplecloud.api.stats.StatsApi;
import app.simplecloud.api.web.models.ModelsCloudStats;
import app.simplecloud.api.web.models.ModelsGroupStats;
import app.simplecloud.api.web.models.ModelsHealthStatus;
import app.simplecloud.api.web.models.ModelsPersistentStats;
import app.simplecloud.api.web.models.ModelsPlayerStats;
import app.simplecloud.api.web.models.ModelsResourceStats;
import app.simplecloud.api.web.models.ModelsServerStats;
import app.simplecloud.api.web.models.ModelsServerhostStats;
import app.simplecloud.api.web.models.ModelsServersByState;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;

public class StatsApiImpl implements StatsApi {
    private final ApiContext context;
    private final app.simplecloud.api.web.apis.StatsApi statsApi;

    public StatsApiImpl(ApiContext context) {
        this.context = context;
        this.statsApi = context.api(app.simplecloud.api.web.apis.StatsApi::new);
    }

    @Override
    public CompletableFuture<CloudStats> getStats() {
        return ApiCalls.supplyAsync(() -> convert(statsApi.getStats(context.networkId())), context.executor());
    }

    private static CloudStats convert(ModelsCloudStats stats) {
        return new CloudStats(
                convert(stats.getServers()),
                convert(stats.getPlayers()),
                convert(stats.getServerGroups()),
                convert(stats.getPersistentServers()),
                convert(stats.getServerhosts()),
                convert(stats.getResources()),
                convert(stats.getHealth())
        );
    }

    private static CloudStats.ServerStats convert(@Nullable ModelsServerStats stats) {
        if (stats == null) {
            return new CloudStats.ServerStats(0, 0, convert((ModelsServersByState) null));
        }
        return new CloudStats.ServerStats(intValue(stats.getTotal()), intValue(stats.getActive()), convert(stats.getByState()));
    }

    private static CloudStats.ServersByState convert(@Nullable ModelsServersByState byState) {
        if (byState == null) {
            return new CloudStats.ServersByState(0, 0, 0, 0, 0, 0, 0);
        }
        return new CloudStats.ServersByState(
                intValue(byState.getQueued()),
                intValue(byState.getPreparing()),
                intValue(byState.getStarting()),
                intValue(byState.getAvailable()),
                intValue(byState.getIngame()),
                intValue(byState.getStopping()),
                intValue(byState.getStopped())
        );
    }

    private static CloudStats.PlayerStats convert(@Nullable ModelsPlayerStats stats) {
        if (stats == null) {
            return new CloudStats.PlayerStats(0, 0, 0);
        }
        return new CloudStats.PlayerStats(
                intValue(stats.getOnline()),
                intValue(stats.getCapacity()),
                doubleValue(stats.getUtilizationPercent())
        );
    }

    private static CloudStats.CountStats convert(@Nullable ModelsGroupStats stats) {
        return stats == null
                ? new CloudStats.CountStats(0, 0)
                : new CloudStats.CountStats(intValue(stats.getTotal()), intValue(stats.getActive()));
    }

    private static CloudStats.CountStats convert(@Nullable ModelsPersistentStats stats) {
        return stats == null
                ? new CloudStats.CountStats(0, 0)
                : new CloudStats.CountStats(intValue(stats.getTotal()), intValue(stats.getActive()));
    }

    private static CloudStats.ServerhostStats convert(@Nullable ModelsServerhostStats stats) {
        if (stats == null) {
            return new CloudStats.ServerhostStats(0, 0, 0);
        }
        return new CloudStats.ServerhostStats(
                intValue(stats.getTotal()),
                intValue(stats.getActive()),
                intValue(stats.getTotalMemoryCapacityMb())
        );
    }

    private static CloudStats.ResourceStats convert(@Nullable ModelsResourceStats stats) {
        if (stats == null) {
            return new CloudStats.ResourceStats(0, 0, 0);
        }
        return new CloudStats.ResourceStats(
                intValue(stats.getMemoryAllocatedMb()),
                doubleValue(stats.getMemoryUsedMb()),
                doubleValue(stats.getAverageCpuPercent())
        );
    }

    private static CloudStats.HealthStats convert(@Nullable ModelsHealthStatus health) {
        if (health == null) {
            return new CloudStats.HealthStats("", 0);
        }
        return new CloudStats.HealthStats(
                health.getStatus() != null ? health.getStatus() : "",
                doubleValue(health.getHealthyServerPercent())
        );
    }

    private static int intValue(@Nullable Integer value) {
        return value != null ? value : 0;
    }

    private static double doubleValue(@Nullable BigDecimal value) {
        return value != null ? value.doubleValue() : 0;
    }
}
