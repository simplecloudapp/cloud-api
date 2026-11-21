package app.simplecloud.api.testing;

import app.simplecloud.api.group.ScalingConfig;
import app.simplecloud.api.group.ScalingMode;
import app.simplecloud.api.testing.group.TestGroup;
import app.simplecloud.api.testing.server.TestServer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@CloudTest
class ScalingTest {

    @Test
    void scalingIsOffByDefault(TestCloud cloud) {
        TestGroup lobby = cloud.createGroup("lobby", group -> group
                .scaling(ScalingConfig.builder().minServers(2).build()));

        assertTrue(lobby.getServers().isEmpty());
    }

    @Test
    void minServersAreStartedWhenScalingIsEnabled(TestCloud cloud) {
        TestGroup lobby = cloud.createGroup("lobby", group -> group
                .scaling(ScalingConfig.builder().minServers(2).build()));

        cloud.controller().scaling(true);

        assertEquals(2, lobby.getServers().size());
    }

    @Test
    void stoppedServerIsReplaced(TestCloud cloud) {
        cloud.controller().scaling(true);
        TestGroup lobby = cloud.createGroup("lobby", group -> group
                .scaling(ScalingConfig.builder().minServers(1).build()));
        TestServer first = lobby.getServers().getFirst();

        first.stop();

        List<TestServer> servers = lobby.getServers();
        assertEquals(1, servers.size());
        assertEquals("lobby-1", servers.getFirst().getName());
    }

    @Test
    void slotModeStartsServerWhenFreeSlotsRunLow(TestCloud cloud) {
        cloud.controller().scaling(true);
        TestGroup lobby = cloud.createGroup("lobby", group -> group
                .maxPlayers(20)
                .scaling(ScalingConfig.builder()
                        .scalingMode(ScalingMode.SLOTS)
                        .minServers(1)
                        .availableSlots(10)
                        .build()));

        lobby.getServers().getFirst().setPlayerCount(15);

        assertEquals(2, lobby.getServers().size());
    }

    @Test
    void playerModeStartsServerWhenThresholdIsReached(TestCloud cloud) {
        cloud.controller().scaling(true);
        TestGroup lobby = cloud.createGroup("lobby", group -> group
                .maxPlayers(10)
                .scaling(ScalingConfig.builder()
                        .scalingMode(ScalingMode.PLAYERS)
                        .minServers(1)
                        .playerThreshold(0.8)
                        .build()));

        lobby.getServers().getFirst().setPlayerCount(7);
        assertEquals(1, lobby.getServers().size());

        lobby.getServers().getFirst().setPlayerCount(8);
        assertEquals(2, lobby.getServers().size());
    }

    @Test
    void maxServersIsRespected(TestCloud cloud) {
        cloud.controller().scaling(true);
        TestGroup lobby = cloud.createGroup("lobby", group -> group
                .maxPlayers(10)
                .scaling(ScalingConfig.builder()
                        .minServers(1)
                        .maxServers(2)
                        .availableSlots(100)
                        .build()));

        assertEquals(2, lobby.getServers().size());
    }

    @Test
    void manualModeIsIgnored(TestCloud cloud) {
        cloud.controller().scaling(true);
        TestGroup lobby = cloud.createGroup("lobby", group -> group
                .scaling(ScalingConfig.builder()
                        .scalingMode(ScalingMode.MANUAL)
                        .minServers(3)
                        .build()));

        assertTrue(lobby.getServers().isEmpty());
    }
}
