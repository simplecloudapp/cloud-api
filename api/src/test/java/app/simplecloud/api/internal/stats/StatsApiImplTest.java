package app.simplecloud.api.internal.stats;

import app.simplecloud.api.stats.CloudStats;
import app.simplecloud.api.support.ControllerServer;
import app.simplecloud.api.support.TestContexts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StatsApiImplTest {

    private final ControllerServer controller = ControllerServer.start();

    @AfterEach
    void stopController() {
        controller.close();
    }

    @Test
    void mapsReportedValuesAndUsesZeroForMissingSections() {
        controller.on("GET", "/v0/stats", """
                {
                  "servers": {"total": 3, "active": 2, "by_state": {"available": 2, "starting": 1}},
                  "players": {"online": 12, "capacity": 100, "utilization_percent": 12.5}
                }
                """);

        CloudStats stats = new StatsApiImpl(TestContexts.create(controller)).getStats().join();

        assertEquals(3, stats.servers().total());
        assertEquals(2, stats.servers().byState().available());
        assertEquals(1, stats.servers().byState().starting());
        assertEquals(0, stats.servers().byState().stopping());
        assertEquals(12, stats.players().online());
        assertEquals(12.5, stats.players().utilizationPercent());
        assertEquals(0, stats.serverGroups().total());
        assertEquals(0, stats.serverhosts().totalMemoryCapacityMb());
    }
}
