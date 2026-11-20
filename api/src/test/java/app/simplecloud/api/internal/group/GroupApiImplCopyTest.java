package app.simplecloud.api.internal.group;

import app.simplecloud.api.CloudApiException;
import app.simplecloud.api.CloudApiOptions;
import app.simplecloud.api.group.CreateGroupRequest;
import app.simplecloud.api.group.Group;
import app.simplecloud.api.group.GroupQuery;
import app.simplecloud.api.group.GroupServerType;
import app.simplecloud.api.group.UpdateGroupRequest;
import app.simplecloud.api.internal.cache.NoOpQueryCache;
import app.simplecloud.api.internal.nats.FakeNatsConnection;
import app.simplecloud.api.internal.networkcopy.NetworkCopySynchronizer;
import app.simplecloud.api.internal.web.ApiClients;
import build.buf.gen.simplecloud.controller.v2.NetworkState;
import build.buf.gen.simplecloud.controller.v2.StateBatch;
import build.buf.gen.simplecloud.controller.v2.StatePulse;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeout;

/**
 * The synchronizer gets its messages through a fake NATS connection and loads the copy from a local
 * controller, which also answers the HTTP reads and writes of the API.
 */
class GroupApiImplCopyTest {

    private static final String NETWORK_ID = "0d000000-0000-4000-8000-000000000001";
    private static final String LOBBY_GROUP_ID = "0a000000-0000-4000-8000-000000000301";
    private static final String GAME_GROUP_ID = "0a000000-0000-4000-8000-000000000302";
    private static final String SNAPSHOT_LOAD = "GET /v0/network-state";
    private static final String GROUP_LIST_READ = "GET /v0/server-groups";
    private static final String GROUP_LIST_JSON = "{\"server_groups\":[{\"server_group_id\":\"" + LOBBY_GROUP_ID + "\","
            + "\"name\":\"lobby\",\"created_at\":\"2026-11-17T09:00:00Z\"}]}";
    private static final String WRITE_RESPONSE_JSON = "{\"server_group_id\":\"" + LOBBY_GROUP_ID + "\",\"name\":\"lobby\","
            + "\"type\":\"SERVER\",\"properties\":{\"motd\":\"hello\"}}";

    private final List<String> controllerRequests = new CopyOnWriteArrayList<>();
    private volatile boolean sendsRevisionHeaders = true;
    private MockWebServer controllerHttp;
    private FakeNatsConnection natsConnection;
    private NetworkCopySynchronizer synchronizer;
    private GroupApiImpl api;

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
        api = new GroupApiImpl(buildOptions(), new NoOpQueryCache(), ApiClients.create(buildOptions()), Runnable::run, synchronizer);
    }

    @AfterEach
    void stopController() throws IOException {
        controllerHttp.close();
    }

    @Test
    void findGroupByIdAnswersFromTheCopy() throws InterruptedException {
        receive("pulse", buildPulse());

        Optional<Group> group = api.findGroupById(GAME_GROUP_ID).join();

        assertEquals(GAME_GROUP_ID, group.orElseThrow().getServerGroupId());
        assertEquals(List.of(SNAPSHOT_LOAD), controllerRequests);
    }

    @Test
    void findGroupByNameAnswersFromTheCopy() throws InterruptedException {
        receive("pulse", buildPulse());

        Optional<Group> group = api.findGroupByName("lobby").join();

        assertEquals(LOBBY_GROUP_ID, group.orElseThrow().getServerGroupId());
        assertEquals(List.of(SNAPSHOT_LOAD), controllerRequests);
    }

    @Test
    void getAllGroupsAnswersFromTheCopy() throws InterruptedException {
        receive("pulse", buildPulse());
        GroupQuery newestOnlyQuery = GroupQuery.create().limit(1);

        List<Group> groups = api.getAllGroups(newestOnlyQuery).join();

        assertEquals(List.of(GAME_GROUP_ID), listGroupIds(groups));
        assertEquals(List.of(SNAPSHOT_LOAD), controllerRequests);
    }

    @Test
    void getAllGroupsWithALimitBelowOneFailsItsFuture() throws InterruptedException {
        receive("pulse", buildPulse());
        GroupQuery zeroLimitQuery = GroupQuery.create().limit(0);

        CompletableFuture<List<Group>> groupsFuture = assertDoesNotThrow(() -> api.getAllGroups(zeroLimitQuery));

        CompletionException failure = assertThrows(CompletionException.class, groupsFuture::join);
        CloudApiException refusal = assertInstanceOf(CloudApiException.class, failure.getCause());
        assertEquals(CloudApiException.Reason.INVALID_REQUEST, refusal.getReason());
    }

    @Test
    void withoutACopyEachReadAsksTheController() {
        GroupApiImpl apiWithoutCopy = new GroupApiImpl(
                buildOptions(),
                new NoOpQueryCache(),
                ApiClients.create(buildOptions()),
                Runnable::run,
                NetworkCopySynchronizer.createNeverStarted()
        );

        Optional<Group> groupFoundById = apiWithoutCopy.findGroupById(LOBBY_GROUP_ID).join();
        Optional<Group> groupFoundByName = apiWithoutCopy.findGroupByName("lobby").join();
        List<Group> allGroups = apiWithoutCopy.getAllGroups(null).join();

        assertEquals(LOBBY_GROUP_ID, groupFoundById.orElseThrow().getServerGroupId());
        assertEquals(LOBBY_GROUP_ID, groupFoundByName.orElseThrow().getServerGroupId());
        assertEquals(List.of(LOBBY_GROUP_ID), listGroupIds(allGroups));
        assertEquals(List.of(GROUP_LIST_READ, GROUP_LIST_READ, GROUP_LIST_READ), controllerRequests);
    }

    @Test
    void createGroupReturnsOnceABatchBringsItsRevision() throws InterruptedException {
        receive("pulse", buildPulse());
        receiveLater(buildBatchToRevision6());

        api.createGroup(buildCreateRequest()).join();

        assertEquals(6, synchronizer.currentContents().revision());
    }

    @Test
    void updateGroupReturnsOnceABatchBringsItsRevision() throws InterruptedException {
        receive("pulse", buildPulse());
        receiveLater(buildBatchToRevision6());

        api.updateGroup(LOBBY_GROUP_ID, buildUpdateRequest()).join();

        assertEquals(6, synchronizer.currentContents().revision());
    }

    @Test
    void deleteGroupReturnsOnceABatchBringsItsRevision() throws InterruptedException {
        receive("pulse", buildPulse());
        receiveLater(buildBatchToRevision6());

        api.deleteGroup(LOBBY_GROUP_ID).join();

        assertEquals(6, synchronizer.currentContents().revision());
    }

    @Test
    void updateGroupPropertiesReturnsOnceABatchBringsItsRevision() throws InterruptedException {
        receive("pulse", buildPulse());
        receiveLater(buildBatchToRevision6());

        api.updateGroupProperties(LOBBY_GROUP_ID, Map.of("motd", "hello")).join();

        assertEquals(6, synchronizer.currentContents().revision());
    }

    @Test
    void deleteGroupPropertiesReturnsOnceABatchBringsItsRevision() throws InterruptedException {
        receive("pulse", buildPulse());
        receiveLater(buildBatchToRevision6());

        api.deleteGroupProperties(LOBBY_GROUP_ID, List.of("motd")).join();

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
        api.createGroup(buildCreateRequest()).join();
        api.updateGroup(LOBBY_GROUP_ID, buildUpdateRequest()).join();
        api.deleteGroup(LOBBY_GROUP_ID).join();
        api.updateGroupProperties(LOBBY_GROUP_ID, Map.of("motd", "hello")).join();
        api.deleteGroupProperties(LOBBY_GROUP_ID, List.of("motd")).join();
    }

    private MockResponse respondTo(RecordedRequest request) {
        String route = request.getMethod() + " " + request.getRequestUrl().encodedPath();
        controllerRequests.add(route);
        if (route.equals(SNAPSHOT_LOAD)) {
            byte[] snapshotBytes = buildSnapshot().toByteArray();
            Buffer snapshotBody = new Buffer().write(snapshotBytes);
            return new MockResponse().setBody(snapshotBody);
        }
        if (route.equals(GROUP_LIST_READ)) {
            return buildJsonResponse(GROUP_LIST_JSON);
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
        NatsMessage natsMessage = NatsMessage.builder()
                .subject(subject)
                .data(message.toByteArray())
                .build();
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

    private static CreateGroupRequest buildCreateRequest() {
        return CreateGroupRequest.builder()
                .name("lobby")
                .type(GroupServerType.SERVER)
                .build();
    }

    private static UpdateGroupRequest buildUpdateRequest() {
        return UpdateGroupRequest.builder()
                .maxPlayers(30)
                .build();
    }

    private static MockResponse buildJsonResponse(String body) {
        return new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(body);
    }

    private static StatePulse buildPulse() {
        return StatePulse.newBuilder()
                .setEpoch(1)
                .setRevision(5)
                .setViewsMayAnswer(true)
                .build();
    }

    private static StateBatch buildBatchToRevision6() {
        return StateBatch.newBuilder()
                .setEpoch(1)
                .setBaseRevision(5)
                .setRevision(6)
                .build();
    }

    private static NetworkState buildSnapshot() {
        return NetworkState.newBuilder()
                .setEpoch(1)
                .setRevision(5)
                .addServerGroups(buildStateServerGroup(LOBBY_GROUP_ID, "lobby", "2026-11-17T09:00:00Z"))
                .addServerGroups(buildStateServerGroup(GAME_GROUP_ID, "game", "2026-11-17T10:00:00Z"))
                .build();
    }

    private static StateServerGroup buildStateServerGroup(String id, String name, String createdAt) {
        String summaryJson = "{\"server_group_id\":\"" + id + "\",\"name\":\"" + name + "\",\"created_at\":\"" + createdAt + "\"}";
        String infoJson = "{\"id\":\"" + id + "\",\"name\":\"" + name + "\"}";
        return StateServerGroup.newBuilder()
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

    private static List<String> listGroupIds(List<Group> groups) {
        List<String> ids = new ArrayList<>();
        for (Group group : groups) {
            ids.add(group.getServerGroupId());
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
