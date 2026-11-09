package app.simplecloud.api.internal.player;

import app.simplecloud.api.CloudApiException;
import app.simplecloud.api.CloudApiOptions;
import app.simplecloud.api.internal.web.ApiClients;
import app.simplecloud.api.player.ConnectOutcome;
import app.simplecloud.api.player.ConnectStatus;
import app.simplecloud.api.player.ConnectTarget;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Runs connects through the generated REST client against a local HTTP server, because how that client
 * fails on an answer it can't read decides which exception plugins see.
 */
class PlayerApiImplControllerAnswerTest {

    private static final UUID PLAYER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final ConnectTarget TARGET = ConnectTarget.groupName("lobby");

    private MockWebServer controller;

    @BeforeEach
    void startController() throws IOException {
        controller = new MockWebServer();
        controller.start();
    }

    @AfterEach
    void stopController() throws IOException {
        controller.close();
    }

    @Test
    void connectTo_readsTheControllersResultAndServer() {
        controller.enqueue(jsonAnswer("{\"success\":true,\"result\":\"SUCCESS\",\"server_name\":\"lobby-2\"}"));

        ConnectOutcome outcome = playerApi().connectTo(PLAYER_ID, TARGET).join();

        assertEquals(ConnectOutcome.of(ConnectStatus.SUCCESS, "lobby-2"), outcome);
    }

    @Test
    void connectTo_withResultThisVersionDoesntKnow_failsAsUnknown() {
        controller.enqueue(jsonAnswer("{\"success\":false,\"result\":\"CANCELLED\"}"));

        CloudApiException failure = connectFailure();

        assertEquals(CloudApiException.Reason.UNKNOWN, failure.getReason());
    }

    @Test
    void connectTo_withBrokenOffAnswer_failsAsUnknown() {
        controller.enqueue(jsonAnswer("{\"success\":true,\"result\":"));

        CloudApiException failure = connectFailure();

        assertEquals(CloudApiException.Reason.UNKNOWN, failure.getReason());
    }

    private CloudApiException connectFailure() {
        PlayerApiImpl api = playerApi();
        CompletionException error = assertThrows(CompletionException.class, () -> api.connectTo(PLAYER_ID, TARGET).join());
        return assertInstanceOf(CloudApiException.class, error.getCause());
    }

    private PlayerApiImpl playerApi() {
        CloudApiOptions options = CloudApiOptions.builder()
                .controllerUrl(controller.url("/").toString())
                .networkId("network")
                .networkSecret("secret")
                .build();
        return new PlayerApiImpl(options, null, ApiClients.create(options));
    }

    private static MockResponse jsonAnswer(String body) {
        return new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(body);
    }
}
