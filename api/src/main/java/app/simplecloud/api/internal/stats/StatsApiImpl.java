package app.simplecloud.api.internal.stats;

import app.simplecloud.api.CloudApiOptions;
import app.simplecloud.api.internal.web.ApiCalls;
import app.simplecloud.api.server.ServerState;
import app.simplecloud.api.stats.CloudStats;
import app.simplecloud.api.stats.StatsApi;
import app.simplecloud.api.web.ApiClient;
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
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import static app.simplecloud.api.internal.util.RestModelMapper.orZero;

public class StatsApiImpl implements StatsApi {
    private final CloudApiOptions options;
    private final app.simplecloud.api.web.apis.StatsApi statsApi;
    private final Executor executor;

    public StatsApiImpl(CloudApiOptions options, ApiClient httpClient, Executor executor) {
        this.options = options;
        this.statsApi = new app.simplecloud.api.web.apis.StatsApi(httpClient);
        this.executor = executor;
    }

    @Override
    public CompletableFuture<CloudStats> getStats() {
        return ApiCalls.supplyAsync(executor, () -> toStats(statsApi.getStats(options.getNetworkId())));
    }

    static CloudStats toStats(ModelsCloudStats model) {
        ModelsHealthStatus health = model.getHealth() != null ? model.getHealth() : new ModelsHealthStatus();
        ModelsServerStats servers = model.getServers() != null ? model.getServers() : new ModelsServerStats();
        ModelsGroupStats groups = model.getServerGroups() != null ? model.getServerGroups() : new ModelsGroupStats();
        ModelsPersistentStats persistentServers =
                model.getPersistentServers() != null ? model.getPersistentServers() : new ModelsPersistentStats();
        ModelsServerhostStats serverhosts =
                model.getServerhosts() != null ? model.getServerhosts() : new ModelsServerhostStats();
        ModelsPlayerStats players = model.getPlayers() != null ? model.getPlayers() : new ModelsPlayerStats();
        ModelsResourceStats resources =
                model.getResources() != null ? model.getResources() : new ModelsResourceStats();

        return new CloudStats(
                new CloudStats.Health(health.getStatus(), toDouble(health.getHealthyServerPercent())),
                new CloudStats.Servers(
                        orZero(servers.getTotal()), orZero(servers.getActive()), byState(servers.getByState())),
                new CloudStats.Count(orZero(groups.getTotal()), orZero(groups.getActive())),
                new CloudStats.Count(orZero(persistentServers.getTotal()), orZero(persistentServers.getActive())),
                new CloudStats.Serverhosts(
                        orZero(serverhosts.getTotal()),
                        orZero(serverhosts.getActive()),
                        orZero(serverhosts.getTotalMemoryCapacityMb())),
                new CloudStats.Players(
                        orZero(players.getOnline()),
                        orZero(players.getCapacity()),
                        toDouble(players.getUtilizationPercent())),
                new CloudStats.Resources(
                        orZero(resources.getMemoryAllocatedMb()),
                        toDouble(resources.getMemoryUsedMb()),
                        toDouble(resources.getAverageCpuPercent())));
    }

    private static Map<ServerState, Integer> byState(@Nullable ModelsServersByState model) {
        Map<ServerState, Integer> byState = new EnumMap<>(ServerState.class);
        if (model == null) {
            return byState;
        }
        put(byState, ServerState.QUEUED, model.getQueued());
        put(byState, ServerState.PREPARING, model.getPreparing());
        put(byState, ServerState.STARTING, model.getStarting());
        put(byState, ServerState.AVAILABLE, model.getAvailable());
        put(byState, ServerState.INGAME, model.getIngame());
        put(byState, ServerState.STOPPING, model.getStopping());
        put(byState, ServerState.STOPPED, model.getStopped());
        return byState;
    }

    private static void put(Map<ServerState, Integer> byState, ServerState state, @Nullable Integer count) {
        if (count != null) {
            byState.put(state, count);
        }
    }

    private static double toDouble(@Nullable BigDecimal value) {
        return value == null ? 0 : value.doubleValue();
    }
}
