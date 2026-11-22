package app.simplecloud.api.internal.server;

import app.simplecloud.api.CloudApiException;
import app.simplecloud.api.cache.CacheConfig;
import app.simplecloud.api.internal.cache.QueryCacheImpl;
import app.simplecloud.api.server.Server;
import app.simplecloud.api.server.ServerQuery;
import app.simplecloud.api.server.ServerState;
import app.simplecloud.api.server.UpdateServerRequest;
import app.simplecloud.api.support.ControllerServer;
import app.simplecloud.api.support.TestContexts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ServerApiImplTest {

    private static final String SERVER_ID = "7a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d";
    private static final String SERVERS = "/v0/servers";

    private final ControllerServer controller = ControllerServer.start();

    @AfterEach
    void stopController() {
        controller.close();
    }

    @Test
    void queryFiltersAreSentAsCommaSeparatedLists() {
        controller.on("GET", SERVERS, servers(server(1)));

        new ServerApiImpl(TestContexts.create(controller)).getAllServers(ServerQuery.create()
                .filterByServerGroupName("lobby", "game")
                .filterByState(ServerState.AVAILABLE, ServerState.INGAME)).join();

        ControllerServer.Request request = controller.onlyRequest("GET", SERVERS);
        assertEquals("lobby,game", request.query("name"));
        assertEquals("AVAILABLE,INGAME", request.query("state"));
    }

    @Test
    void findsServerByGroupAndNumericalId() {
        controller.on("GET", SERVERS, servers(server(1), server(2)));

        Server server = new ServerApiImpl(TestContexts.create(controller)).getServerByNumericalId("lobby", 2).join();

        assertEquals(2, server.getNumericalId());
        ControllerServer.Request request = controller.onlyRequest("GET", SERVERS);
        assertEquals("lobby", request.query("name"));
        assertEquals("2", request.query("numerical_id"));
    }

    @Test
    void updatedServerIsServedFromTheCache() {
        QueryCacheImpl cache = new QueryCacheImpl(CacheConfig.DEFAULT);
        try {
            ServerApiImpl api = new ServerApiImpl(TestContexts.create(controller, cache));
            controller.on("PATCH", SERVERS, "{\"server\":" + server(1) + "}");

            api.updateServer(SERVER_ID, UpdateServerRequest.builder().maxPlayers(20).build()).join();
            Server server = api.getServerById(SERVER_ID).join();

            assertEquals(SERVER_ID, server.getServerId());
            assertEquals(0, controller.requests("GET", SERVERS).size());
        } finally {
            cache.shutdown();
        }
    }

    @Test
    void updateWithoutServerInResponseFails() {
        controller.on("PATCH", SERVERS, "{}");

        CompletionException failure = assertThrows(CompletionException.class, () -> new ServerApiImpl(TestContexts.create(controller))
                .updateServer(SERVER_ID, UpdateServerRequest.builder().maxPlayers(20).build()).join());

        CloudApiException exception = assertInstanceOf(CloudApiException.class, failure.getCause());
        assertEquals(CloudApiException.Reason.UNKNOWN, exception.getReason());
    }

    private static String servers(String... servers) {
        return "{\"servers\":[" + String.join(",", servers) + "]}";
    }

    private static String server(int numericalId) {
        return "{\"server_id\":\"" + SERVER_ID + "\",\"numerical_id\":" + numericalId + ",\"state\":\"AVAILABLE\""
                + ",\"created_at\":\"2026-01-01T00:00:00Z\",\"updated_at\":\"2026-01-01T00:00:00Z\"}";
    }
}
