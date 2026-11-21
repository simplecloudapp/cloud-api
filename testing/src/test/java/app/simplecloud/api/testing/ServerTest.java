package app.simplecloud.api.testing;

import app.simplecloud.api.CloudApi;
import app.simplecloud.api.event.server.ServerStartedEvent;
import app.simplecloud.api.event.server.ServerStateChangedEvent;
import app.simplecloud.api.event.server.ServerStoppedEvent;
import app.simplecloud.api.server.Server;
import app.simplecloud.api.server.ServerQuery;
import app.simplecloud.api.server.ServerState;
import app.simplecloud.api.server.UpdateServerRequest;
import app.simplecloud.api.testing.group.TestGroup;
import app.simplecloud.api.testing.server.TestServer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@CloudTest
class ServerTest {

    @Test
    void startedServersGetIncreasingNumericalIds(TestCloud cloud) {
        var started = cloud.collect(ServerStartedEvent.class);
        TestGroup lobby = cloud.createGroup("lobby");

        TestServer first = lobby.startServer();
        TestServer second = lobby.startServer();

        assertEquals("lobby-1", first.getName());
        assertEquals("lobby-2", second.getName());
        assertEquals(ServerState.AVAILABLE, first.getState());
        assertEquals(2, started.count());
        assertSame(lobby, first.getGroup());
    }

    @Test
    void freedNumericalIdIsReused(TestCloud cloud) {
        TestGroup lobby = cloud.createGroup("lobby");
        TestServer first = lobby.startServer();
        lobby.startServer();

        first.stop();

        assertEquals("lobby-1", lobby.startServer().getName());
    }

    @Test
    void stateChangePublishesEventAndIsVisibleThroughApi(TestCloud cloud) {
        TestServer server = cloud.createGroup("bedwars").startServer();
        var changes = cloud.collect(ServerStateChangedEvent.class);

        server.setState(ServerState.INGAME);

        ServerStateChangedEvent change = changes.single();
        assertEquals(ServerState.AVAILABLE, change.getOldState());
        assertEquals(ServerState.INGAME, change.getNewState());
        assertEquals(ServerState.INGAME, cloud.api().server().getServerById(server.getServerId()).join().getState());
    }

    @Test
    void updateThroughApiChangesHandle(TestCloud cloud) {
        TestServer server = cloud.createGroup("lobby").startServer();

        cloud.api().server().updateServer(server.getServerId(), UpdateServerRequest.builder()
                .state(ServerState.INGAME)
                .maxPlayers(64)
                .build()).join();

        assertEquals(ServerState.INGAME, server.getState());
        assertEquals(64, server.getMaxPlayers());
    }

    @Test
    void queryFiltersByGroupAndState(TestCloud cloud) {
        TestGroup lobby = cloud.createGroup("lobby");
        lobby.startServer();
        lobby.startServer().setState(ServerState.INGAME);
        cloud.createGroup("bedwars").startServer();

        List<Server> available = cloud.api().server().getAllServers(ServerQuery.create()
                .filterByServerGroupName("lobby")
                .filterByState(ServerState.AVAILABLE)).join();

        assertEquals(1, available.size());
        assertEquals(1, available.getFirst().getNumericalId());
        assertEquals(2, cloud.api().server().getServersByGroup("lobby").join().size());
    }

    @Test
    void crashPublishesStoppedEventWithDetails(TestCloud cloud) {
        TestServer server = cloud.createGroup("lobby").startServer();
        var stopped = cloud.collect(ServerStoppedEvent.class);

        server.crash(137, "out of memory");

        ServerStoppedEvent event = stopped.single();
        assertTrue(event.getCrashed());
        assertEquals(137, event.getExitCode());
        assertFalse(server.isRunning());
        assertTrue(cloud.api().server().findServerById(server.getServerId()).join().isEmpty());
    }

    @Test
    void currentServerFollowsRunAs(TestCloud cloud) {
        TestServer lobby = cloud.createGroup("lobby").startServer();
        TestServer game = cloud.createGroup("game").startServer();
        CloudApi gameApi = cloud.newApi(game);

        cloud.runAs(lobby);

        assertEquals(lobby.getServerId(), cloud.api().server().getCurrentServer().join().getServerId());
        assertEquals(game.getServerId(), gameApi.server().getCurrentServer().join().getServerId());
    }

    @Test
    void currentServerFailsWithoutRunAs(TestCloud cloud) {
        assertThrows(Exception.class, () -> cloud.api().server().getCurrentServer().join());
    }
}
