package app.simplecloud.api.testing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.simplecloud.api.CloudApiException;
import app.simplecloud.api.event.persistentserver.PersistentServerCreatedEvent;
import app.simplecloud.api.event.persistentserver.PersistentServerDeletedEvent;
import app.simplecloud.api.event.persistentserver.PersistentServerStartedEvent;
import app.simplecloud.api.event.persistentserver.PersistentServerStoppedEvent;
import app.simplecloud.api.event.persistentserver.PersistentServerUpdatedEvent;
import app.simplecloud.api.persistentserver.CreatePersistentServerRequest;
import app.simplecloud.api.persistentserver.PersistentServer;
import app.simplecloud.api.persistentserver.PersistentServerQuery;
import app.simplecloud.api.persistentserver.UpdatePersistentServerRequest;
import app.simplecloud.api.player.CloudPlayer.ConnectResult;
import app.simplecloud.api.server.Server;
import app.simplecloud.api.server.ServerQuery;
import app.simplecloud.api.testing.persistentserver.TestPersistentServer;
import app.simplecloud.api.testing.player.TestPlayer;
import app.simplecloud.api.testing.server.TestServer;
import java.util.List;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;

@CloudTest
class PersistentServerTest {

    @Test
    void createdPersistentServerIsVisibleThroughApi(TestCloud cloud) {
        var created = cloud.track(PersistentServerCreatedEvent.class);

        TestPersistentServer survival = cloud.createPersistentServer(CreatePersistentServerRequest.builder()
                .name("survival")
                .maxPlayers(100)
                .tags(List.of("smp"))
                .build());

        PersistentServer fetched = cloud.api()
                .persistentServer()
                .getPersistentServerByName("survival")
                .join();
        assertEquals(survival.getPersistentServerId(), fetched.getPersistentServerId());
        assertEquals(100, fetched.getMaxPlayers());
        assertTrue(fetched.isPersistentServer());
        assertEquals("survival", created.single().getName());
        assertFalse(survival.isRunning());
    }

    @Test
    void duplicateNameFailsWithConflict(TestCloud cloud) {
        cloud.createPersistentServer("survival");

        CloudApiException exception =
                assertThrows(CloudApiException.class, () -> cloud.createPersistentServer("survival"));

        assertEquals(CloudApiException.Reason.CONFLICT, exception.getReason());
    }

    @Test
    void startRunsServerNamedAfterPersistentServer(TestCloud cloud) {
        TestPersistentServer survival = cloud.createPersistentServer("survival");
        var started = cloud.track(PersistentServerStartedEvent.class);

        TestServer server = survival.start();

        assertEquals("survival", server.getName());
        assertSame(survival, server.getPersistentServer());
        assertNull(server.getGroup());
        assertEquals(survival.getPersistentServerId(), server.getPersistentServerId());
        assertEquals(server.getServerId(), started.single().getServerId());
        assertEquals(server, survival.getServer().orElseThrow());
        assertTrue(cloud.server("survival").isPresent());
    }

    @Test
    void startingTwiceFailsWithConflict(TestCloud cloud) {
        TestPersistentServer survival = cloud.createPersistentServer("survival");
        survival.start();

        CloudApiException exception = assertThrows(CloudApiException.class, survival::start);

        assertEquals(CloudApiException.Reason.CONFLICT, exception.getReason());
    }

    @Test
    void stoppingServerPublishesPersistentStop(TestCloud cloud) {
        TestPersistentServer survival = cloud.createPersistentServer("survival");
        TestServer server = survival.start();
        var stopped = cloud.track(PersistentServerStoppedEvent.class);

        server.stop();

        assertEquals(server.getServerId(), stopped.single().getServerId());
        assertFalse(survival.isRunning());
        assertTrue(survival.getServer().isEmpty());
    }

    @Test
    void serversCanBeFilteredByPersistentServer(TestCloud cloud) {
        TestPersistentServer survival = cloud.createPersistentServer("survival");
        survival.start();
        cloud.createGroup("lobby").startServer();

        List<Server> servers = cloud.api()
                .server()
                .getAllServers(ServerQuery.create().filterByPersistentServerId(survival.getPersistentServerId()))
                .join();

        assertEquals(
                List.of("survival"),
                servers.stream().map(server -> server.getServerBase().getName()).toList());
    }

    @Test
    void updateChangesHandleAndPublishesEvent(TestCloud cloud) {
        TestPersistentServer survival = cloud.createPersistentServer("survival");
        var updated = cloud.track(PersistentServerUpdatedEvent.class);

        survival.update(UpdatePersistentServerRequest.builder().maxPlayers(64).build())
                .setProperty("motd", "hi");

        assertEquals(64, survival.getMaxPlayers());
        assertEquals("hi", survival.getProperties().get("motd"));
        assertEquals(2, updated.count());
    }

    @Test
    void deleteStopsServerAndPublishesEvents(TestCloud cloud) {
        TestPersistentServer survival = cloud.createPersistentServer("survival");
        survival.start();
        var stopped = cloud.track(PersistentServerStoppedEvent.class);
        var deleted = cloud.track(PersistentServerDeletedEvent.class);

        cloud.api()
                .persistentServer()
                .deletePersistentServer(survival.getPersistentServerId())
                .join();

        assertEquals(1, stopped.count());
        assertEquals("survival", deleted.single().getName());
        assertFalse(survival.exists());
        assertTrue(cloud.api().server().getAllServers().join().isEmpty());
    }

    @Test
    void queryFiltersByActiveAndTags(TestCloud cloud) {
        cloud.createPersistentServer(CreatePersistentServerRequest.builder()
                .name("survival")
                .tags(List.of("smp"))
                .build());
        cloud.createPersistentServer(CreatePersistentServerRequest.builder()
                .name("creative")
                .active(false)
                .build());

        List<PersistentServer> active = cloud.api()
                .persistentServer()
                .getAllPersistentServers(PersistentServerQuery.create().filterByActive(true))
                .join();
        List<PersistentServer> smp = cloud.api()
                .persistentServer()
                .getAllPersistentServers(PersistentServerQuery.create().filterByTags("smp"))
                .join();

        assertEquals(
                List.of("survival"),
                active.stream().map(PersistentServer::getName).toList());
        assertEquals(
                List.of("survival"), smp.stream().map(PersistentServer::getName).toList());
    }

    @Test
    void scalingStartsActivePersistentServers(TestCloud cloud) {
        TestPersistentServer survival = cloud.createPersistentServer("survival");
        TestPersistentServer creative = cloud.createPersistentServer(CreatePersistentServerRequest.builder()
                .name("creative")
                .active(false)
                .build());

        cloud.controller().scaling(true);

        assertTrue(survival.isRunning());
        assertFalse(creative.isRunning());
    }

    @Test
    void playersCanConnectToPersistentServer(TestCloud cloud) {
        TestPersistentServer survival = cloud.createPersistentServer("survival");
        TestPlayer steve = cloud.createPlayer("Steve").login();

        assertEquals(
                ConnectResult.SERVER_NOT_FOUND,
                steve.connectToPersistentServer(survival.getPersistentServerId())
                        .join());

        survival.start();

        assertEquals(
                ConnectResult.SUCCESS,
                steve.connectToPersistentServer(survival.getPersistentServerId())
                        .join());
        assertEquals("survival", steve.getConnectedServerName());
        assertEquals(1, survival.getPlayerCount());
    }

    @Test
    void unknownPersistentServerFailsWithNotFound(TestCloud cloud) {
        CompletionException exception = assertThrows(
                CompletionException.class,
                () -> cloud.api()
                        .persistentServer()
                        .getPersistentServerById("missing")
                        .join());

        CloudApiException cause = assertInstanceOf(CloudApiException.class, exception.getCause());
        assertEquals(CloudApiException.Reason.NOT_FOUND, cause.getReason());
    }
}
