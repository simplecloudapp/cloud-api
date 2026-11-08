package app.simplecloud.api.internal.server;

import app.simplecloud.api.CloudApiException;
import app.simplecloud.api.blueprint.Blueprint;
import app.simplecloud.api.blueprint.RuntimeConfig;
import app.simplecloud.api.blueprint.RuntimeType;
import app.simplecloud.api.cache.CacheConfig;
import app.simplecloud.api.group.GroupServerType;
import app.simplecloud.api.internal.ControllerServer;
import app.simplecloud.api.internal.cache.NoOpQueryCache;
import app.simplecloud.api.internal.cache.QueryCacheImpl;
import app.simplecloud.api.server.Server;
import app.simplecloud.api.server.ServerApi;
import app.simplecloud.api.server.ServerQuery;
import app.simplecloud.api.server.ServerState;
import app.simplecloud.api.server.UpdateServerRequest;
import app.simplecloud.api.workflow.WorkflowExecutionResult;
import com.google.gson.JsonObject;
import okhttp3.HttpUrl;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerApiImplTest {

    private static final String SERVER_ID = "3c9a1f2e-6b4d-4e8a-9f1c-2d7e5b8a0c34";
    private static final String OTHER_SERVER_ID = "8e2d4b6a-1c3f-4a5e-b7d9-0f2a4c6e8b1d";
    private static final String GROUP_ID = "0b6e2c4a-7d1f-4e3b-9a5c-2f8d6e1b3c7a";
    private static final String PERSISTENT_SERVER_ID = "7f3e1d5c-9b2a-4c6e-8d0f-1a3b5c7d9e2f";
    private static final String BLUEPRINT_ID = "5a1c9e2b-3d4f-4a6b-8c7d-9e0f1a2b3c4d";

    private ControllerServer controller;
    private QueryCacheImpl cache;

    @BeforeEach
    void setUp() {
        controller = new ControllerServer();
    }

    @AfterEach
    void tearDown() {
        if (cache != null) {
            cache.shutdown();
        }
        controller.close();
    }

    @Test
    void findServerByIdReadsServerFromController() {
        controller.respond(200, servers(server(SERVER_ID, 1)));

        Server server = api().findServerById(SERVER_ID).join().orElseThrow();

        assertEquals(SERVER_ID, server.getServerId());
        assertEquals(GROUP_ID, server.getServerGroupId());
        assertEquals(1, server.getNumericalId());
        assertEquals(ServerState.AVAILABLE, server.getState());
        assertEquals(3, server.getPlayerCount());
        assertEquals(25565, server.getPort());

        RecordedRequest request = controller.takeRequest();
        assertEquals("/v0/servers", ControllerServer.url(request).encodedPath());
        assertEquals(SERVER_ID, ControllerServer.url(request).queryParameter("server_id"));
        assertEquals(ControllerServer.NETWORK_ID, request.getHeader("X-Network-ID"));
    }

    @Test
    void serverExposesBlueprintOfItsGroup() {
        controller.respond(200, servers("""
                {"server_id": "%s", "server_group_id": "%s", "numerical_id": 1, "state": "AVAILABLE",
                 "server_group": {"id": "%s", "name": "lobby", "type": "SERVER",
                   "source": {"type": "blueprint", "blueprint": {"id": "%s", "name": "paper",
                     "server_software": "paper", "minecraft_version": "1.21.4",
                     "runtime_config": {"type": "java", "with": {"version": "21"}}}}}}
                """.formatted(SERVER_ID, GROUP_ID, GROUP_ID, BLUEPRINT_ID)));

        Server server = api().findServerById(SERVER_ID).join().orElseThrow();

        Blueprint blueprint = server.getBlueprint();
        assertNotNull(blueprint);
        assertEquals(BLUEPRINT_ID, blueprint.getBlueprintId());
        assertEquals("1.21.4", blueprint.getMinecraftVersion());
        RuntimeConfig runtime = blueprint.getRuntimeConfig();
        assertNotNull(runtime);
        assertEquals(RuntimeType.JAVA, runtime.getType());
        assertEquals(Map.of("version", "21"), runtime.getWith());
    }

    @Test
    void persistentServerHasNoBlueprint() {
        controller.respond(200, servers("""
                {"server_id": "%s", "persistent_server_id": "%s", "numerical_id": 1, "state": "AVAILABLE"}
                """.formatted(SERVER_ID, PERSISTENT_SERVER_ID)));

        assertNull(api().findServerById(SERVER_ID).join().orElseThrow().getBlueprint());
    }

    @Test
    void findServerByIdDoesNotAskControllerForNonUuid() {
        assertTrue(api().findServerById("lobby-1").join().isEmpty());

        assertEquals(0, controller.requestCount());
    }

    @Test
    void getServerByIdRetriesMissBeforeFailingWithNotFound() {
        controller.respond(200, servers());
        controller.respond(200, servers());
        controller.respond(200, servers());

        assertEquals(
                CloudApiException.Reason.NOT_FOUND,
                failure(api().getServerById(SERVER_ID)).getReason());
        assertEquals(3, controller.requestCount());
    }

    @Test
    void findServerByIdIgnoresOtherServersInResponse() {
        controller.respond(200, servers(server(OTHER_SERVER_ID, 2), server(SERVER_ID, 1)));

        Server server = api().findServerById(SERVER_ID).join().orElseThrow();

        assertEquals(SERVER_ID, server.getServerId());
    }

    @Test
    void findServerByNumericalIdPicksMatchingServer() {
        controller.respond(200, servers(server(OTHER_SERVER_ID, 1), server(SERVER_ID, 2)));

        Server server = api().findServerByNumericalId("lobby", 2).join().orElseThrow();

        assertEquals(SERVER_ID, server.getServerId());
        HttpUrl url = ControllerServer.url(controller.takeRequest());
        assertEquals("lobby", url.queryParameter("name"));
        assertEquals("2", url.queryParameter("numerical_id"));
    }

    @Test
    void getAllServersSendsQueryAsParameters() {
        controller.respond(200, servers(server(SERVER_ID, 1)));

        List<Server> servers = api().getAllServers(ServerQuery.create()
                        .filterByServerGroupId(GROUP_ID, "other-group")
                        .filterByState(ServerState.AVAILABLE, ServerState.INGAME)
                        .filterByServerGroupType(GroupServerType.SERVER)
                        .filterByServerGroupTags("lobby", "eu")
                        .filterByNumericalId(1, 2)
                        .sortBy(ServerQuery.SortField.NUMERICAL_ID)
                        .sortOrder(ServerQuery.SortOrder.ASC)
                        .limit(10)
                        .offset(20))
                .join();

        assertEquals(1, servers.size());
        HttpUrl url = ControllerServer.url(controller.takeRequest());
        assertEquals(GROUP_ID + ",other-group", url.queryParameter("server_group_id"));
        assertEquals("AVAILABLE,INGAME", url.queryParameter("state"));
        assertEquals("SERVER", url.queryParameter("type"));
        assertEquals("lobby,eu", url.queryParameter("tags"));
        assertEquals("1,2", url.queryParameter("numerical_id"));
        assertEquals("numerical_id", url.queryParameter("sort_by"));
        assertEquals("asc", url.queryParameter("sort_order"));
        assertEquals("10", url.queryParameter("limit"));
        assertEquals("20", url.queryParameter("offset"));
    }

    @Test
    void getServersByGroupSkipsServersWithoutGroup() {
        controller.respond(
                200,
                servers(
                        server(SERVER_ID, 1),
                        """
                        {"server_id": "%s", "persistent_server_id": "%s", "numerical_id": 1, "state": "AVAILABLE"}
                        """
                                .formatted(OTHER_SERVER_ID, PERSISTENT_SERVER_ID)));

        List<Server> servers = api().getServersByGroup("lobby").join();

        assertEquals(1, servers.size());
        assertEquals(SERVER_ID, servers.getFirst().getServerId());
    }

    @Test
    void cachedServerListIsRefetchedAfterStop() {
        ServerApi api = cachedApi();
        controller.respond(200, servers(server(SERVER_ID, 1)));
        controller.respond(200, "{}");
        controller.respond(200, servers());

        assertEquals(1, api.getAllServers(null).join().size());
        assertEquals(1, api.getAllServers(null).join().size());
        assertEquals(1, controller.requestCount());

        api.stopServer(SERVER_ID).join();

        assertTrue(api.getAllServers(null).join().isEmpty());
        controller.takeRequest();
        RecordedRequest stop = controller.takeRequest();
        assertEquals("DELETE", stop.getMethod());
        assertEquals(SERVER_ID, ControllerServer.url(stop).queryParameter("server_id"));
        assertEquals(3, controller.requestCount());
    }

    @Test
    void updateServerOnlySendsFieldsThatAreSet() {
        controller.respond(200, "{\"server\": " + server(SERVER_ID, 1) + "}");
        UpdateServerRequest request = new UpdateServerRequest();
        request.setState(ServerState.INGAME);
        request.setMaxPlayers(20);

        api().updateServer(SERVER_ID, request).join();

        RecordedRequest patch = controller.takeRequest();
        assertEquals("PATCH", patch.getMethod());
        assertEquals(SERVER_ID, ControllerServer.url(patch).queryParameter("server_id"));
        JsonObject body = ControllerServer.body(patch);
        assertEquals("INGAME", body.get("state").getAsString());
        assertEquals(20, body.get("max_players").getAsInt());
        assertFalse(body.has("min_memory"));
        assertFalse(body.has("max_memory"));
    }

    @Test
    void updateServerServesUpdatedServerFromCache() {
        ServerApi api = cachedApi();
        controller.respond(200, "{\"server\": " + server(SERVER_ID, 1) + "}");

        api.updateServer(SERVER_ID, new UpdateServerRequest()).join();
        Server server = api.getServerById(SERVER_ID).join();

        assertEquals(SERVER_ID, server.getServerId());
        assertEquals(1, controller.requestCount());
    }

    @Test
    void updateServerFailsWhenResponseHasNoServer() {
        controller.respond(200, "{}");

        assertEquals(
                CloudApiException.Reason.UNKNOWN,
                failure(api().updateServer(SERVER_ID, new UpdateServerRequest())).getReason());
    }

    @Test
    void sendCommandSendsCommandToServer() {
        controller.respond(200, "{}");

        api().sendCommand(SERVER_ID, "say hi").join();

        RecordedRequest request = controller.takeRequest();
        assertEquals("/v0/servers/command", ControllerServer.url(request).encodedPath());
        assertEquals(SERVER_ID, ControllerServer.url(request).queryParameter("server_id"));
        assertEquals("say hi", ControllerServer.body(request).get("command").getAsString());
    }

    @Test
    void sendCommandRejectsBlankCommandWithoutAskingController() {
        CompletionException exception = assertThrows(
                CompletionException.class, () -> api().sendCommand(SERVER_ID, " ").join());

        assertInstanceOf(IllegalArgumentException.class, exception.getCause());
        assertEquals(0, controller.requestCount());
    }

    @Test
    void executeWorkflowReportsFailedServerWithMessage() {
        controller.respond(200, "{\"success\": false, \"error_message\": \"step failed\"}");

        WorkflowExecutionResult result =
                api().executeWorkflow(SERVER_ID, "backup").join();

        assertEquals(
                new WorkflowExecutionResult("backup", 1, List.of(), List.of(), Map.of(SERVER_ID, "step failed")),
                result);
        RecordedRequest request = controller.takeRequest();
        assertEquals("/v0/servers/workflows/manual", ControllerServer.url(request).encodedPath());
        assertEquals("backup", ControllerServer.body(request).get("workflow_id").getAsString());
    }

    @Test
    void executeWorkflowReportsSucceededServer() {
        controller.respond(200, "{\"success\": true}");

        WorkflowExecutionResult result =
                api().executeWorkflow(SERVER_ID, "backup").join();

        assertEquals(List.of(SERVER_ID), result.succeededServerIds());
        assertTrue(result.failedServers().isEmpty());
    }

    @Test
    void updateServerPropertiesReturnsControllerProperties() {
        controller.respond(200, "{\"properties\": {\"motd\": \"hi\", \"rank\": \"admin\"}}");

        Map<String, Object> properties =
                api().updateServerProperties(SERVER_ID, Map.of("motd", "hi")).join();

        assertEquals(Map.of("motd", "hi", "rank", "admin"), properties);
        JsonObject body = ControllerServer.body(controller.takeRequest());
        assertEquals("hi", body.getAsJsonObject("properties").get("motd").getAsString());
    }

    @Test
    void findServerByIdAcceptsUppercaseId() {
        controller.respond(200, servers(server(SERVER_ID, 1)));

        Server server = api().findServerById(SERVER_ID.toUpperCase()).join().orElseThrow();

        assertEquals(SERVER_ID, server.getServerId());
        assertEquals(SERVER_ID, ControllerServer.url(controller.takeRequest()).queryParameter("server_id"));
    }

    @Test
    void findServerByNumericalIdIgnoresPersistentServerWithSameName() {
        controller.respond(
                200,
                servers("""
                        {"server_id": "%s", "persistent_server_id": "%s", "numerical_id": 2, "state": "AVAILABLE"}
                        """
                        .formatted(OTHER_SERVER_ID, PERSISTENT_SERVER_ID), server(SERVER_ID, 2)));

        Server server = api().findServerByNumericalId("lobby", 2).join().orElseThrow();

        assertEquals(SERVER_ID, server.getServerId());
    }

    @Test
    void getAllServersSendsServerhostAndPersistentServerIdsAsLists() {
        controller.respond(200, servers(server(SERVER_ID, 1)));

        api().getAllServers(ServerQuery.create()
                        .filterByServerhostId("host-1", "host-2")
                        .filterByPersistentServerId(PERSISTENT_SERVER_ID))
                .join();

        HttpUrl url = ControllerServer.url(controller.takeRequest());
        assertEquals("host-1,host-2", url.queryParameter("serverhost_id"));
        assertEquals(PERSISTENT_SERVER_ID, url.queryParameter("persistent_server_id"));
    }

    @Test
    void updateServerRejectsUnknownStateWithoutAskingController() {
        CompletableFuture<Server> future = api().updateServer(
                SERVER_ID, UpdateServerRequest.builder().state(ServerState.UNKNOWN_STATE).build());

        CompletionException exception = assertThrows(CompletionException.class, future::join);
        assertInstanceOf(IllegalArgumentException.class, exception.getCause());
        assertEquals(0, controller.requestCount());
    }

    @Test
    void setServerPropertiesReplacesProperties() {
        controller.respond(200, "{\"properties\": {\"motd\": \"hi\"}}");

        Map<String, Object> properties =
                api().setServerProperties(SERVER_ID, Map.of("motd", "hi")).join();

        assertEquals(Map.of("motd", "hi"), properties);
        RecordedRequest request = controller.takeRequest();
        assertEquals("PUT", request.getMethod());
        assertEquals("hi", ControllerServer.body(request).getAsJsonObject("properties").get("motd").getAsString());
    }

    private ServerApi api() {
        return new ServerApiImpl(controller.options(), new NoOpQueryCache(), controller.apiClient(), Runnable::run);
    }

    private ServerApi cachedApi() {
        cache = new QueryCacheImpl(CacheConfig.DEFAULT);
        return new ServerApiImpl(controller.options(), cache, controller.apiClient(), Runnable::run);
    }

    private static String server(String serverId, int numericalId) {
        return """
                {"server_id": "%s", "server_group_id": "%s", "numerical_id": %d, "state": "AVAILABLE",
                 "player_count": 3, "max_players": 20, "ip": "10.0.0.2", "port": 25565,
                 "network_id": "%s", "serverhost_id": "host-1"}
                """
                .formatted(serverId, GROUP_ID, numericalId, ControllerServer.NETWORK_ID);
    }

    private static String servers(String... servers) {
        return "{\"servers\": [" + String.join(",", servers) + "]}";
    }

    private static CloudApiException failure(CompletableFuture<?> future) {
        CompletionException exception = assertThrows(CompletionException.class, future::join);
        return assertInstanceOf(CloudApiException.class, exception.getCause());
    }
}
