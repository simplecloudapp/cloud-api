package app.simplecloud.api.internal.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.simplecloud.api.internal.ApiContext;
import app.simplecloud.api.player.CloudPlayer;
import app.simplecloud.api.player.CloudPlayer.ConnectResult;
import app.simplecloud.api.support.ControllerServer;
import app.simplecloud.api.support.TestContexts;
import app.simplecloud.api.web.apis.PlayersApi;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class PlayerApiImplTest {

    private static final UUID PLAYER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String CONNECT = "/v0/players/connect";

    private final ControllerServer controller = ControllerServer.start();
    private final PlayerApiImpl api = api();

    @AfterEach
    void stopController() {
        controller.close();
    }

    @Test
    void findsPlayerByName() {
        controller.on("GET", "/v0/players/name", "{\"id\":\"" + PLAYER_ID + "\",\"name\":\"Steve\",\"online\":true}");

        CloudPlayer player = api.find("Steve").join().orElseThrow();

        assertEquals(PLAYER_ID, player.getUniqueId());
        assertEquals("Steve", controller.onlyRequest("GET", "/v0/players/name").query("name"));
    }

    @Test
    void unknownPlayerIsEmpty() {
        controller.on("GET", "/v0/players/id", 404, "{\"error\":\"player not found\"}");

        assertEquals(Optional.empty(), api.find(PLAYER_ID).join());
    }

    @Test
    void namesLongerThanMinecraftAllowsAreNotLookedUp() {
        assertEquals(Optional.empty(), api.find("ThisNameIsTooLongForMinecraft").join());
        assertTrue(controller.requests().isEmpty());
    }

    @Test
    void connectReportsOfflinePlayer() {
        controller.on("POST", CONNECT, 404, "{\"error\":\"Player is not online\"}");

        assertEquals(ConnectResult.PLAYER_NOT_FOUND, connectToLobby());
    }

    @Test
    void connectReportsMissingServer() {
        controller.on("POST", CONNECT, 404, "{\"error\":\"server group not found\"}");

        assertEquals(ConnectResult.SERVER_NOT_FOUND, connectToLobby());
    }

    @Test
    void connectReportsPlayerAlreadyOnTheServer() {
        controller.on(
                "POST", CONNECT, "{\"success\":false,\"message\":\"Player is already connected to this server\"}");

        assertEquals(ConnectResult.ALREADY_CONNECTED, connectToLobby());
    }

    @Test
    void connectSendsGroupAndSelectionMode() {
        controller.on("POST", CONNECT, "{\"success\":true}");

        assertEquals(ConnectResult.SUCCESS, connectToLobby());

        ControllerServer.Request request = controller.onlyRequest("POST", CONNECT);
        assertEquals(PLAYER_ID.toString(), request.query("player_id"));
        assertEquals("lobby", request.json().get("server_group_id").getAsString());
        assertEquals("RANDOM", request.json().get("selection_mode").getAsString());
    }

    @Test
    void kickingAnOfflinePlayerReturnsFalse() {
        controller.on("POST", "/v0/players/kick", 404, "{\"error\":\"Player is not online\"}");

        assertFalse(api.kick(PLAYER_ID, null).join());
    }

    private ConnectResult connectToLobby() {
        return api.connectToGroup(PLAYER_ID, "lobby", null).join();
    }

    private PlayerApiImpl api() {
        ApiContext context = TestContexts.create(controller);
        return new PlayerApiImpl(context, null, context.api(PlayersApi::new));
    }
}
