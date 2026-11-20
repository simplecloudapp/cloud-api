package app.simplecloud.api.internal.persistentserver;

import app.simplecloud.api.CloudApiOptions;
import app.simplecloud.api.group.GroupServerType;
import app.simplecloud.api.internal.cache.NoOpQueryCache;
import app.simplecloud.api.internal.nats.FakeNatsConnection;
import app.simplecloud.api.internal.networkcopy.NetworkCopySynchronizer;
import app.simplecloud.api.internal.web.ApiClients;
import app.simplecloud.api.persistentserver.CreatePersistentServerRequest;
import app.simplecloud.api.persistentserver.PersistentServer;
import app.simplecloud.api.persistentserver.PersistentServerQuery;
import app.simplecloud.api.persistentserver.UpdatePersistentServerRequest;
import build.buf.gen.simplecloud.controller.v2.NetworkState;
import build.buf.gen.simplecloud.controller.v2.StateBatch;
import build.buf.gen.simplecloud.controller.v2.StatePersistentServer;
import build.buf.gen.simplecloud.controller.v2.StatePulse;
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
import org.junit.jupiter.api.Timeout;

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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The synchronizer gets its messages through a fake NATS connection and loads the copy from a local
 * controller, which also answers the HTTP reads and writes of the API.
 */
class PersistentServerApiImplCopyTest {

    private static final String NETWORK_ID = "0d000000-0000-4000-8000-000000000001";
    private static final String SURVIVAL_ID = "0c000000-0000-4000-8000-000000000401";
    private static final String CREATIVE_ID = "0c000000-0000-4000-8000-000000000402";
    private static final String SNAPSHOT_LOAD = "GET /v0/network-state";
    private static final String PERSISTENT_SERVER_LIST_READ = "GET /v0/persistent-servers";
    private static final String PERSISTENT_SERVER_LIST_JSON = "{\"persistent_servers\":[{\"persistent_server_id\":\"" + SURVIVAL_ID + "\","
            + "\"name\":\"survival\",\"created_at\":\"2026-11-17T09:00:00Z\"}]}";
    private static final String WRITE_RESPONSE_JSON = "{\"persistent_server_id\":\"" + SURVIVAL_ID + "\",\"name\":\"survival\","
            + "\"type\":\"SERVER\",\"max_players\":30,\"properties\":{\"motd\":\"hello\"}}";

    private final List<String> controllerRequests = new CopyOnWriteArrayList<>();
    private volatile boolean sendsRevisionHeaders = true;
    private MockWebServer controllerHttp;
    private FakeNatsConnection natsConnection;
    private NetworkCopySynchronizer synchronizer;
    private PersistentServerApiImpl api;

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
        api = new PersistentServerApiImpl(buildOptions(), new NoOpQueryCache(), ApiClients.create(buildOptions()), Runnable::run, synchronizer);
    }

    @AfterEach
    void stopController() throws IOException {
        controllerHttp.close();
    }

    @Test
    void findPersistentServerByIdAnswersFromTheCopy() throws InterruptedException {
        receive("pulse", buildPulse());

        Optional<PersistentServer> persistentServer = api.findPersistentServerById(CREATIVE_ID).join();

        assertEquals(CREATIVE_ID, persistentServer.orElseThrow().getPersistentServerId());
        assertEquals(List.of(SNAPSHOT_LOAD), controllerRequests);
    }

    @Test
    void findPersistentServerByNameAnswersFromTheCopy() throws InterruptedException {
        receive("pulse", buildPulse());

        Optional<PersistentServer> persistentServer = api.findPersistentServerByName("survival").join();

        assertEquals(SURVIVAL_ID, persistentServer.orElseThrow().getPersistentServerId());
        assertEquals(List.of(SNAPSHOT_LOAD), controllerRequests);
    }

    @Test
    void getAllPersistentServersAnswersFromTheCopy() throws InterruptedException {
        receive("pulse", buildPulse());
        PersistentServerQuery newestOnlyQuery = PersistentServerQuery.create().limit(1);

        List<PersistentServer> persistentServers = api.getAllPersistentServers(newestOnlyQuery).join();

        assertEquals(List.of(CREATIVE_ID), listPersistentServerIds(persistentServers));
        assertEquals(List.of(SNAPSHOT_LOAD), controllerRequests);
    }

    @Test
    void withoutACopyEachReadAsksTheController() {
        PersistentServerApiImpl apiWithoutCopy = new PersistentServerApiImpl(
                buildOptions(),
                new NoOpQueryCache(),
                ApiClients.create(buildOptions()),
                Runnable::run,
                NetworkCopySynchronizer.createNeverStarted()
        );

        Optional<PersistentServer> persistentServerFoundById = apiWithoutCopy.findPersistentServerById(SURVIVAL_ID).join();
        Optional<PersistentServer> persistentServerFoundByName = apiWithoutCopy.findPersistentServerByName("survival").join();
        List<PersistentServer> allPersistentServers = apiWithoutCopy.getAllPersistentServers(null).join();

        assertEquals(SURVIVAL_ID, persistentServerFoundById.orElseThrow().getPersistentServerId());
        assertEquals(SURVIVAL_ID, persistentServerFoundByName.orElseThrow().getPersistentServerId());
        assertEquals(List.of(SURVIVAL_ID), listPersistentServerIds(allPersistentServers));
        List<String> expectedRequests = List.of(PERSISTENT_SERVER_LIST_READ, PERSISTENT_SERVER_LIST_READ, PERSISTENT_SERVER_LIST_READ);
        assertEquals(expectedRequests, controllerRequests);
    }

    @Test
    void createPersistentServerReturnsOnceABatchBringsItsRevision() throws InterruptedException {
        receive("pulse", buildPulse());
        receiveLater(buildBatchToRevision6());

        api.createPersistentServer(buildCreateRequest()).join();

        assertEquals(6, synchronizer.currentContents().revision());
    }

    @Test
    void updatePersistentServerReturnsOnceABatchBringsItsRevision() throws InterruptedException {
        receive("pulse", buildPulse());
        receiveLater(buildBatchToRevision6());

        api.updatePersistentServer(SURVIVAL_ID, buildUpdateRequest()).join();

        assertEquals(6, synchronizer.currentContents().revision());
    }

    @Test
    @Timeout(10)
    void updatePersistentServerWhoseBatchNeverComesReturnsTheNewValuesAfterThreeSeconds() throws InterruptedException {
        receive("pulse", buildPulse());
        Duration shortestWait = Duration.ofSeconds(3);
        Duration longestWait = Duration.ofSeconds(5);
        long startNanos = System.nanoTime();

        PersistentServer updatedPersistentServer = api.updatePersistentServer(SURVIVAL_ID, buildUpdateRequest()).join();

        Duration waited = Duration.ofNanos(System.nanoTime() - startNanos);
        assertEquals(Integer.valueOf(30), updatedPersistentServer.getMaxPlayers());
        assertTrue(waited.compareTo(shortestWait) >= 0, "waited " + waited);
        assertTrue(waited.compareTo(longestWait) <= 0, "waited " + waited);
    }

    @Test
    void deletePersistentServerReturnsOnceABatchBringsItsRevision() throws InterruptedException {
        receive("pulse", buildPulse());
        receiveLater(buildBatchToRevision6());

        api.deletePersistentServer(SURVIVAL_ID).join();

        assertEquals(6, synchronizer.currentContents().revision());
    }

    @Test
    void updatePersistentServerPropertiesReturnsOnceABatchBringsItsRevision() throws InterruptedException {
        receive("pulse", buildPulse());
        receiveLater(buildBatchToRevision6());

        api.updatePersistentServerProperties(SURVIVAL_ID, Map.of("motd", "hello")).join();

        assertEquals(6, synchronizer.currentContents().revision());
    }

    @Test
    void deletePersistentServerPropertiesReturnsOnceABatchBringsItsRevision() throws InterruptedException {
        receive("pulse", buildPulse());
        receiveLater(buildBatchToRevision6());

        api.deletePersistentServerProperties(SURVIVAL_ID, List.of("motd")).join();

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
        api.createPersistentServer(buildCreateRequest()).join();
        api.updatePersistentServer(SURVIVAL_ID, buildUpdateRequest()).join();
        api.deletePersistentServer(SURVIVAL_ID).join();
        api.updatePersistentServerProperties(SURVIVAL_ID, Map.of("motd", "hello")).join();
        api.deletePersistentServerProperties(SURVIVAL_ID, List.of("motd")).join();
    }

    private MockResponse respondTo(RecordedRequest request) {
        String route = request.getMethod() + " " + request.getRequestUrl().encodedPath();
        controllerRequests.add(route);
        if (route.equals(SNAPSHOT_LOAD)) {
            Buffer snapshotBody = new Buffer().write(buildSnapshot().toByteArray());
            return new MockResponse().setBody(snapshotBody);
        }
        if (route.equals(PERSISTENT_SERVER_LIST_READ)) {
            return buildJsonResponse(PERSISTENT_SERVER_LIST_JSON);
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

    private static CreatePersistentServerRequest buildCreateRequest() {
        return CreatePersistentServerRequest.builder()
                .name("survival")
                .type(GroupServerType.SERVER)
                .build();
    }

    private static UpdatePersistentServerRequest buildUpdateRequest() {
        return UpdatePersistentServerRequest.builder()
                .maxPlayers(30)
                .build();
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
                .addPersistentServers(buildStatePersistentServer(SURVIVAL_ID, "survival", "2026-11-17T09:00:00Z"))
                .addPersistentServers(buildStatePersistentServer(CREATIVE_ID, "creative", "2026-11-17T10:00:00Z"))
                .build();
    }

    private static StatePersistentServer buildStatePersistentServer(String id, String name, String createdAt) {
        String summaryJson = "{\"persistent_server_id\":\"" + id + "\",\"name\":\"" + name + "\",\"created_at\":\"" + createdAt + "\"}";
        String infoJson = "{\"id\":\"" + id + "\",\"name\":\"" + name + "\"}";
        return StatePersistentServer.newBuilder()
                .setId(toUuidBytes(id))
                .setSummaryJson(ByteString.copyFromUtf8(summaryJson))
                .setInfoJson(ByteString.copyFromUtf8(infoJson))
                .build();
    }

    private static ByteString toUuidBytes(String id) {
        UUID uuid = UUID.fromString(id);
        ByteBuffer bytes = ByteBuffer.allocate(16);
        bytes.putLong(uuid.getMostSignificantBits());
        bytes.putLong(uuid.getLeastSignificantBits());
        return ByteString.copyFrom(bytes.array());
    }

    private static List<String> listPersistentServerIds(List<PersistentServer> persistentServers) {
        List<String> ids = new ArrayList<>();
        for (PersistentServer persistentServer : persistentServers) {
            ids.add(persistentServer.getPersistentServerId());
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
