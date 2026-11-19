package app.simplecloud.api.internal.server;

import app.simplecloud.api.CloudApiOptions;
import app.simplecloud.api.internal.cache.NoOpQueryCache;
import app.simplecloud.api.internal.nats.FakeNatsConnection;
import app.simplecloud.api.internal.networkcopy.NetworkCopy;
import app.simplecloud.api.internal.web.ApiClients;
import app.simplecloud.api.server.Server;
import app.simplecloud.api.server.ServerQuery;
import app.simplecloud.api.server.UpdateServerRequest;
import build.buf.gen.simplecloud.controller.v2.NetworkState;
import build.buf.gen.simplecloud.controller.v2.StateBatch;
import build.buf.gen.simplecloud.controller.v2.StatePulse;
import build.buf.gen.simplecloud.controller.v2.StateServer;
import build.buf.gen.simplecloud.controller.v2.StateServerGroup;
import com.google.protobuf.ByteString;
import com.google.protobuf.MessageLite;
import io.nats.client.impl.NatsMessage;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okio.Buffer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeout;

/**
 * The copy gets its messages through a fake NATS connection and loads from a local controller,
 * which also answers the HTTP reads and writes of the API.
 */
class ServerApiImplCopyTest {

    private static final String NETWORK_ID = "0d000000-0000-4000-8000-000000000001";
    private static final String LOBBY_GROUP_ID = "0a000000-0000-4000-8000-000000000301";
    private static final String FIRST_SERVER_ID = "00000000-0000-4000-8000-000000000201";
    private static final String SECOND_SERVER_ID = "00000000-0000-4000-8000-000000000202";
    private static final String NETWORK_STATE_LOAD = "GET /v0/network-state";
    private static final String SERVER_LIST_READ = "GET /v0/servers";
    private static final String SERVER_LIST_JSON = "{\"servers\":[{\"server_id\":\"" + FIRST_SERVER_ID + "\","
            + "\"server_group_id\":\"" + LOBBY_GROUP_ID + "\",\"numerical_id\":1,\"state\":\"AVAILABLE\"}]}";
    private static final String WRITE_ANSWER_JSON = "{\"server\":{\"server_id\":\"" + FIRST_SERVER_ID + "\",\"state\":\"AVAILABLE\"},"
            + "\"properties\":{\"motd\":\"hello\"}}";

    private final List<String> controllerRequests = new CopyOnWriteArrayList<>();
    private volatile boolean sendsRevisionHeaders = true;
    private MockWebServer controller;
    private FakeNatsConnection nats;
    private NetworkCopy copy;
    private ServerApiImpl api;

    @BeforeEach
    void startControllerAndCopy() throws IOException {
        controller = new MockWebServer();
        controller.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                return answer(request);
            }
        });
        controller.start();
        nats = FakeNatsConnection.withoutRequests();
        copy = new NetworkCopy(new OkHttpClient(), controllerUrl(), NETWORK_ID, "network-password");
        copy.start(nats.connection());
        api = new ServerApiImpl(options(), new NoOpQueryCache(), ApiClients.create(options()), Runnable::run, copy);
    }

    @AfterEach
    void stopController() throws IOException {
        controller.close();
    }

    @Test
    void findServerByIdAnswersFromTheCopy() throws InterruptedException {
        receive("pulse", pulse());

        Optional<Server> server = api.findServerById(SECOND_SERVER_ID).join();

        assertEquals(SECOND_SERVER_ID, server.orElseThrow().getServerId());
        assertEquals(List.of(NETWORK_STATE_LOAD), controllerRequests);
    }

    @Test
    void findServerByNumericalIdAnswersFromTheCopy() throws InterruptedException {
        receive("pulse", pulse());

        Optional<Server> server = api.findServerByNumericalId("lobby", 2).join();

        assertEquals(SECOND_SERVER_ID, server.orElseThrow().getServerId());
        assertEquals(List.of(NETWORK_STATE_LOAD), controllerRequests);
    }

    @Test
    void getServersByGroupAnswersFromTheCopy() throws InterruptedException {
        receive("pulse", pulse());

        List<Server> servers = api.getServersByGroup("lobby").join();

        assertEquals(List.of(FIRST_SERVER_ID, SECOND_SERVER_ID), serverIds(servers));
        assertEquals(List.of(NETWORK_STATE_LOAD), controllerRequests);
    }

    @Test
    void getAllServersAnswersFromTheCopy() throws InterruptedException {
        receive("pulse", pulse());
        ServerQuery query = ServerQuery.create().filterByNumericalId(2);

        List<Server> servers = api.getAllServers(query).join();

        assertEquals(List.of(SECOND_SERVER_ID), serverIds(servers));
        assertEquals(List.of(NETWORK_STATE_LOAD), controllerRequests);
    }

    @Test
    void withoutACopyEachReadAsksTheController() {
        ServerApiImpl apiWithoutCopy = new ServerApiImpl(
                options(),
                new NoOpQueryCache(),
                ApiClients.create(options()),
                Runnable::run,
                NetworkCopy.neverStarted()
        );

        Optional<Server> byId = apiWithoutCopy.findServerById(FIRST_SERVER_ID).join();
        Optional<Server> byNumericalId = apiWithoutCopy.findServerByNumericalId("lobby", 1).join();
        List<Server> ofGroup = apiWithoutCopy.getServersByGroup("lobby").join();
        List<Server> all = apiWithoutCopy.getAllServers(null).join();

        assertEquals(FIRST_SERVER_ID, byId.orElseThrow().getServerId());
        assertEquals(FIRST_SERVER_ID, byNumericalId.orElseThrow().getServerId());
        assertEquals(List.of(FIRST_SERVER_ID), serverIds(ofGroup));
        assertEquals(List.of(FIRST_SERVER_ID), serverIds(all));
        assertEquals(List.of(SERVER_LIST_READ, SERVER_LIST_READ, SERVER_LIST_READ, SERVER_LIST_READ), controllerRequests);
    }

    @Test
    void stopServerReturnsOnceABatchBringsItsRevision() throws InterruptedException {
        receive("pulse", pulse());
        receiveLater(batchToRevision6());

        api.stopServer(FIRST_SERVER_ID).join();

        assertEquals(6, copy.contents().revision());
    }

    @Test
    void updateServerReturnsOnceABatchBringsItsRevision() throws InterruptedException {
        receive("pulse", pulse());
        receiveLater(batchToRevision6());

        api.updateServer(FIRST_SERVER_ID, UpdateServerRequest.builder().maxPlayers(30).build()).join();

        assertEquals(6, copy.contents().revision());
    }

    @Test
    void updateServerPropertiesReturnsOnceABatchBringsItsRevision() throws InterruptedException {
        receive("pulse", pulse());
        receiveLater(batchToRevision6());

        api.updateServerProperties(FIRST_SERVER_ID, Map.of("motd", "hello")).join();

        assertEquals(6, copy.contents().revision());
    }

    @Test
    void deleteServerPropertiesReturnsOnceABatchBringsItsRevision() throws InterruptedException {
        receive("pulse", pulse());
        receiveLater(batchToRevision6());

        api.deleteServerProperties(FIRST_SERVER_ID, List.of("motd")).join();

        assertEquals(6, copy.contents().revision());
    }

    @Test
    void writesWithoutRevisionHeadersDoNotWait() throws InterruptedException {
        receive("pulse", pulse());
        sendsRevisionHeaders = false;

        assertTimeout(Duration.ofSeconds(2), this::runEachWrite);

        assertEquals(5, copy.contents().revision());
    }

    private void runEachWrite() {
        api.stopServer(FIRST_SERVER_ID).join();
        api.updateServer(FIRST_SERVER_ID, UpdateServerRequest.builder().maxPlayers(30).build()).join();
        api.updateServerProperties(FIRST_SERVER_ID, Map.of("motd", "hello")).join();
        api.deleteServerProperties(FIRST_SERVER_ID, List.of("motd")).join();
    }

    private MockResponse answer(RecordedRequest request) {
        String route = request.getMethod() + " " + request.getRequestUrl().encodedPath();
        controllerRequests.add(route);
        if (route.equals(NETWORK_STATE_LOAD)) {
            Buffer state = new Buffer().write(networkState().toByteArray());
            return new MockResponse().setBody(state);
        }
        if (route.equals(SERVER_LIST_READ)) {
            return json(SERVER_LIST_JSON);
        }
        MockResponse written = json(WRITE_ANSWER_JSON);
        if (sendsRevisionHeaders) {
            // The case the controller's Go server sends.
            written.setHeader("X-Sc-State-Epoch", "1");
            written.setHeader("X-Sc-State-Revision", "6");
        }
        return written;
    }

    private void receive(String kind, MessageLite message) throws InterruptedException {
        String subject = NETWORK_ID + ".state.v1." + kind;
        NatsMessage natsMessage = NatsMessage.builder().subject(subject).data(message.toByteArray()).build();
        nats.deliver(subject, natsMessage);
    }

    private void receiveLater(StateBatch batch) {
        Thread.ofVirtual().start(() -> receiveAfter100Milliseconds(batch));
    }

    private void receiveAfter100Milliseconds(StateBatch batch) {
        try {
            Thread.sleep(100);
            receive("batch", batch);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static MockResponse json(String body) {
        return new MockResponse().setHeader("Content-Type", "application/json").setBody(body);
    }

    private static StatePulse pulse() {
        return StatePulse.newBuilder().setEpoch(1).setRevision(5).setViewsMayAnswer(true).build();
    }

    private static StateBatch batchToRevision6() {
        return StateBatch.newBuilder().setEpoch(1).setBaseRevision(5).setRevision(6).build();
    }

    private static NetworkState networkState() {
        return NetworkState.newBuilder()
                .setEpoch(1)
                .setRevision(5)
                .addServerGroups(lobbyGroup())
                .addServers(lobbyServer(FIRST_SERVER_ID, 1))
                .addServers(lobbyServer(SECOND_SERVER_ID, 2))
                .build();
    }

    private static StateServerGroup lobbyGroup() {
        String summary = "{\"server_group_id\":\"" + LOBBY_GROUP_ID + "\",\"name\":\"lobby\",\"created_at\":\"2026-11-17T09:00:00Z\"}";
        String info = "{\"id\":\"" + LOBBY_GROUP_ID + "\",\"name\":\"lobby\"}";
        return StateServerGroup.newBuilder()
                .setId(uuidBytes(LOBBY_GROUP_ID))
                .setSummaryJson(ByteString.copyFromUtf8(summary))
                .setInfoJson(ByteString.copyFromUtf8(info))
                .build();
    }

    private static StateServer lobbyServer(String id, int numericalId) {
        return StateServer.newBuilder()
                .setId(uuidBytes(id))
                .setServerGroupId(uuidBytes(LOBBY_GROUP_ID))
                .setNumericalId(numericalId)
                .setState("AVAILABLE")
                .setPropertiesJson(ByteString.copyFromUtf8("{}"))
                .build();
    }

    private static ByteString uuidBytes(String id) {
        UUID uuid = UUID.fromString(id);
        ByteBuffer bytes = ByteBuffer.allocate(16);
        bytes.putLong(uuid.getMostSignificantBits());
        bytes.putLong(uuid.getLeastSignificantBits());
        return ByteString.copyFrom(bytes.array());
    }

    private static List<String> serverIds(List<Server> servers) {
        List<String> ids = new ArrayList<>();
        for (Server server : servers) {
            ids.add(server.getServerId());
        }
        return ids;
    }

    private String controllerUrl() {
        return "http://" + controller.getHostName() + ":" + controller.getPort();
    }

    private CloudApiOptions options() {
        return CloudApiOptions.builder()
                .controllerUrl(controllerUrl())
                .networkId(NETWORK_ID)
                .networkSecret("network-password")
                .disableCache()
                .build();
    }
}
