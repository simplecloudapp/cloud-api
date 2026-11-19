package app.simplecloud.api.internal.persistentserver;

import app.simplecloud.api.CloudApiOptions;
import app.simplecloud.api.group.GroupServerType;
import app.simplecloud.api.internal.cache.NoOpQueryCache;
import app.simplecloud.api.internal.nats.FakeNatsConnection;
import app.simplecloud.api.internal.networkcopy.NetworkCopy;
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
 * The copy gets its messages through a fake NATS connection and loads from a local controller,
 * which also answers the HTTP reads and writes of the API.
 */
class PersistentServerApiImplCopyTest {

    private static final String NETWORK_ID = "0d000000-0000-4000-8000-000000000001";
    private static final String SURVIVAL = "0c000000-0000-4000-8000-000000000401";
    private static final String CREATIVE = "0c000000-0000-4000-8000-000000000402";
    private static final String NETWORK_STATE_LOAD = "GET /v0/network-state";
    private static final String PERSISTENT_SERVER_LIST_READ = "GET /v0/persistent-servers";
    private static final String PERSISTENT_SERVER_LIST = "{\"persistent_servers\":[{\"persistent_server_id\":\"" + SURVIVAL + "\","
            + "\"name\":\"survival\",\"created_at\":\"2026-11-17T09:00:00Z\"}]}";
    private static final String WRITE_ANSWER = "{\"persistent_server_id\":\"" + SURVIVAL + "\",\"name\":\"survival\","
            + "\"type\":\"SERVER\",\"max_players\":30,\"properties\":{\"motd\":\"hello\"}}";

    private final List<String> controllerRequests = new CopyOnWriteArrayList<>();
    private volatile boolean sendsRevisionHeaders = true;
    private MockWebServer controller;
    private FakeNatsConnection nats;
    private NetworkCopy copy;
    private PersistentServerApiImpl api;

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
        api = new PersistentServerApiImpl(options(), new NoOpQueryCache(), ApiClients.create(options()), Runnable::run, copy);
    }

    @AfterEach
    void stopController() throws IOException {
        controller.close();
    }

    @Test
    void findPersistentServerByIdAnswersFromTheCopy() throws InterruptedException {
        receive("pulse", pulse());

        Optional<PersistentServer> persistentServer = api.findPersistentServerById(CREATIVE).join();

        assertEquals(CREATIVE, persistentServer.orElseThrow().getPersistentServerId());
        assertEquals(List.of(NETWORK_STATE_LOAD), controllerRequests);
    }

    @Test
    void findPersistentServerByNameAnswersFromTheCopy() throws InterruptedException {
        receive("pulse", pulse());

        Optional<PersistentServer> persistentServer = api.findPersistentServerByName("survival").join();

        assertEquals(SURVIVAL, persistentServer.orElseThrow().getPersistentServerId());
        assertEquals(List.of(NETWORK_STATE_LOAD), controllerRequests);
    }

    @Test
    void getAllPersistentServersAnswersFromTheCopy() throws InterruptedException {
        receive("pulse", pulse());
        PersistentServerQuery newestOnly = PersistentServerQuery.create().limit(1);

        List<PersistentServer> persistentServers = api.getAllPersistentServers(newestOnly).join();

        assertEquals(List.of(CREATIVE), persistentServerIds(persistentServers));
        assertEquals(List.of(NETWORK_STATE_LOAD), controllerRequests);
    }

    @Test
    void withoutACopyEachReadAsksTheController() {
        PersistentServerApiImpl apiWithoutCopy = new PersistentServerApiImpl(
                options(),
                new NoOpQueryCache(),
                ApiClients.create(options()),
                Runnable::run,
                NetworkCopy.absent()
        );

        Optional<PersistentServer> byId = apiWithoutCopy.findPersistentServerById(SURVIVAL).join();
        Optional<PersistentServer> byName = apiWithoutCopy.findPersistentServerByName("survival").join();
        List<PersistentServer> all = apiWithoutCopy.getAllPersistentServers(null).join();

        assertEquals(SURVIVAL, byId.orElseThrow().getPersistentServerId());
        assertEquals(SURVIVAL, byName.orElseThrow().getPersistentServerId());
        assertEquals(List.of(SURVIVAL), persistentServerIds(all));
        List<String> expectedRequests = List.of(PERSISTENT_SERVER_LIST_READ, PERSISTENT_SERVER_LIST_READ, PERSISTENT_SERVER_LIST_READ);
        assertEquals(expectedRequests, controllerRequests);
    }

    @Test
    void createPersistentServerReturnsOnceABatchBringsItsRevision() throws InterruptedException {
        receive("pulse", pulse());
        receiveLater(batchToRevision6());

        api.createPersistentServer(createRequest()).join();

        assertEquals(6, copy.contents().revision());
    }

    @Test
    void updatePersistentServerReturnsOnceABatchBringsItsRevision() throws InterruptedException {
        receive("pulse", pulse());
        receiveLater(batchToRevision6());

        api.updatePersistentServer(SURVIVAL, updateRequest()).join();

        assertEquals(6, copy.contents().revision());
    }

    @Test
    @Timeout(10)
    void updatePersistentServerWhoseBatchNeverComesReturnsTheNewValuesAfterThreeSeconds() throws InterruptedException {
        receive("pulse", pulse());
        Duration shortestWait = Duration.ofSeconds(3);
        Duration longestWait = Duration.ofSeconds(5);
        long startNanos = System.nanoTime();

        PersistentServer updated = api.updatePersistentServer(SURVIVAL, updateRequest()).join();

        Duration waited = Duration.ofNanos(System.nanoTime() - startNanos);
        assertEquals(Integer.valueOf(30), updated.getMaxPlayers());
        assertTrue(waited.compareTo(shortestWait) >= 0, "waited " + waited);
        assertTrue(waited.compareTo(longestWait) <= 0, "waited " + waited);
    }

    @Test
    void deletePersistentServerReturnsOnceABatchBringsItsRevision() throws InterruptedException {
        receive("pulse", pulse());
        receiveLater(batchToRevision6());

        api.deletePersistentServer(SURVIVAL).join();

        assertEquals(6, copy.contents().revision());
    }

    @Test
    void updatePersistentServerPropertiesReturnsOnceABatchBringsItsRevision() throws InterruptedException {
        receive("pulse", pulse());
        receiveLater(batchToRevision6());

        api.updatePersistentServerProperties(SURVIVAL, Map.of("motd", "hello")).join();

        assertEquals(6, copy.contents().revision());
    }

    @Test
    void deletePersistentServerPropertiesReturnsOnceABatchBringsItsRevision() throws InterruptedException {
        receive("pulse", pulse());
        receiveLater(batchToRevision6());

        api.deletePersistentServerProperties(SURVIVAL, List.of("motd")).join();

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
        api.createPersistentServer(createRequest()).join();
        api.updatePersistentServer(SURVIVAL, updateRequest()).join();
        api.deletePersistentServer(SURVIVAL).join();
        api.updatePersistentServerProperties(SURVIVAL, Map.of("motd", "hello")).join();
        api.deletePersistentServerProperties(SURVIVAL, List.of("motd")).join();
    }

    private MockResponse answer(RecordedRequest request) {
        String route = request.getMethod() + " " + request.getRequestUrl().encodedPath();
        controllerRequests.add(route);
        if (route.equals(NETWORK_STATE_LOAD)) {
            Buffer state = new Buffer().write(networkState().toByteArray());
            return new MockResponse().setBody(state);
        }
        if (route.equals(PERSISTENT_SERVER_LIST_READ)) {
            return json(PERSISTENT_SERVER_LIST);
        }
        MockResponse written = json(WRITE_ANSWER);
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

    private static CreatePersistentServerRequest createRequest() {
        return CreatePersistentServerRequest.builder()
                .name("survival")
                .type(GroupServerType.SERVER)
                .build();
    }

    private static UpdatePersistentServerRequest updateRequest() {
        return UpdatePersistentServerRequest.builder()
                .maxPlayers(30)
                .build();
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
                .addPersistentServers(persistentServer(SURVIVAL, "survival", "2026-11-17T09:00:00Z"))
                .addPersistentServers(persistentServer(CREATIVE, "creative", "2026-11-17T10:00:00Z"))
                .build();
    }

    private static StatePersistentServer persistentServer(String id, String name, String createdAt) {
        String summary = "{\"persistent_server_id\":\"" + id + "\",\"name\":\"" + name + "\",\"created_at\":\"" + createdAt + "\"}";
        String info = "{\"id\":\"" + id + "\",\"name\":\"" + name + "\"}";
        return StatePersistentServer.newBuilder()
                .setId(uuidBytes(id))
                .setSummaryJson(ByteString.copyFromUtf8(summary))
                .setInfoJson(ByteString.copyFromUtf8(info))
                .build();
    }

    private static ByteString uuidBytes(String id) {
        UUID uuid = UUID.fromString(id);
        ByteBuffer bytes = ByteBuffer.allocate(16);
        bytes.putLong(uuid.getMostSignificantBits());
        bytes.putLong(uuid.getLeastSignificantBits());
        return ByteString.copyFrom(bytes.array());
    }

    private static List<String> persistentServerIds(List<PersistentServer> persistentServers) {
        List<String> ids = new ArrayList<>();
        for (PersistentServer persistentServer : persistentServers) {
            ids.add(persistentServer.getPersistentServerId());
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
