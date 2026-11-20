package app.simplecloud.api.internal.group;

import app.simplecloud.api.CloudApiOptions;
import app.simplecloud.api.group.CreateGroupRequest;
import app.simplecloud.api.group.Group;
import app.simplecloud.api.group.GroupQuery;
import app.simplecloud.api.group.GroupServerType;
import app.simplecloud.api.group.UpdateGroupRequest;
import app.simplecloud.api.internal.cache.NoOpQueryCache;
import app.simplecloud.api.internal.nats.FakeNatsConnection;
import app.simplecloud.api.internal.networkcopy.NetworkCopy;
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
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeout;

/**
 * The copy gets its messages through a fake NATS connection and loads from a local controller,
 * which also answers the HTTP reads and writes of the API.
 */
class GroupApiImplCopyTest {

    private static final String NETWORK_ID = "0d000000-0000-4000-8000-000000000001";
    private static final String LOBBY_GROUP_ID = "0a000000-0000-4000-8000-000000000301";
    private static final String GAME_GROUP_ID = "0a000000-0000-4000-8000-000000000302";
    private static final String NETWORK_STATE_LOAD = "GET /v0/network-state";
    private static final String GROUP_LIST_READ = "GET /v0/server-groups";
    private static final String GROUP_LIST_JSON = "{\"server_groups\":[{\"server_group_id\":\"" + LOBBY_GROUP_ID + "\","
            + "\"name\":\"lobby\",\"created_at\":\"2026-11-17T09:00:00Z\"}]}";
    private static final String WRITE_ANSWER_JSON = "{\"server_group_id\":\"" + LOBBY_GROUP_ID + "\",\"name\":\"lobby\","
            + "\"type\":\"SERVER\",\"properties\":{\"motd\":\"hello\"}}";

    private final List<String> controllerRequests = new CopyOnWriteArrayList<>();
    private volatile boolean sendsRevisionHeaders = true;
    private MockWebServer controller;
    private FakeNatsConnection nats;
    private NetworkCopy copy;
    private GroupApiImpl api;

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
        api = new GroupApiImpl(options(), new NoOpQueryCache(), ApiClients.create(options()), Runnable::run, copy);
    }

    @AfterEach
    void stopController() throws IOException {
        controller.close();
    }

    @Test
    void findGroupByIdAnswersFromTheCopy() throws InterruptedException {
        receive("pulse", pulse());

        Optional<Group> group = api.findGroupById(GAME_GROUP_ID).join();

        assertEquals(GAME_GROUP_ID, group.orElseThrow().getServerGroupId());
        assertEquals(List.of(NETWORK_STATE_LOAD), controllerRequests);
    }

    @Test
    void findGroupByNameAnswersFromTheCopy() throws InterruptedException {
        receive("pulse", pulse());

        Optional<Group> group = api.findGroupByName("lobby").join();

        assertEquals(LOBBY_GROUP_ID, group.orElseThrow().getServerGroupId());
        assertEquals(List.of(NETWORK_STATE_LOAD), controllerRequests);
    }

    @Test
    void getAllGroupsAnswersFromTheCopy() throws InterruptedException {
        receive("pulse", pulse());
        GroupQuery newestOnly = GroupQuery.create().limit(1);

        List<Group> groups = api.getAllGroups(newestOnly).join();

        assertEquals(List.of(GAME_GROUP_ID), groupIds(groups));
        assertEquals(List.of(NETWORK_STATE_LOAD), controllerRequests);
    }

    @Test
    void withoutACopyEachReadAsksTheController() {
        GroupApiImpl apiWithoutCopy = new GroupApiImpl(
                options(),
                new NoOpQueryCache(),
                ApiClients.create(options()),
                Runnable::run,
                NetworkCopy.neverStarted()
        );

        Optional<Group> byId = apiWithoutCopy.findGroupById(LOBBY_GROUP_ID).join();
        Optional<Group> byName = apiWithoutCopy.findGroupByName("lobby").join();
        List<Group> all = apiWithoutCopy.getAllGroups(null).join();

        assertEquals(LOBBY_GROUP_ID, byId.orElseThrow().getServerGroupId());
        assertEquals(LOBBY_GROUP_ID, byName.orElseThrow().getServerGroupId());
        assertEquals(List.of(LOBBY_GROUP_ID), groupIds(all));
        assertEquals(List.of(GROUP_LIST_READ, GROUP_LIST_READ, GROUP_LIST_READ), controllerRequests);
    }

    @Test
    void createGroupReturnsOnceABatchBringsItsRevision() throws InterruptedException {
        receive("pulse", pulse());
        receiveLater(batchToRevision6());

        api.createGroup(createRequest()).join();

        assertEquals(6, copy.currentContents().revision());
    }

    @Test
    void updateGroupReturnsOnceABatchBringsItsRevision() throws InterruptedException {
        receive("pulse", pulse());
        receiveLater(batchToRevision6());

        api.updateGroup(LOBBY_GROUP_ID, updateRequest()).join();

        assertEquals(6, copy.currentContents().revision());
    }

    @Test
    void deleteGroupReturnsOnceABatchBringsItsRevision() throws InterruptedException {
        receive("pulse", pulse());
        receiveLater(batchToRevision6());

        api.deleteGroup(LOBBY_GROUP_ID).join();

        assertEquals(6, copy.currentContents().revision());
    }

    @Test
    void updateGroupPropertiesReturnsOnceABatchBringsItsRevision() throws InterruptedException {
        receive("pulse", pulse());
        receiveLater(batchToRevision6());

        api.updateGroupProperties(LOBBY_GROUP_ID, Map.of("motd", "hello")).join();

        assertEquals(6, copy.currentContents().revision());
    }

    @Test
    void deleteGroupPropertiesReturnsOnceABatchBringsItsRevision() throws InterruptedException {
        receive("pulse", pulse());
        receiveLater(batchToRevision6());

        api.deleteGroupProperties(LOBBY_GROUP_ID, List.of("motd")).join();

        assertEquals(6, copy.currentContents().revision());
    }

    @Test
    void writesWithoutRevisionHeadersDoNotWait() throws InterruptedException {
        receive("pulse", pulse());
        sendsRevisionHeaders = false;

        assertTimeout(Duration.ofSeconds(2), this::runEachWrite);

        assertEquals(5, copy.currentContents().revision());
    }

    private void runEachWrite() {
        api.createGroup(createRequest()).join();
        api.updateGroup(LOBBY_GROUP_ID, updateRequest()).join();
        api.deleteGroup(LOBBY_GROUP_ID).join();
        api.updateGroupProperties(LOBBY_GROUP_ID, Map.of("motd", "hello")).join();
        api.deleteGroupProperties(LOBBY_GROUP_ID, List.of("motd")).join();
    }

    private MockResponse answer(RecordedRequest request) {
        String route = request.getMethod() + " " + request.getRequestUrl().encodedPath();
        controllerRequests.add(route);
        if (route.equals(NETWORK_STATE_LOAD)) {
            Buffer state = new Buffer().write(networkState().toByteArray());
            return new MockResponse().setBody(state);
        }
        if (route.equals(GROUP_LIST_READ)) {
            return json(GROUP_LIST_JSON);
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

    private static CreateGroupRequest createRequest() {
        return CreateGroupRequest.builder()
                .name("lobby")
                .type(GroupServerType.SERVER)
                .build();
    }

    private static UpdateGroupRequest updateRequest() {
        return UpdateGroupRequest.builder()
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
                .addServerGroups(group(LOBBY_GROUP_ID, "lobby", "2026-11-17T09:00:00Z"))
                .addServerGroups(group(GAME_GROUP_ID, "game", "2026-11-17T10:00:00Z"))
                .build();
    }

    private static StateServerGroup group(String id, String name, String createdAt) {
        String summary = "{\"server_group_id\":\"" + id + "\",\"name\":\"" + name + "\",\"created_at\":\"" + createdAt + "\"}";
        String info = "{\"id\":\"" + id + "\",\"name\":\"" + name + "\"}";
        return StateServerGroup.newBuilder()
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

    private static List<String> groupIds(List<Group> groups) {
        List<String> ids = new ArrayList<>();
        for (Group group : groups) {
            ids.add(group.getServerGroupId());
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
