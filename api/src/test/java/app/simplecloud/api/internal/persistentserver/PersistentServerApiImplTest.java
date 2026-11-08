package app.simplecloud.api.internal.persistentserver;

import app.simplecloud.api.CloudApiException;
import app.simplecloud.api.blueprint.CreateBlueprintRequest;
import app.simplecloud.api.group.DeploymentHost;
import app.simplecloud.api.group.Group;
import app.simplecloud.api.group.GroupServerType;
import app.simplecloud.api.group.ScalingConfig;
import app.simplecloud.api.group.ScalingMode;
import app.simplecloud.api.group.SourceConfig;
import app.simplecloud.api.group.SourceType;
import app.simplecloud.api.internal.ControllerServer;
import app.simplecloud.api.internal.cache.NoOpQueryCache;
import app.simplecloud.api.persistentserver.CreatePersistentServerRequest;
import app.simplecloud.api.persistentserver.PersistentServer;
import app.simplecloud.api.persistentserver.PersistentServerApi;
import app.simplecloud.api.persistentserver.PersistentServerQuery;
import app.simplecloud.api.persistentserver.UpdatePersistentServerRequest;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PersistentServerApiImplTest {

    private static final String PERSISTENT_SERVER_ID = "7f3e1d5c-9b2a-4c6e-8d0f-1a3b5c7d9e2f";
    private static final String OTHER_PERSISTENT_SERVER_ID = "2a4c6e8f-0b1d-4e3f-a5b7-c9d1e3f5a7b9";
    private static final String BLUEPRINT_ID = "5a1c9e2b-3d4f-4a6b-8c7d-9e0f1a2b3c4d";
    private static final String GROUP_ID = "0b6e2c4a-7d1f-4e3b-9a5c-2f8d6e1b3c7a";
    private static final String SERVER_ID = "3c9a1f2e-6b4d-4e8a-9f1c-2d7e5b8a0c34";

    private ControllerServer controller;
    private PersistentServerApi api;

    @BeforeEach
    void setUp() {
        controller = new ControllerServer();
        api = new PersistentServerApiImpl(
                controller.options(), new NoOpQueryCache(), controller.apiClient(), Runnable::run);
    }

    @AfterEach
    void tearDown() {
        controller.close();
    }

    @Test
    void findPersistentServerByIdReadsServerFromController() {
        controller.respond(200, persistentServers(persistentServer(PERSISTENT_SERVER_ID, "hub")));

        PersistentServer server =
                api.findPersistentServerById(PERSISTENT_SERVER_ID).join().orElseThrow();

        assertEquals(PERSISTENT_SERVER_ID, server.getPersistentServerId());
        assertEquals("hub", server.getName());
        assertEquals(GroupServerType.SERVER, server.getType());
        assertEquals(50, server.getMaxPlayers());
        SourceConfig source = server.getSource();
        assertNotNull(source);
        assertEquals(BLUEPRINT_ID, source.getBlueprint());

        RecordedRequest request = controller.takeRequest();
        assertEquals("/v0/persistent-servers", ControllerServer.url(request).encodedPath());
        assertEquals(PERSISTENT_SERVER_ID, ControllerServer.url(request).queryParameter("persistent_server_id"));
    }

    @Test
    void findPersistentServerByIdDoesNotAskControllerForNonUuid() {
        assertTrue(api.findPersistentServerById("hub").join().isEmpty());

        assertEquals(0, controller.requestCount());
    }

    @Test
    void findPersistentServerByNameOnlyAcceptsExactName() {
        controller.respond(
                200,
                persistentServers(
                        persistentServer(OTHER_PERSISTENT_SERVER_ID, "hub-old"),
                        persistentServer(PERSISTENT_SERVER_ID, "hub")));

        PersistentServer server = api.findPersistentServerByName("hub").join().orElseThrow();

        assertEquals(PERSISTENT_SERVER_ID, server.getPersistentServerId());
        assertEquals("hub", ControllerServer.url(controller.takeRequest()).queryParameter("name"));
    }

    @Test
    void getAllPersistentServersSendsQueryAsParameters() {
        controller.respond(200, persistentServers(persistentServer(PERSISTENT_SERVER_ID, "hub")));

        List<PersistentServer> servers = api.getAllPersistentServers(PersistentServerQuery.create()
                        .filterByTags("hub", "eu")
                        .filterByActive(true)
                        .filterByType(GroupServerType.SERVER)
                        .filterBySourceType(SourceType.BLUEPRINT)
                        .filterByBlueprintId(BLUEPRINT_ID)
                        .filterByServerhostId("host-1")
                        .limit(10)
                        .offset(20))
                .join();

        assertEquals(1, servers.size());
        HttpUrl url = ControllerServer.url(controller.takeRequest());
        assertEquals("hub,eu", url.queryParameter("tags"));
        assertEquals("true", url.queryParameter("active"));
        assertEquals("SERVER", url.queryParameter("type"));
        assertEquals("blueprint", url.queryParameter("source_type"));
        assertEquals(BLUEPRINT_ID, url.queryParameter("blueprint_id"));
        assertEquals("host-1", url.queryParameter("serverhost_id"));
        assertEquals("10", url.queryParameter("limit"));
        assertEquals("20", url.queryParameter("offset"));
    }

    @Test
    void updatePersistentServerReturnsServerFromPatchResponse() {
        controller.respond(200, persistentServer(PERSISTENT_SERVER_ID, "hub"));

        PersistentServer server = api.updatePersistentServer(
                        PERSISTENT_SERVER_ID,
                        UpdatePersistentServerRequest.builder().maxPlayers(80).build())
                .join();

        assertEquals(PERSISTENT_SERVER_ID, server.getPersistentServerId());
        assertEquals("hub", server.getName());
        RecordedRequest patch = controller.takeRequest();
        assertEquals("PATCH", patch.getMethod());
        assertEquals(PERSISTENT_SERVER_ID, ControllerServer.url(patch).queryParameter("persistent_server_id"));
        JsonObject body = ControllerServer.body(patch);
        assertEquals(80, body.get("max_players").getAsInt());
        assertFalse(body.has("name"));
        assertFalse(body.has("source"));
        assertEquals(1, controller.requestCount());
    }

    @Test
    void deletePersistentServerAlsoDeletesItsBlueprint() {
        controller.respond(200, persistentServers(persistentServer(PERSISTENT_SERVER_ID, "hub")));
        controller.respond(200, "{}");
        controller.respond(200, "{}");

        api.deletePersistentServer(PERSISTENT_SERVER_ID).join();

        assertEquals("GET", controller.takeRequest().getMethod());
        RecordedRequest deleteServer = controller.takeRequest();
        assertEquals(
                "/v0/persistent-servers", ControllerServer.url(deleteServer).encodedPath());
        assertEquals("DELETE", deleteServer.getMethod());
        RecordedRequest deleteBlueprint = controller.takeRequest();
        assertEquals("/v0/blueprints", ControllerServer.url(deleteBlueprint).encodedPath());
        assertEquals("DELETE", deleteBlueprint.getMethod());
        assertEquals(BLUEPRINT_ID, ControllerServer.url(deleteBlueprint).queryParameter("blueprint_id"));
    }

    @Test
    void createPersistentServerKeepsBlueprintWhenServerWasCreatedDespiteError() {
        controller.respond(201, "{\"blueprint_id\": \"" + BLUEPRINT_ID + "\"}");
        controller.respond(503, "{\"error\": \"upstream timeout\"}");
        controller.respond(200, persistentServers(persistentServer(PERSISTENT_SERVER_ID, "hub")));

        CloudApiException failure = failure(api.createPersistentServer(CreatePersistentServerRequest.builder()
                .name("hub")
                .createBlueprint(paperBlueprint())
                .build()));

        assertEquals(CloudApiException.Reason.UNAVAILABLE, failure.getReason());
        assertEquals(
                "/v0/blueprints", ControllerServer.url(controller.takeRequest()).encodedPath());
        assertEquals("POST", controller.takeRequest().getMethod());
        RecordedRequest reconcile = controller.takeRequest();
        assertEquals("GET", reconcile.getMethod());
        assertEquals("hub", ControllerServer.url(reconcile).queryParameter("name"));
        assertEquals(3, controller.requestCount());
    }

    @Test
    void convertToGroupSendsHostsAndScaling() {
        controller.respond(201, """
                {"server_group_id": "%s", "name": "hub", "type": "SERVER",
                 "scaling": {"min_servers": 1, "max_servers": 3, "scaling_mode": "SLOTS"}}
                """.formatted(GROUP_ID));

        Group group = api.convertToGroup(
                        PERSISTENT_SERVER_ID, List.of(new DeploymentHost("host-1", 1)), completeScaling())
                .join();

        assertEquals(GROUP_ID, group.getServerGroupId());
        RecordedRequest request = controller.takeRequest();
        assertEquals(
                "/v0/persistent-servers/convert-to-group",
                ControllerServer.url(request).encodedPath());
        assertEquals(PERSISTENT_SERVER_ID, ControllerServer.url(request).queryParameter("persistent_server_id"));
        JsonObject body = ControllerServer.body(request);
        JsonObject host = body.getAsJsonObject("deployment")
                .getAsJsonArray("hosts")
                .get(0)
                .getAsJsonObject();
        assertEquals("host-1", host.get("name").getAsString());
        assertEquals(1, host.get("priority").getAsInt());
        JsonObject scaling = body.getAsJsonObject("scaling");
        assertEquals("SLOTS", scaling.get("scaling_mode").getAsString());
        assertEquals(3, scaling.get("max_servers").getAsInt());
    }

    @Test
    void convertToGroupRejectsIncompleteScalingWithoutAskingController() {
        ScalingConfig scaling = ScalingConfig.builder().maxServers(3).build();

        CompletionException exception = assertThrows(
                CompletionException.class,
                () -> api.convertToGroup(PERSISTENT_SERVER_ID, List.of(), scaling)
                        .join());

        assertInstanceOf(IllegalArgumentException.class, exception.getCause());
        assertEquals(0, controller.requestCount());
    }

    @Test
    void executeWorkflowMapsControllerResult() {
        controller.respond(200, """
                {"total_servers": 2, "succeeded_server_ids": ["%s"], "skipped_server_ids": [],
                 "failed_servers": [{"server_id": "failed-server", "error_message": "step failed"}]}
                """.formatted(SERVER_ID));

        WorkflowExecutionResult result =
                api.executeWorkflow(PERSISTENT_SERVER_ID, "backup").join();

        assertEquals(
                new WorkflowExecutionResult(
                        "backup", 2, List.of(SERVER_ID), List.of(), Map.of("failed-server", "step failed")),
                result);
        RecordedRequest request = controller.takeRequest();
        assertEquals(
                "/v0/persistent-servers/workflows/manual",
                ControllerServer.url(request).encodedPath());
        assertEquals("backup", ControllerServer.body(request).get("workflow_id").getAsString());
    }

    private static ScalingConfig completeScaling() {
        return ScalingConfig.builder()
                .scalingMode(ScalingMode.SLOTS)
                .minServers(1)
                .maxServers(3)
                .playerThreshold(0.75)
                .build();
    }

    private static CreateBlueprintRequest paperBlueprint() {
        return CreateBlueprintRequest.builder()
                .serverSoftware("paper")
                .minecraftVersion("1.21.4")
                .serverUrl("https://example.com/paper.jar")
                .build();
    }

    private static String persistentServer(String id, String name) {
        return """
                {"persistent_server_id": "%s", "name": "%s", "type": "SERVER", "max_players": 50, "active": true,
                 "tags": ["%s"], "source": {"type": "blueprint", "blueprint": "%s"}}
                """.formatted(id, name, name, BLUEPRINT_ID);
    }

    private static String persistentServers(String... servers) {
        return "{\"persistent_servers\": [" + String.join(",", servers) + "]}";
    }

    private static CloudApiException failure(CompletableFuture<?> future) {
        CompletionException exception = assertThrows(CompletionException.class, future::join);
        return assertInstanceOf(CloudApiException.class, exception.getCause());
    }
}
