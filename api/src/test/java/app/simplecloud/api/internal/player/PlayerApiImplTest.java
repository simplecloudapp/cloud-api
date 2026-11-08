package app.simplecloud.api.internal.player;

import app.simplecloud.api.CloudApiException;
import app.simplecloud.api.internal.ControllerServer;
import app.simplecloud.api.player.CloudPlayer;
import app.simplecloud.api.player.ConnectResponse;
import app.simplecloud.api.player.ConnectTarget;
import app.simplecloud.api.player.PlayerApi;
import app.simplecloud.api.player.PlayerClientSettings;
import app.simplecloud.api.player.PlayerQuery;
import app.simplecloud.api.player.ServerSelectionMode;
import app.simplecloud.api.player.UpdateClientSettingsRequest;
import com.google.gson.JsonObject;
import okhttp3.HttpUrl;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerApiImplTest {

    private static final UUID PLAYER_ID = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
    private static final String SERVER_ID = "6f1c2a51-0c8e-4f0a-9d55-3b8e1f6c2d10";
    private static final String GROUP_ID = "1b2e7c3d-5a64-4e2f-8c1d-9a0b3c4d5e6f";
    private static final String PLAYER_JSON = """
            {"id": "069a79f4-44e9-4726-a5be-fca90e38aaf5", "name": "Steve", "online": true,
             "connected_server_name": "lobby-1", "online_time_seconds": 3600, "properties": {"rank": "admin"}}
            """;

    private ControllerServer controller;
    private PlayerApi api;

    @BeforeEach
    void setUp() {
        controller = new ControllerServer();
        api = new PlayerApiImpl(controller.options(), null, controller.apiClient(), Runnable::run);
    }

    @AfterEach
    void tearDown() {
        controller.close();
    }

    @Test
    void findReadsPlayerFromController() {
        controller.respond(200, PLAYER_JSON);

        CloudPlayer player = api.find(PLAYER_ID).join().orElseThrow();

        assertEquals(PLAYER_ID, player.getUniqueId());
        assertEquals("Steve", player.getName());
        assertTrue(player.isOnline());
        assertEquals("lobby-1", player.getConnectedServerName());
        assertEquals(3600, player.getOnlineTimeSeconds());
        assertEquals(Map.of("rank", "admin"), player.getProperties());

        RecordedRequest request = controller.takeRequest();
        assertEquals("/v0/players/id", ControllerServer.url(request).encodedPath());
        assertEquals(PLAYER_ID.toString(), ControllerServer.url(request).queryParameter("player_id"));
        assertEquals(ControllerServer.NETWORK_ID, request.getHeader("X-Network-ID"));
    }

    @Test
    void findReturnsEmptyForUnknownPlayer() {
        controller.respond(404, "{\"error\": \"Player not found\"}");

        assertTrue(api.find(PLAYER_ID).join().isEmpty());
    }

    @Test
    void getFailsWithNotFoundForUnknownPlayer() {
        controller.respond(404, "{\"error\": \"Player not found\"}");

        assertEquals(
                CloudApiException.Reason.NOT_FOUND, failure(api.get(PLAYER_ID)).getReason());
    }

    @Test
    void findByInvalidNameDoesNotAskController() {
        assertTrue(api.find("").join().isEmpty());
        assertTrue(api.find("a-name-longer-than-16").join().isEmpty());

        assertEquals(0, controller.requestCount());
    }

    @Test
    void listSendsQueryAsParameters() {
        controller.respond(200, "{\"players\": [" + PLAYER_JSON + "]}");

        var players = api.list(PlayerQuery.create()
                        .filterByOnline(true)
                        .search("ste")
                        .sortBy(PlayerQuery.SortField.ONLINE_TIME)
                        .sortOrder(PlayerQuery.SortOrder.DESC)
                        .limit(10))
                .join();

        assertEquals(1, players.size());
        HttpUrl url = ControllerServer.url(controller.takeRequest());
        assertEquals("true", url.queryParameter("online"));
        assertEquals("ste", url.queryParameter("search"));
        assertEquals("online_time_seconds", url.queryParameter("sort_by"));
        assertEquals("desc", url.queryParameter("sort_order"));
        assertEquals("10", url.queryParameter("limit"));
    }

    @Test
    void connectSendsServerNameAndMapsResult() {
        controller.respond(200, "{\"success\": false, \"result\": \"ALREADY_CONNECTED\"}");

        assertEquals(
                CloudPlayer.ConnectResult.ALREADY_CONNECTED,
                api.connectToServerByName(PLAYER_ID, "lobby-1").join());

        RecordedRequest request = controller.takeRequest();
        assertEquals("/v0/players/connect", ControllerServer.url(request).encodedPath());
        assertEquals(
                "lobby-1", ControllerServer.body(request).get("server_name").getAsString());
    }

    @Test
    void connectReportsOfflinePlayerAsPlayerNotFound() {
        controller.respond(404, "{\"error\": \"Player is not online\"}");

        assertEquals(
                CloudPlayer.ConnectResult.PLAYER_NOT_FOUND,
                api.connectToServerByName(PLAYER_ID, "lobby-1").join());
    }

    @Test
    void connectReportsUnknownServerAsServerNotFound() {
        controller.respond(404, "{\"error\": \"Server not found\"}");

        assertEquals(
                CloudPlayer.ConnectResult.SERVER_NOT_FOUND,
                api.connectToServerByName(PLAYER_ID, "lobby-9").join());
    }

    @Test
    void connectFailsWhenNameIsAmbiguous() {
        controller.respond(409, "{\"error\": \"Server name is ambiguous\"}");

        assertEquals(
                CloudApiException.Reason.CONFLICT,
                failure(api.connectToServerByName(PLAYER_ID, "lobby")).getReason());
    }

    @Test
    void connectToServerByIdSendsServerIdOnly() {
        controller.respond(200, "{\"success\": true, \"result\": \"SUCCESS\", \"server_name\": \"lobby-1\"}");

        assertEquals(
                CloudPlayer.ConnectResult.SUCCESS,
                api.connectToServerById(PLAYER_ID, SERVER_ID).join());

        JsonObject body = ControllerServer.body(controller.takeRequest());
        assertEquals(SERVER_ID, body.get("server_id").getAsString());
        assertFalse(body.has("server_name"));
    }

    @Test
    void connectToReturnsTheChosenServer() {
        controller.respond(200, "{\"success\": true, \"result\": \"SUCCESS\", \"server_name\": \"lobby-2\"}");

        ConnectResponse response = api.connectTo(
                        PLAYER_ID, new ConnectTarget.GroupId(GROUP_ID, ServerSelectionMode.FIRST))
                .join();

        assertEquals(new ConnectResponse(CloudPlayer.ConnectResult.SUCCESS, "lobby-2"), response);
        JsonObject body = ControllerServer.body(controller.takeRequest());
        assertEquals(GROUP_ID, body.get("server_group_id").getAsString());
        assertEquals("FIRST", body.get("selection_mode").getAsString());
    }

    @Test
    void connectToReportsNoServerWithoutServerName() {
        controller.respond(
                200,
                "{\"success\": false, \"result\": \"NO_SERVER_AVAILABLE\", \"message\": \"Target server not found\"}");

        assertEquals(
                new ConnectResponse(CloudPlayer.ConnectResult.NO_SERVER_AVAILABLE, null),
                api.connectTo(PLAYER_ID, new ConnectTarget.GroupName("lobby", null))
                        .join());
    }

    @Test
    void kickReturnsFalseForOfflinePlayer() {
        controller.respond(404, "{\"error\": \"Player is not online\"}");

        assertFalse(api.kick(PLAYER_ID, null).join());
    }

    @Test
    void removeOnlineTimeNeverGoesBelowZero() {
        controller.respond(200, "{\"online_time_seconds\": 30}");
        controller.respond(200, PLAYER_JSON);

        api.removeOnlineTimeSeconds(PLAYER_ID, 100).join();

        controller.takeRequest();
        RecordedRequest patch = controller.takeRequest();
        assertEquals("PATCH", patch.getMethod());
        assertEquals(0, ControllerServer.body(patch).get("online_time_seconds").getAsInt());
    }

    @Test
    void updateClientSettingsOnlySendsFieldsThatAreSet() {
        controller.respond(200, """
                {"success": true, "settings": {"locale": "de_DE", "view_distance": 8, "chat_mode": "CHAT_MODE_ENABLED"}}
                """);

        PlayerClientSettings settings = api.updateClientSettings(
                        PLAYER_ID,
                        UpdateClientSettingsRequest.builder()
                                .locale("de_DE")
                                .viewDistance(8)
                                .build())
                .join();

        assertEquals("de_DE", settings.getLocale());
        assertEquals(8, settings.getViewDistance());
        assertEquals(PlayerClientSettings.ChatMode.ENABLED, settings.getChatMode());

        JsonObject body = ControllerServer.body(controller.takeRequest());
        assertEquals("de_DE", body.get("locale").getAsString());
        assertEquals(8, body.get("view_distance").getAsInt());
        assertFalse(body.has("chat_mode"));
        assertFalse(body.has("main_hand"));
    }

    @Test
    void sessionPagingIsValidatedBeforeAskingController() {
        CompletionException exception = assertThrows(
                CompletionException.class,
                () -> api.getSessions(PLAYER_ID, 101, 0).join());

        assertInstanceOf(IllegalArgumentException.class, exception.getCause());
        assertEquals(0, controller.requestCount());
    }

    private static CloudApiException failure(CompletableFuture<?> future) {
        CompletionException exception = assertThrows(CompletionException.class, future::join);
        return assertInstanceOf(CloudApiException.class, exception.getCause());
    }
}
