package app.simplecloud.api.internal.blueprint;

import app.simplecloud.api.blueprint.CreateBlueprintRequest;
import app.simplecloud.api.blueprint.RuntimeConfig;
import app.simplecloud.api.blueprint.UpdateBlueprintRequest;
import app.simplecloud.api.internal.ApiContext;
import app.simplecloud.api.internal.util.blueprint.InlineBlueprintManager;
import app.simplecloud.api.support.ControllerServer;
import app.simplecloud.api.support.TestContexts;
import app.simplecloud.api.web.apis.BlueprintsApi;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlueprintApiImplTest {

    private static final String BLUEPRINT_ID = "5d3c2b1a-0f9e-4d8c-b7a6-123456789abc";
    private static final String BLUEPRINTS = "/v0/blueprints";

    private final ControllerServer controller = ControllerServer.start();

    @AfterEach
    void stopController() {
        controller.close();
    }

    @Test
    void createResolvesTheServerUrlAndFillsInDefaults() {
        controller.on("POST", BLUEPRINTS, blueprint("paper"));
        BlueprintApiImpl api = api(request -> "https://example.com/" + request.getServerSoftware() + "-" + request.getMinecraftVersion() + ".jar");

        api.createBlueprint(CreateBlueprintRequest.builder()
                .name(" paper ")
                .serverSoftware("paper")
                .minecraftVersion("1.21.4")
                .build()).join();

        JsonObject body = controller.onlyRequest("POST", BLUEPRINTS).json();
        assertEquals("paper", body.get("name").getAsString());
        assertEquals("https://example.com/paper-1.21.4.jar", body.get("server_url").getAsString());
        assertEquals(JsonParser.parseString("[\"internal/setup\"]"), body.get("workflow_steps"));
        assertEquals("java", body.getAsJsonObject("runtime_config").get("type").getAsString());
        assertEquals("21", body.getAsJsonObject("runtime_config").getAsJsonObject("with").get("version").getAsString());
    }

    @Test
    void createFailsWhenTheManifestHasNoDownloadLink() {
        BlueprintApiImpl api = api(request -> null);

        CompletionException failure = assertThrows(CompletionException.class, () -> api.createBlueprint(CreateBlueprintRequest.builder()
                .name("paper")
                .serverSoftware("paper")
                .minecraftVersion("0.0.1")
                .build()).join());

        assertInstanceOf(IllegalArgumentException.class, failure.getCause());
        assertTrue(controller.requests().isEmpty());
    }

    @Test
    void createRequiresAName() {
        CompletionException failure = assertThrows(CompletionException.class,
                () -> api().createBlueprint(CreateBlueprintRequest.builder().name(" ").build()).join());

        assertInstanceOf(IllegalArgumentException.class, failure.getCause());
        assertTrue(controller.requests().isEmpty());
    }

    @Test
    void findByIdIgnoresCase() {
        controller.on("GET", BLUEPRINTS, "{\"blueprints\":[" + blueprint("paper") + "]}");

        assertEquals("paper", api().getBlueprintById(BLUEPRINT_ID.toUpperCase()).join().getName());
    }

    @Test
    void runtimeUpdateNeedsAType() {
        CompletionException failure = assertThrows(CompletionException.class, () -> api().updateBlueprint(BLUEPRINT_ID,
                UpdateBlueprintRequest.builder().runtimeConfig(RuntimeConfig.builder().with(Map.of("version", "25")).build()).build()
        ).join());

        assertInstanceOf(IllegalArgumentException.class, failure.getCause());
        assertTrue(controller.requests().isEmpty());
    }

    private BlueprintApiImpl api() {
        return api(request -> null);
    }

    private BlueprintApiImpl api(InlineBlueprintManager.ServerUrlResolver resolver) {
        ApiContext context = TestContexts.create(controller);
        return new BlueprintApiImpl(context, context.api(BlueprintsApi::new), resolver);
    }

    private static String blueprint(String name) {
        return "{\"blueprint_id\":\"" + BLUEPRINT_ID + "\",\"name\":\"" + name + "\""
                + ",\"created_at\":\"2026-01-01T00:00:00Z\",\"updated_at\":\"2026-01-01T00:00:00Z\"}";
    }
}
