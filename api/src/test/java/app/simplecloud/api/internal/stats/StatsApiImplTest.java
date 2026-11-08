package app.simplecloud.api.internal.stats;

import app.simplecloud.api.internal.ControllerServer;
import app.simplecloud.api.server.ServerState;
import app.simplecloud.api.stats.CloudStats;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class StatsApiImplTest {

    private ControllerServer controller;
    private StatsApiImpl api;

    @BeforeEach
    void setUp() {
        controller = new ControllerServer();
        api = new StatsApiImpl(controller.options(), controller.apiClient(), Runnable::run);
    }

    @AfterEach
    void tearDown() {
        controller.close();
    }

    @Test
    void getStatsReadsControllerStats() {
        controller.respond(200, """
                {"health": {"status": "healthy", "healthy_server_percent": 100},
                 "servers": {"total": 3, "active": 2, "by_state": {"available": 2, "stopped": 1}},
                 "server_groups": {"total": 2, "active": 1},
                 "persistent_servers": {"total": 1, "active": 1},
                 "serverhosts": {"total": 1, "active": 1, "total_memory_capacity_mb": 8192},
                 "players": {"online": 5, "capacity": 40, "utilization_percent": 12.5},
                 "resources": {"memory_allocated_mb": 3072, "memory_used_mb": 1500.5, "average_cpu_percent": 7.5}}
                """);

        CloudStats stats = api.getStats().join();

        assertEquals("healthy", stats.health().status());
        assertEquals(3, stats.servers().total());
        assertEquals(
                Map.of(ServerState.AVAILABLE, 2, ServerState.STOPPED, 1),
                stats.servers().byState());
        assertEquals(new CloudStats.Count(2, 1), stats.groups());
        assertEquals(8192, stats.serverhosts().totalMemoryCapacityMb());
        assertEquals(12.5, stats.players().utilizationPercent());
        assertEquals(1500.5, stats.resources().memoryUsedMb());
        assertEquals("/v0/stats", ControllerServer.url(controller.takeRequest()).encodedPath());
    }

    @Test
    void missingSectionsBecomeZero() {
        controller.respond(200, "{}");

        CloudStats stats = api.getStats().join();

        assertNull(stats.health().status());
        assertEquals(0, stats.servers().total());
        assertEquals(Map.of(), stats.servers().byState());
    }
}
