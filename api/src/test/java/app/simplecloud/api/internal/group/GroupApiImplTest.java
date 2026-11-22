package app.simplecloud.api.internal.group;

import app.simplecloud.api.CloudApiException;
import app.simplecloud.api.blueprint.CreateBlueprintRequest;
import app.simplecloud.api.cache.CacheConfig;
import app.simplecloud.api.cache.QueryCache;
import app.simplecloud.api.group.CreateGroupRequest;
import app.simplecloud.api.group.Group;
import app.simplecloud.api.group.ScalingConfig;
import app.simplecloud.api.group.ScalingMode;
import app.simplecloud.api.group.SourceConfig;
import app.simplecloud.api.group.UpdateGroupRequest;
import app.simplecloud.api.internal.ApiContext;
import app.simplecloud.api.internal.cache.NoOpQueryCache;
import app.simplecloud.api.internal.cache.QueryCacheImpl;
import app.simplecloud.api.internal.util.blueprint.InlineBlueprintManager;
import app.simplecloud.api.support.ControllerServer;
import app.simplecloud.api.support.ControllerServer.Request;
import app.simplecloud.api.support.TestContexts;
import app.simplecloud.api.web.apis.BlueprintsApi;
import app.simplecloud.api.web.apis.ServerGroupsApi;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroupApiImplTest {

    private static final String GROUP_ID = "0b6e2c4a-7d1f-4e3b-9a5c-2f8d6e1b3c7a";
    private static final String GROUPS = "/v0/server-groups";
    private static final String BLUEPRINTS = "/v0/blueprints";

    private final ControllerServer controller = ControllerServer.start();

    @AfterEach
    void stopController() {
        controller.close();
    }

    @Test
    void findsGroupByNameWithNetworkCredentials() {
        controller.on("GET", GROUPS, groupList(group("Lobby")));

        Group group = api().getGroupByName("Lobby").join();

        assertEquals(GROUP_ID, group.getServerGroupId());
        assertEquals("Lobby", group.getName());
        Request request = controller.onlyRequest("GET", GROUPS);
        assertEquals("Lobby", request.query("name"));
        assertEquals(TestContexts.NETWORK_ID, request.header("X-Network-ID"));
        assertEquals(TestContexts.NETWORK_SECRET, request.header("X-Network-Credential"));
    }

    @Test
    void missingGroupFailsWithNotFound() {
        controller.on("GET", GROUPS, groupList());

        CompletionException failure = assertThrows(CompletionException.class, () -> api().getGroupByName("Lobby").join());

        CloudApiException exception = assertInstanceOf(CloudApiException.class, failure.getCause());
        assertEquals(CloudApiException.Reason.NOT_FOUND, exception.getReason());
    }

    @Test
    void lookupByIdSkipsTheControllerForInvalidIds() {
        assertTrue(api().findGroupById("Lobby").join().isEmpty());
        assertTrue(controller.requests().isEmpty());
    }

    @Test
    void createFillsInDefaults() {
        controller.on("POST", GROUPS, group("Lobby"));

        api().createGroup(CreateGroupRequest.builder()
                .name("Lobby")
                .source(SourceConfig.builder().blueprint("bp-1").build())
                .build()).join();

        JsonObject body = controller.onlyRequest("POST", GROUPS).json();
        assertEquals("SERVER", body.get("type").getAsString());
        assertEquals(50, body.get("max_players").getAsInt());
        assertTrue(body.get("active").getAsBoolean());
        assertEquals(JsonParser.parseString("[\"Lobby\"]"), body.get("tags"));
        assertEquals(0, body.getAsJsonObject("scaling").get("min_servers").getAsInt());
        assertEquals(1, body.getAsJsonObject("scaling").get("max_servers").getAsInt());
        assertEquals("blacklist", body.getAsJsonObject("deployment").get("strategy").getAsString());
        assertEquals(JsonParser.parseString("[\"internal/setup\"]"),
                body.getAsJsonObject("workflows").getAsJsonObject("when").get("start"));
        assertEquals("bp-1", body.getAsJsonObject("source").get("blueprint").getAsString());
    }

    @Test
    void playersScalingModeIsSentAsServers() {
        controller.on("POST", GROUPS, group("Lobby"));

        api().createGroup(CreateGroupRequest.builder()
                .name("Lobby")
                .scaling(ScalingConfig.builder().scalingMode(ScalingMode.PLAYERS).build())
                .build()).join();

        JsonObject scaling = controller.onlyRequest("POST", GROUPS).json().getAsJsonObject("scaling");
        assertEquals("SERVERS", scaling.get("scaling_mode").getAsString());
    }

    @Test
    void createWithInlineBlueprintCreatesTheBlueprintFirst() {
        controller.on("POST", BLUEPRINTS, "{\"blueprint_id\":\"bp-new\",\"name\":\"Lobby\"}");
        controller.on("POST", GROUPS, group("Lobby"));

        api().createGroup(CreateGroupRequest.builder()
                .name("Lobby")
                .createBlueprint(inlineBlueprint())
                .build()).join();

        List<Request> requests = controller.requests();
        assertEquals(List.of(BLUEPRINTS, GROUPS), requests.stream().map(Request::path).toList());
        JsonObject blueprint = requests.get(0).json();
        assertEquals("Lobby", blueprint.get("name").getAsString());
        assertEquals(JsonParser.parseString("[\"internal/setup\"]"), blueprint.get("workflow_steps"));
        JsonObject source = requests.get(1).json().getAsJsonObject("source");
        assertEquals("blueprint", source.get("type").getAsString());
        assertEquals("bp-new", source.get("blueprint").getAsString());
    }

    @Test
    void failedCreateRemovesTheInlineBlueprintAgain() {
        controller.on("POST", BLUEPRINTS, "{\"blueprint_id\":\"bp-new\"}");
        controller.on("POST", GROUPS, 409, "{\"error\":\"group already exists\"}");
        controller.on("DELETE", BLUEPRINTS, "{}");

        CompletionException failure = assertThrows(CompletionException.class, () -> api().createGroup(CreateGroupRequest.builder()
                .name("Lobby")
                .createBlueprint(inlineBlueprint())
                .build()).join());

        CloudApiException exception = assertInstanceOf(CloudApiException.class, failure.getCause());
        assertEquals(CloudApiException.Reason.CONFLICT, exception.getReason());
        assertEquals("group already exists (409)", exception.getMessage());
        assertEquals("bp-new", controller.onlyRequest("DELETE", BLUEPRINTS).query("blueprint_id"));
    }

    @Test
    void inlineBlueprintCannotBeCombinedWithAnImage() {
        CompletionException failure = assertThrows(CompletionException.class, () -> api().createGroup(CreateGroupRequest.builder()
                .name("Lobby")
                .source(SourceConfig.builder().image("nginx").build())
                .createBlueprint(inlineBlueprint())
                .build()).join());

        assertInstanceOf(IllegalArgumentException.class, failure.getCause());
        assertTrue(controller.requests().isEmpty());
    }

    @Test
    void scalingUpdateIsMergedWithTheCurrentGroup() {
        controller.on("GET", GROUPS, groupList(group("Lobby", "\"scaling\":{\"min_servers\":2,\"max_servers\":5}")));
        controller.on("PATCH", GROUPS, group("Lobby"));

        api().updateGroup(GROUP_ID, UpdateGroupRequest.builder()
                .scaling(ScalingConfig.builder().maxServers(10).build())
                .build()).join();

        Request patch = controller.onlyRequest("PATCH", GROUPS);
        assertEquals(GROUP_ID, patch.query("server_group_id"));
        JsonObject scaling = patch.json().getAsJsonObject("scaling");
        assertEquals(2, scaling.get("min_servers").getAsInt());
        assertEquals(10, scaling.get("max_servers").getAsInt());
    }

    @Test
    void deleteAlsoDeletesTheReferencedBlueprint() {
        controller.on("GET", GROUPS, groupList(group("Lobby", "\"source\":{\"type\":\"blueprint\",\"blueprint\":\"bp-1\"}")));
        controller.on("DELETE", GROUPS, "{}");
        controller.on("DELETE", BLUEPRINTS, "{}");

        api().deleteGroup(GROUP_ID).join();

        assertEquals(GROUP_ID, controller.onlyRequest("DELETE", GROUPS).query("server_group_id"));
        assertEquals("bp-1", controller.onlyRequest("DELETE", BLUEPRINTS).query("blueprint_id"));
    }

    @Test
    void cachedGroupListIsFetchedAgainAfterAChange() {
        QueryCacheImpl cache = new QueryCacheImpl(CacheConfig.DEFAULT);
        try {
            GroupApiImpl api = api(cache);
            controller.on("GET", GROUPS, groupList(group("Lobby")));
            controller.on("PUT", GROUPS + "/properties", "{\"properties\":{\"motd\":\"hi\"}}");

            api.getAllGroups(null).join();
            api.getAllGroups(null).join();
            assertEquals(1, controller.requests("GET", GROUPS).size());

            api.setGroupProperties(GROUP_ID, Map.of("motd", "hi")).join();
            api.getAllGroups(null).join();
            assertEquals(2, controller.requests("GET", GROUPS).size());
        } finally {
            cache.shutdown();
        }
    }

    private GroupApiImpl api() {
        return api(new NoOpQueryCache());
    }

    private GroupApiImpl api(QueryCache cache) {
        ApiContext context = TestContexts.create(controller, cache);
        InlineBlueprintManager blueprints = new InlineBlueprintManager(
                context.options(),
                context.api(BlueprintsApi::new),
                request -> "https://example.com/server.jar"
        );
        return new GroupApiImpl(context, context.api(ServerGroupsApi::new), blueprints);
    }

    private static CreateBlueprintRequest inlineBlueprint() {
        return CreateBlueprintRequest.builder()
                .configurator("paper")
                .serverUrl("https://example.com/paper.jar")
                .build();
    }

    private static String groupList(String... groups) {
        return "{\"server_groups\":[" + String.join(",", groups) + "]}";
    }

    private static String group(String name) {
        return group(name, null);
    }

    private static String group(String name, String extraFields) {
        return "{\"server_group_id\":\"" + GROUP_ID + "\",\"name\":\"" + name + "\",\"type\":\"SERVER\""
                + ",\"created_at\":\"2026-01-01T00:00:00Z\",\"updated_at\":\"2026-01-01T00:00:00Z\""
                + (extraFields == null ? "" : "," + extraFields)
                + "}";
    }
}
