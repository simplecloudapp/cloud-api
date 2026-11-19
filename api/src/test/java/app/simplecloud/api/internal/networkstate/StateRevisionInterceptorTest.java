package app.simplecloud.api.internal.networkstate;

import app.simplecloud.api.internal.nats.FakeNatsConnection;
import app.simplecloud.api.web.ApiClient;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StateRevisionInterceptorTest {

    private static final String NETWORK_ID = "0d000000-0000-4000-8000-000000000001";
    /**
     * 2^32 and 2^64 - 1, so both headers must parse as unsigned numbers.
     */
    private static final String EPOCH = "4294967296";
    private static final String REVISION = "18446744073709551615";
    private static final MediaType JSON = MediaType.get("application/json");

    private final RecordingView view = RecordingView.withoutCopy();
    private final MockWebServer controller = new MockWebServer();
    private NetworkViewShell shell;
    private OkHttpClient client;

    @BeforeEach
    void start() throws IOException {
        controller.start();
        SnapshotLoader unusedSnapshots = new SnapshotLoader(new ApiClient(), "http://127.0.0.1:1", NETWORK_ID);
        FakeNatsConnection nats = FakeNatsConnection.withoutRequests();
        shell = NetworkViewShell.start(NETWORK_ID, nats.connection(), unusedSnapshots, view, () -> 0);
        client = StateRevisionInterceptor.addTo(new OkHttpClient(), shell);
    }

    @AfterEach
    void stop() throws IOException {
        shell.close();
        controller.close();
    }

    @Test
    void aWriteWithRevisionHeadersHandsTheViewItsRevision() throws IOException {
        controller.enqueue(withRevisionHeaders(new MockResponse()));

        send("PATCH", "/v0/server-groups/lobby");

        ViewInput.WriteReturned expected = new ViewInput.WriteReturned(1L << 32, -1L);
        assertEquals(List.of(expected), writesTheViewTook());
    }

    @Test
    void aWriteWithoutRevisionHeadersHandsTheViewNothing() throws IOException {
        controller.enqueue(new MockResponse().setHeader("X-SC-State-Epoch", EPOCH));

        send("POST", "/v0/servers/abc/stop");

        assertEquals(List.of(), writesTheViewTook());
    }

    @Test
    void aWriteWithRevisionHeadersThatAreNoUnsignedNumbersHandsTheViewNothing() throws IOException {
        controller.enqueue(new MockResponse()
                .setHeader("X-SC-State-Epoch", EPOCH)
                .setHeader("X-SC-State-Revision", "-1"));

        send("DELETE", "/v0/persistent-servers/abc");

        assertEquals(List.of(), writesTheViewTook());
    }

    @Test
    void aWriteThatThrowsHandsTheViewNothingAndTheCallerTheException() {
        controller.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST));

        assertThrows(IOException.class, () -> send("PATCH", "/v0/server-groups/lobby"));
        assertEquals(List.of(), writesTheViewTook());
    }

    @Test
    void aWriteWithAServerErrorHandsTheViewNothing() throws IOException {
        controller.enqueue(withRevisionHeaders(new MockResponse().setResponseCode(502)));

        send("PUT", "/v0/blueprints/abc");

        assertEquals(List.of(), writesTheViewTook());
    }

    @Test
    void aWriteWithAClientErrorAReadAndAPlayerWriteHandTheViewNothing() throws IOException {
        controller.enqueue(withRevisionHeaders(new MockResponse().setResponseCode(409)));
        controller.enqueue(withRevisionHeaders(new MockResponse()));
        controller.enqueue(withRevisionHeaders(new MockResponse()));

        send("PUT", "/v0/blueprints/abc");
        send("GET", "/v0/servers");
        send("POST", "/v0/players/abc/connect");

        assertEquals(List.of(), writesTheViewTook());
    }

    @Test
    void writesToServersGroupsPersistentServersAndBlueprintsAreNetworkStateWrites() {
        assertTrue(writesNetworkState("POST", "/v0/servers/abc/properties"));
        assertTrue(writesNetworkState("PATCH", "/v0/server-groups"));
        assertTrue(writesNetworkState("POST", "/v0/server-groups/convert-to-persistent"));
        assertTrue(writesNetworkState("DELETE", "/v0/persistent-servers/abc"));
        assertTrue(writesNetworkState("PUT", "/v0/blueprints/abc"));
    }

    @Test
    void readsAndWritesToOtherRoutesAreNoNetworkStateWrites() {
        assertFalse(writesNetworkState("GET", "/v0/servers"));
        assertFalse(writesNetworkState("POST", "/v0/players/abc"));
        assertFalse(writesNetworkState("POST", "/v0"));
        assertFalse(writesNetworkState("POST", "/servers/abc"));
    }

    private void send(String method, String path) throws IOException {
        RequestBody body = method.equals("GET") ? null : RequestBody.create("{}", JSON);
        Request request = new Request.Builder()
                .url(controller.url(path))
                .method(method, body)
                .build();
        try (Response response = client.newCall(request).execute()) {
            response.body().string();
        }
    }

    private List<ViewInput.WriteReturned> writesTheViewTook() {
        return view.received(ViewInput.WriteReturned.class);
    }

    private boolean writesNetworkState(String method, String path) {
        RequestBody body = method.equals("GET") ? null : RequestBody.create("{}", JSON);
        Request request = new Request.Builder()
                .url(controller.url(path))
                .method(method, body)
                .build();
        return StateRevisionInterceptor.writesNetworkState(request);
    }

    private static MockResponse withRevisionHeaders(MockResponse response) {
        return response
                .setHeader("X-SC-State-Epoch", EPOCH)
                .setHeader("X-SC-State-Revision", REVISION);
    }
}
