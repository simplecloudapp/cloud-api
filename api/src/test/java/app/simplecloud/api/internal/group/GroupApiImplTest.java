package app.simplecloud.api.internal.group;

import app.simplecloud.api.CloudApiException;
import app.simplecloud.api.blueprint.CreateBlueprintRequest;
import app.simplecloud.api.group.CreateGroupRequest;
import app.simplecloud.api.group.Group;
import app.simplecloud.api.group.GroupApi;
import app.simplecloud.api.group.GroupServerType;
import app.simplecloud.api.group.ScalingMode;
import app.simplecloud.api.group.SourceConfig;
import app.simplecloud.api.internal.ControllerServer;
import app.simplecloud.api.internal.cache.NoOpQueryCache;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroupApiImplTest {

    private static final String GROUP_ID = "0b6e2c4a-7d1f-4e3b-9a5c-2f8d6e1b3c7a";
    private static final String BLUEPRINT_ID = "5a1c9e2b-3d4f-4a6b-8c7d-9e0f1a2b3c4d";
    private static final String GROUP_JSON = """
            {"server_group_id": "%s", "name": "lobby", "type": "SERVER", "max_players": 50,
             "scaling": {"min_servers": 1, "max_servers": 3, "scaling_mode": "SLOTS"},
             "source": {"type": "blueprint", "blueprint": "%s"}}
            """.formatted(GROUP_ID, BLUEPRINT_ID);

    private ControllerServer controller;
    private GroupApi api;

    @BeforeEach
    void setUp() {
        controller = new ControllerServer();
        api = new GroupApiImpl(controller.options(), new NoOpQueryCache(), controller.apiClient(), Runnable::run);
    }

    @AfterEach
    void tearDown() {
        controller.close();
    }

    @Test
    void getGroupByNameReadsGroupFromController() {
        controller.respond(200, "{\"server_groups\": [" + GROUP_JSON + "]}");

        Group group = api.getGroupByName("lobby").join();

        assertEquals(GROUP_ID, group.getServerGroupId());
        assertEquals(GroupServerType.SERVER, group.getType());
        assertEquals(1, group.getScaling().getMinServers());
        assertEquals(ScalingMode.SLOTS, group.getScaling().getScalingMode());
        assertEquals(BLUEPRINT_ID, group.getSource().getBlueprint());
        assertEquals("lobby", controller.takeRequest().getRequestUrl().queryParameter("name"));
    }

    @Test
    void findGroupByIdDoesNotAskControllerForNonUuid() {
        assertTrue(api.findGroupById("lobby").join().isEmpty());

        assertEquals(0, controller.requestCount());
    }

    @Test
    void createGroupFillsInDefaults() {
        controller.respond(201, GROUP_JSON);

        api.createGroup(new CreateGroupRequest("lobby")).join();

        JsonObject body = ControllerServer.body(controller.takeRequest());
        assertEquals("SERVER", body.get("type").getAsString());
        assertEquals(50, body.get("max_players").getAsInt());
        assertTrue(body.get("active").getAsBoolean());
        assertEquals(single("lobby"), body.getAsJsonArray("tags"));
        assertEquals(
                "blacklist", body.getAsJsonObject("deployment").get("strategy").getAsString());

        JsonObject scaling = body.getAsJsonObject("scaling");
        assertEquals(0, scaling.get("min_servers").getAsInt());
        assertEquals(1, scaling.get("max_servers").getAsInt());
        assertEquals(0.75, scaling.get("player_threshold").getAsDouble());
        assertEquals("SLOTS", scaling.get("scaling_mode").getAsString());
        assertEquals(
                "3m", scaling.getAsJsonObject("scale_down").get("idle_time").getAsString());

        JsonObject when = body.getAsJsonObject("workflows").getAsJsonObject("when");
        assertEquals(single("internal/setup"), when.getAsJsonArray("start"));
        assertEquals(single("internal/cleanup"), when.getAsJsonArray("stop"));
    }

    @Test
    void createGroupWithInlineBlueprintCreatesBlueprintFirst() {
        controller.respond(201, "{\"blueprint_id\": \"" + BLUEPRINT_ID + "\"}");
        controller.respond(201, GROUP_JSON);

        api.createGroup(new CreateGroupRequest("lobby").setCreateBlueprint(paperBlueprint()))
                .join();

        RecordedRequest blueprintRequest = controller.takeRequest();
        assertEquals("/v0/blueprints", blueprintRequest.getRequestUrl().encodedPath());
        JsonObject blueprint = ControllerServer.body(blueprintRequest);
        assertEquals("lobby", blueprint.get("name").getAsString());
        assertEquals(
                "https://example.com/paper.jar", blueprint.get("server_url").getAsString());
        assertEquals(
                "21",
                blueprint
                        .getAsJsonObject("runtime_config")
                        .getAsJsonObject("with")
                        .get("version")
                        .getAsString());

        RecordedRequest groupRequest = controller.takeRequest();
        assertEquals("/v0/server-groups", groupRequest.getRequestUrl().encodedPath());
        JsonObject source = ControllerServer.body(groupRequest).getAsJsonObject("source");
        assertEquals(BLUEPRINT_ID, source.get("blueprint").getAsString());
    }

    @Test
    void createGroupDeletesInlineBlueprintWhenGroupIsRejected() {
        controller.respond(201, "{\"blueprint_id\": \"" + BLUEPRINT_ID + "\"}");
        controller.respond(400, "{\"error\": \"name already taken\"}");
        controller.respond(200, "{}");

        CloudApiException failure =
                failure(api.createGroup(new CreateGroupRequest("lobby").setCreateBlueprint(paperBlueprint())));

        assertEquals(CloudApiException.Reason.INVALID_REQUEST, failure.getReason());
        controller.takeRequest();
        controller.takeRequest();
        RecordedRequest delete = controller.takeRequest();
        assertEquals("DELETE", delete.getMethod());
        assertEquals(BLUEPRINT_ID, delete.getRequestUrl().queryParameter("blueprint_id"));
    }

    @Test
    void createGroupRejectsInlineBlueprintCombinedWithImage() {
        CreateGroupRequest request = new CreateGroupRequest("lobby")
                .setCreateBlueprint(paperBlueprint())
                .setSource(SourceConfig.builder()
                        .image("ghcr.io/example/lobby:latest")
                        .build());

        CompletionException exception = assertThrows(
                CompletionException.class, () -> api.createGroup(request).join());

        assertInstanceOf(IllegalArgumentException.class, exception.getCause());
        assertEquals(0, controller.requestCount());
    }

    @Test
    void deleteGroupAlsoDeletesItsBlueprint() {
        controller.respond(200, "{\"server_groups\": [" + GROUP_JSON + "]}");
        controller.respond(200, "{}");
        controller.respond(200, "{}");

        api.deleteGroup(GROUP_ID).join();

        assertEquals("GET", controller.takeRequest().getMethod());
        RecordedRequest deleteGroup = controller.takeRequest();
        assertEquals("/v0/server-groups", deleteGroup.getRequestUrl().encodedPath());
        assertEquals("DELETE", deleteGroup.getMethod());
        RecordedRequest deleteBlueprint = controller.takeRequest();
        assertEquals("/v0/blueprints", deleteBlueprint.getRequestUrl().encodedPath());
        assertEquals(BLUEPRINT_ID, deleteBlueprint.getRequestUrl().queryParameter("blueprint_id"));
    }

    @Test
    void rejectedRollingRestartKeepsControllerMessage() {
        controller.respond(400, "{\"error\": \"rolling restart requires SLOTS scaling\"}");

        CloudApiException failure = failure(api.startRollingRestart(GROUP_ID));

        assertEquals(CloudApiException.Reason.INVALID_REQUEST, failure.getReason());
        assertTrue(failure.getMessage().contains("rolling restart requires SLOTS scaling"));
    }

    private static CreateBlueprintRequest paperBlueprint() {
        return CreateBlueprintRequest.builder()
                .serverSoftware("paper")
                .minecraftVersion("1.21.4")
                .serverUrl("https://example.com/paper.jar")
                .build();
    }

    private static JsonArray single(String value) {
        JsonArray array = new JsonArray();
        array.add(value);
        return array;
    }

    private static CloudApiException failure(CompletableFuture<?> future) {
        CompletionException exception = assertThrows(CompletionException.class, future::join);
        return assertInstanceOf(CloudApiException.class, exception.getCause());
    }
}
