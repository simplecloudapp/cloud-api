package app.simplecloud.api.internal.blueprint;

import app.simplecloud.api.CloudApiException;
import app.simplecloud.api.blueprint.Blueprint;
import app.simplecloud.api.blueprint.BlueprintApi;
import app.simplecloud.api.blueprint.UpdateBlueprintRequest;
import app.simplecloud.api.cache.CacheConfig;
import app.simplecloud.api.internal.ControllerServer;
import app.simplecloud.api.internal.cache.QueryCacheImpl;
import com.google.gson.JsonObject;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlueprintApiImplTest {

    private static final String BLUEPRINT_ID = "5a1c9e2b-3d4f-4a6b-8c7d-9e0f1a2b3c4d";
    private static final String OTHER_BLUEPRINT_ID = "9d8c7b6a-5f4e-4d3c-b2a1-0f9e8d7c6b5a";

    private ControllerServer controller;
    private QueryCacheImpl cache;
    private BlueprintApi api;

    @BeforeEach
    void setUp() {
        controller = new ControllerServer();
        cache = new QueryCacheImpl(CacheConfig.DEFAULT);
        api = new BlueprintApiImpl(controller.options(), cache, controller.apiClient(), Runnable::run);
    }

    @AfterEach
    void tearDown() {
        cache.shutdown();
        controller.close();
    }

    @Test
    void lookupsShareOneCachedList() {
        controller.respond(200, blueprints(blueprint(OTHER_BLUEPRINT_ID, "proxy"), blueprint(BLUEPRINT_ID, "lobby")));

        Blueprint byName = api.getBlueprintByName("lobby").join();
        Blueprint byId = api.getBlueprintById(BLUEPRINT_ID).join();

        assertEquals(BLUEPRINT_ID, byName.getBlueprintId());
        assertEquals("paper", byName.getServerSoftware());
        assertEquals("1.21.4", byName.getMinecraftVersion());
        assertEquals("lobby", byId.getName());
        assertEquals(1, controller.requestCount());

        RecordedRequest request = controller.takeRequest();
        assertEquals("/v0/blueprints", ControllerServer.url(request).encodedPath());
        assertEquals(ControllerServer.NETWORK_ID, request.getHeader("X-Network-ID"));
    }

    @Test
    void getBlueprintByIdFailsWithNotFoundForUnknownBlueprint() {
        controller.respond(200, blueprints(blueprint(OTHER_BLUEPRINT_ID, "proxy")));

        assertEquals(
                CloudApiException.Reason.NOT_FOUND,
                failure(api.getBlueprintById(BLUEPRINT_ID)).getReason());
    }

    @Test
    void findBlueprintByNameIsEmptyWhenControllerHasNoBlueprints() {
        controller.respond(200, "{}");

        assertTrue(api.findBlueprintByName("lobby").join().isEmpty());
    }

    @Test
    void updateBlueprintOnlySendsFieldsThatAreSetAndRefreshesList() {
        controller.respond(200, blueprints(blueprint(BLUEPRINT_ID, "lobby")));
        controller.respond(200, blueprint(BLUEPRINT_ID, "lobby"));
        controller.respond(200, blueprints(blueprint(BLUEPRINT_ID, "lobby")));

        api.getAllBlueprints().join();
        api.updateBlueprint(
                        BLUEPRINT_ID,
                        UpdateBlueprintRequest.builder()
                                .minecraftVersion("1.21.5")
                                .build())
                .join();
        api.getAllBlueprints().join();

        controller.takeRequest();
        RecordedRequest patch = controller.takeRequest();
        assertEquals("PATCH", patch.getMethod());
        assertEquals(BLUEPRINT_ID, ControllerServer.url(patch).queryParameter("blueprint_id"));
        JsonObject body = ControllerServer.body(patch);
        assertEquals("1.21.5", body.get("minecraft_version").getAsString());
        assertFalse(body.has("name"));
        assertFalse(body.has("server_url"));
        assertFalse(body.has("runtime_config"));
        assertEquals("GET", controller.takeRequest().getMethod());
    }

    @Test
    void deleteBlueprintRefreshesList() {
        controller.respond(200, blueprints(blueprint(BLUEPRINT_ID, "lobby")));
        controller.respond(200, "{}");
        controller.respond(200, blueprints());

        api.getAllBlueprints().join();
        api.deleteBlueprint(BLUEPRINT_ID).join();

        assertTrue(api.getAllBlueprints().join().isEmpty());
        controller.takeRequest();
        RecordedRequest delete = controller.takeRequest();
        assertEquals("DELETE", delete.getMethod());
        assertEquals(BLUEPRINT_ID, ControllerServer.url(delete).queryParameter("blueprint_id"));
    }

    @Test
    void rejectedDeleteKeepsControllerReason() {
        controller.respond(409, "{\"error\": \"blueprint is still in use\"}");

        CloudApiException failure = failure(api.deleteBlueprint(BLUEPRINT_ID));

        assertEquals(CloudApiException.Reason.CONFLICT, failure.getReason());
        String message = failure.getMessage();
        assertNotNull(message);
        assertTrue(message.contains("blueprint is still in use"));
    }

    private static String blueprint(String id, String name) {
        return """
                {"blueprint_id": "%s", "name": "%s", "server_software": "paper", "minecraft_version": "1.21.4",
                 "server_url": "https://example.com/paper.jar"}
                """
                .formatted(id, name);
    }

    private static String blueprints(String... blueprints) {
        return "{\"blueprints\": [" + String.join(",", blueprints) + "]}";
    }

    private static CloudApiException failure(CompletableFuture<?> future) {
        CompletionException exception = assertThrows(CompletionException.class, future::join);
        return assertInstanceOf(CloudApiException.class, exception.getCause());
    }
}
