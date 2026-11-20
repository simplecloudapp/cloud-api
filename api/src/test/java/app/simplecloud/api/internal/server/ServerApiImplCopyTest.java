package app.simplecloud.api.internal.server;

import app.simplecloud.api.CloudApiOptions;
import app.simplecloud.api.internal.cache.NoOpQueryCache;
import app.simplecloud.api.internal.nats.FakeNatsConnection;
import app.simplecloud.api.internal.networkcopy.NetworkCopySynchronizer;
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
 * The synchronizer gets its messages through a fake NATS connection and loads the copy from a local
 * controller, which also answers the HTTP reads and writes of the API.
 */
class ServerApiImplCopyTest {

    private static final String NETWORK_ID = "0d000000-0000-4000-8000-000000000001";
    private static final String LOBBY_GROUP_ID = "0a000000-0000-4000-8000-000000000301";
    private static final String FIRST_SERVER_ID = "00000000-0000-4000-8000-000000000201";
    private static final String SECOND_SERVER_ID = "00000000-0000-4000-8000-000000000202";
    private static final String SNAPSHOT_LOAD = "GET /v0/network-state";
    private static final String SERVER_LIST_READ = "GET /v0/servers";
    private static final String SERVER_LIST_JSON = "{\"servers\":[{\"server_id\":\"" + FIRST_SERVER_ID + "\","
            + "\"server_group_id\":\"" + LOBBY_GROUP_ID + "\",\"numerical_id\":1,\"state\":\"AVAILABLE\"}]}";
    private static final String WRITE_RESPONSE_JSON = "{\"server\":{\"server_id\":\"" + FIRST_SERVER_ID + "\",\"state\":\"AVAILABLE\"},"
            + "\"properties\":{\"motd\":\"hello\"}}";

    private final List<String> controllerRequests = new CopyOnWriteArrayList<>();
    private volatile boolean sendsRevisionHeaders = true;
    private MockWebServer controllerHttp;
    private FakeNatsConnection natsConnection;
    private NetworkCopySynchronizer synchronizer;
    private ServerApiImpl api;

    @BeforeEach
    void startControllerAndSynchronizer() throws IOException {
        controllerHttp = new MockWebServer();
        controllerHttp.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                return respondTo(request);
            }
        });
        controllerHttp.start();
        natsConnection = FakeNatsConnection.withoutRequests();
        synchronizer = new NetworkCopySynchronizer(new OkHttpClient(), getControllerUrl(), NETWORK_ID, "network-password");
        synchronizer.start(natsConnection.connection());
        api = new ServerApiImpl(buildOptions(), new NoOpQueryCache(), ApiClients.create(buildOptions()), Runnable::run, synchronizer);
    }

    @AfterEach
    void stopController() throws IOException {
        controllerHttp.close();
    }

    @Test
    void findServerByIdAnswersFromTheCopy() throws InterruptedException {
        receive("pulse", buildPulse());

        Optional<Server> server = api.findServerById(SECOND_SERVER_ID).join();

        assertEquals(SECOND_SERVER_ID, server.orElseThrow().getServerId());
        assertEquals(List.of(SNAPSHOT_LOAD), controllerRequests);
    }

    @Test
    void findServerByNumericalIdAnswersFromTheCopy() throws InterruptedException {
        receive("pulse", buildPulse());

        Optional<Server> server = api.findServerByNumericalId("lobby", 2).join();

        assertEquals(SECOND_SERVER_ID, server.orElseThrow().getServerId());
        assertEquals(List.of(SNAPSHOT_LOAD), controllerRequests);
    }

    @Test
    void getServersByGroupAnswersFromTheCopy() throws InterruptedException {
        receive("pulse", buildPulse());

        List<Server> servers = api.getServersByGroup("lobby").join();

        assertEquals(List.of(FIRST_SERVER_ID, SECOND_SERVER_ID), listServerIds(servers));
        assertEquals(List.of(SNAPSHOT_LOAD), controllerRequests);
    }

    @Test
    void getAllServersAnswersFromTheCopy() throws InterruptedException {
        receive("pulse", buildPulse());
        ServerQuery query = ServerQuery.create().filterByNumericalId(2);

        List<Server> servers = api.getAllServers(query).join();

        assertEquals(List.of(SECOND_SERVER_ID), listServerIds(servers));
        assertEquals(List.of(SNAPSHOT_LOAD), controllerRequests);
    }

    @Test
    void withoutACopyEachReadAsksTheController() {
        ServerApiImpl apiWithoutCopy = new ServerApiImpl(
                buildOptions(),
                new NoOpQueryCache(),
                ApiClients.create(buildOptions()),
                Runnable::run,
                NetworkCopySynchronizer.createNeverStarted()
        );

        Optional<Server> serverFoundById = apiWithoutCopy.findServerById(FIRST_SERVER_ID).join();
        Optional<Server> serverFoundByNumericalId = apiWithoutCopy.findServerByNumericalId("lobby", 1).join();
        List<Server> serversOfGroup = apiWithoutCopy.getServersByGroup("lobby").join();
        List<Server> allServers = apiWithoutCopy.getAllServers(null).join();

        assertEquals(FIRST_SERVER_ID, serverFoundById.orElseThrow().getServerId());
        assertEquals(FIRST_SERVER_ID, serverFoundByNumericalId.orElseThrow().getServerId());
        assertEquals(List.of(FIRST_SERVER_ID), listServerIds(serversOfGroup));
        assertEquals(List.of(FIRST_SERVER_ID), listServerIds(allServers));
        assertEquals(List.of(SERVER_LIST_READ, SERVER_LIST_READ, SERVER_LIST_READ, SERVER_LIST_READ), controllerRequests);
    }

    @Test
    void stopServerReturnsOnceABatchBringsItsRevision() throws InterruptedException {
        receive("pulse", buildPulse());
        receiveLater(buildBatchToRevision6());

        api.stopServer(FIRST_SERVER_ID).join();

        assertEquals(6, synchronizer.currentContents().revision());
    }

    @Test
    void updateServerReturnsOnceABatchBringsItsRevision() throws InterruptedException {
        receive("pulse", buildPulse());
        receiveLater(buildBatchToRevision6());

        api.updateServer(FIRST_SERVER_ID, UpdateServerRequest.builder().maxPlayers(30).build()).join();

        assertEquals(6, synchronizer.currentContents().revision());
    }

    @Test
    void updateServerPropertiesReturnsOnceABatchBringsItsRevision() throws InterruptedException {
        receive("pulse", buildPulse());
        receiveLater(buildBatchToRevision6());

        api.updateServerProperties(FIRST_SERVER_ID, Map.of("motd", "hello")).join();

        assertEquals(6, synchronizer.currentContents().revision());
    }

    @Test
    void deleteServerPropertiesReturnsOnceABatchBringsItsRevision() throws InterruptedException {
        receive("pulse", buildPulse());
        receiveLater(buildBatchToRevision6());

        api.deleteServerProperties(FIRST_SERVER_ID, List.of("motd")).join();

        assertEquals(6, synchronizer.currentContents().revision());
    }

    @Test
    void writesWithoutRevisionHeadersDoNotWait() throws InterruptedException {
        receive("pulse", buildPulse());
        sendsRevisionHeaders = false;

        assertTimeout(Duration.ofSeconds(2), this::runEachWrite);

        assertEquals(5, synchronizer.currentContents().revision());
    }

    private void runEachWrite() {
        api.stopServer(FIRST_SERVER_ID).join();
        api.updateServer(FIRST_SERVER_ID, UpdateServerRequest.builder().maxPlayers(30).build()).join();
        api.updateServerProperties(FIRST_SERVER_ID, Map.of("motd", "hello")).join();
        api.deleteServerProperties(FIRST_SERVER_ID, List.of("motd")).join();
    }

    private MockResponse respondTo(RecordedRequest request) {
        String route = request.getMethod() + " " + request.getRequestUrl().encodedPath();
        controllerRequests.add(route);
        if (route.equals(SNAPSHOT_LOAD)) {
            Buffer snapshotBody = new Buffer().write(buildSnapshot().toByteArray());
            return new MockResponse().setBody(snapshotBody);
        }
        if (route.equals(SERVER_LIST_READ)) {
            return buildJsonResponse(SERVER_LIST_JSON);
        }
        MockResponse writeResponse = buildJsonResponse(WRITE_RESPONSE_JSON);
        if (sendsRevisionHeaders) {
            // The case the controller's Go server sends.
            writeResponse.setHeader("X-Sc-State-Epoch", "1");
            writeResponse.setHeader("X-Sc-State-Revision", "6");
        }
        return writeResponse;
    }

    private void receive(String kind, MessageLite message) throws InterruptedException {
        String subject = NETWORK_ID + ".state.v1." + kind;
        NatsMessage natsMessage = NatsMessage.builder().subject(subject).data(message.toByteArray()).build();
        natsConnection.deliver(subject, natsMessage);
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

    private static MockResponse buildJsonResponse(String body) {
        return new MockResponse().setHeader("Content-Type", "application/json").setBody(body);
    }

    private static StatePulse buildPulse() {
        return StatePulse.newBuilder().setEpoch(1).setRevision(5).setViewsMayAnswer(true).build();
    }

    private static StateBatch buildBatchToRevision6() {
        return StateBatch.newBuilder().setEpoch(1).setBaseRevision(5).setRevision(6).build();
    }

    private static NetworkState buildSnapshot() {
        return NetworkState.newBuilder()
                .setEpoch(1)
                .setRevision(5)
                .addServerGroups(buildLobbyStateServerGroup())
                .addServers(buildLobbyStateServer(FIRST_SERVER_ID, 1))
                .addServers(buildLobbyStateServer(SECOND_SERVER_ID, 2))
                .build();
    }

    private static StateServerGroup buildLobbyStateServerGroup() {
        String summaryJson = "{\"server_group_id\":\"" + LOBBY_GROUP_ID + "\",\"name\":\"lobby\",\"created_at\":\"2026-11-17T09:00:00Z\"}";
        String infoJson = "{\"id\":\"" + LOBBY_GROUP_ID + "\",\"name\":\"lobby\"}";
        return StateServerGroup.newBuilder()
                .setId(toUuidBytes(LOBBY_GROUP_ID))
                .setSummaryJson(ByteString.copyFromUtf8(summaryJson))
                .setInfoJson(ByteString.copyFromUtf8(infoJson))
                .build();
    }

    private static StateServer buildLobbyStateServer(String id, int numericalId) {
        return StateServer.newBuilder()
                .setId(toUuidBytes(id))
                .setServerGroupId(toUuidBytes(LOBBY_GROUP_ID))
                .setNumericalId(numericalId)
                .setState("AVAILABLE")
                .setPropertiesJson(ByteString.copyFromUtf8("{}"))
                .build();
    }

    private static ByteString toUuidBytes(String id) {
        UUID uuid = UUID.fromString(id);
        ByteBuffer bytes = ByteBuffer.allocate(16);
        bytes.putLong(uuid.getMostSignificantBits());
        bytes.putLong(uuid.getLeastSignificantBits());
        return ByteString.copyFrom(bytes.array());
    }

    private static List<String> listServerIds(List<Server> servers) {
        List<String> ids = new ArrayList<>();
        for (Server server : servers) {
            ids.add(server.getServerId());
        }
        return ids;
    }

    private String getControllerUrl() {
        return "http://" + controllerHttp.getHostName() + ":" + controllerHttp.getPort();
    }

    private CloudApiOptions buildOptions() {
        return CloudApiOptions.builder()
                .controllerUrl(getControllerUrl())
                .networkId(NETWORK_ID)
                .networkSecret("network-password")
                .disableCache()
                .build();
    }
}
