package app.simplecloud.api.internal.persistentserver;

import app.simplecloud.api.blueprint.CreateBlueprintRequest;
import app.simplecloud.api.group.WorkflowWhen;
import app.simplecloud.api.group.WorkflowsConfig;
import app.simplecloud.api.internal.ApiContext;
import app.simplecloud.api.internal.util.blueprint.InlineBlueprintManager;
import app.simplecloud.api.persistentserver.CreatePersistentServerRequest;
import app.simplecloud.api.persistentserver.UpdatePersistentServerRequest;
import app.simplecloud.api.support.ControllerServer;
import app.simplecloud.api.support.ControllerServer.Request;
import app.simplecloud.api.support.TestContexts;
import app.simplecloud.api.web.apis.BlueprintsApi;
import app.simplecloud.api.web.apis.PersistentServersApi;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PersistentServerApiImplTest {

    private static final String SERVER_ID = "3c2b1a0f-9e8d-4c7b-a6f5-0123456789ab";
    private static final String PERSISTENT_SERVERS = "/v0/persistent-servers";
    private static final String BLUEPRINTS = "/v0/blueprints";

    private final ControllerServer controller = ControllerServer.start();

    @AfterEach
    void stopController() {
        controller.close();
    }

    @Test
    void createWithInlineBlueprintUsesItAsSource() {
        controller.on("POST", BLUEPRINTS, "{\"blueprint_id\":\"bp-new\"}");
        controller.on("POST", PERSISTENT_SERVERS, persistentServer());

        api().createPersistentServer(CreatePersistentServerRequest.builder()
                .name("survival")
                .serverhostId("host-1")
                .createBlueprint(CreateBlueprintRequest.builder().serverUrl("https://example.com/paper.jar").build())
                .build()).join();

        List<Request> requests = controller.requests();
        assertEquals(List.of(BLUEPRINTS, PERSISTENT_SERVERS), requests.stream().map(Request::path).toList());
        JsonObject body = requests.get(1).json();
        assertEquals("host-1", body.get("serverhost_id").getAsString());
        assertEquals("bp-new", body.getAsJsonObject("source").get("blueprint").getAsString());
        assertEquals(JsonParser.parseString("[\"survival\"]"), body.get("tags"));
    }

    @Test
    void workflowUpdateKeepsTheCurrentStopWorkflows() {
        controller.on("GET", PERSISTENT_SERVERS, "{\"persistent_servers\":[" + persistentServer(
                "\"workflows\":{\"when\":{\"start\":[\"internal/setup\"],\"stop\":[\"internal/cleanup\"]}}") + "]}");
        controller.on("PATCH", PERSISTENT_SERVERS, persistentServer());

        api().updatePersistentServer(SERVER_ID, UpdatePersistentServerRequest.builder()
                .workflows(WorkflowsConfig.builder().when(WorkflowWhen.builder().start(List.of("custom/setup")).build()).build())
                .build()).join();

        JsonObject when = controller.onlyRequest("PATCH", PERSISTENT_SERVERS).json().getAsJsonObject("workflows").getAsJsonObject("when");
        assertEquals(JsonParser.parseString("[\"custom/setup\"]"), when.get("start"));
        assertEquals(JsonParser.parseString("[\"internal/cleanup\"]"), when.get("stop"));
    }

    @Test
    void deleteAlsoDeletesTheReferencedBlueprint() {
        controller.on("GET", PERSISTENT_SERVERS, "{\"persistent_servers\":[" + persistentServer(
                "\"source\":{\"type\":\"blueprint\",\"blueprint\":\"bp-1\"}") + "]}");
        controller.on("DELETE", PERSISTENT_SERVERS, "{}");
        controller.on("DELETE", BLUEPRINTS, "{}");

        api().deletePersistentServer(SERVER_ID).join();

        assertEquals(SERVER_ID, controller.onlyRequest("DELETE", PERSISTENT_SERVERS).query("persistent_server_id"));
        assertEquals("bp-1", controller.onlyRequest("DELETE", BLUEPRINTS).query("blueprint_id"));
    }

    @Test
    void replaceDoesNotSupportInlineBlueprints() {
        CompletionException failure = assertThrows(CompletionException.class, () -> api().replacePersistentServer(SERVER_ID,
                CreatePersistentServerRequest.builder()
                        .name("survival")
                        .createBlueprint(CreateBlueprintRequest.builder().serverUrl("https://example.com/paper.jar").build())
                        .build()).join());

        assertInstanceOf(IllegalArgumentException.class, failure.getCause());
        assertTrue(controller.requests().isEmpty());
    }

    private PersistentServerApiImpl api() {
        ApiContext context = TestContexts.create(controller);
        InlineBlueprintManager blueprints = new InlineBlueprintManager(
                context.options(),
                context.api(BlueprintsApi::new),
                request -> "https://example.com/server.jar"
        );
        return new PersistentServerApiImpl(context, context.api(PersistentServersApi::new), blueprints);
    }

    private static String persistentServer() {
        return persistentServer(null);
    }

    private static String persistentServer(String extraFields) {
        return "{\"persistent_server_id\":\"" + SERVER_ID + "\",\"name\":\"survival\",\"type\":\"SERVER\""
                + ",\"created_at\":\"2026-01-01T00:00:00Z\",\"updated_at\":\"2026-01-01T00:00:00Z\""
                + (extraFields == null ? "" : "," + extraFields)
                + "}";
    }
}
