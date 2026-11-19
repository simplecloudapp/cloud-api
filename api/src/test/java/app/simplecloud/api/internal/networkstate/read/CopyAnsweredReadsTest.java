package app.simplecloud.api.internal.networkstate.read;

import app.simplecloud.api.CloudApiException;
import app.simplecloud.api.CloudApiOptions;
import app.simplecloud.api.group.Group;
import app.simplecloud.api.group.GroupQuery;
import app.simplecloud.api.group.GroupServerType;
import app.simplecloud.api.internal.cache.NoOpQueryCache;
import app.simplecloud.api.internal.group.GroupApiImpl;
import app.simplecloud.api.internal.networkstate.AnsweringView;
import app.simplecloud.api.internal.networkstate.NetworkCopy;
import app.simplecloud.api.internal.persistentserver.PersistentServerApiImpl;
import app.simplecloud.api.internal.server.ServerApiImpl;
import app.simplecloud.api.internal.web.ApiClients;
import app.simplecloud.api.persistentserver.PersistentServer;
import app.simplecloud.api.persistentserver.PersistentServerQuery;
import app.simplecloud.api.server.Server;
import app.simplecloud.api.server.ServerQuery;
import app.simplecloud.api.server.ServerState;
import app.simplecloud.api.web.ApiClient;
import build.buf.gen.simplecloud.controller.v2.NetworkState;
import build.buf.gen.simplecloud.controller.v2.StateServerGroup;
import com.google.protobuf.ByteString;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Every server, group and persistent server read of the API classes, answered from a copy of the
 * {@code blueprint-sources} fixture while no request reaches the controller.
 */
class CopyAnsweredReadsTest {

    private static final String NETWORK_ID = "0d000000-0000-4000-8000-000000000001";
    private static final String LOBBY_SERVER_ID = "0d000000-0000-4000-8000-000000000201";
    private static final String PROXY_SERVER_ID = "0d000000-0000-4000-8000-000000000202";
    private static final String GAME_SERVER_ID = "0d000000-0000-4000-8000-000000000203";
    private static final String HUB_SERVER_ID = "0d000000-0000-4000-8000-000000000204";
    private static final String LOBBY_GROUP_ID = "0d000000-0000-4000-8000-000000000301";
    private static final String PROXY_GROUP_ID = "0d000000-0000-4000-8000-000000000302";
    private static final String GAME_GROUP_ID = "0d000000-0000-4000-8000-000000000303";
    private static final String HUB_ID = "0d000000-0000-4000-8000-000000000401";
    private static final String UNKNOWN_ID = "0d000000-0000-4000-8000-0000000000ff";
    private static final Executor CALLER_THREAD = Runnable::run;

    private final MockWebServer controller = new MockWebServer();
    private final AtomicInteger requests = new AtomicInteger();
    private String serverListBody;
    private ServerApiImpl servers;
    private GroupApiImpl groups;
    private PersistentServerApiImpl persistentServers;
    private CloudApiOptions options;
    private ApiClient apiClient;

    @BeforeEach
    void start() throws IOException, URISyntaxException {
        Path fixture = fixtureDirectory();
        serverListBody = Files.readString(fixture.resolve("servers.json"));
        controller.setDispatcher(new CountingDispatcher());
        controller.start();
        options = CloudApiOptions.builder()
                .controllerUrl("http://" + controller.getHostName() + ":" + controller.getPort())
                .networkId(NETWORK_ID)
                .networkSecret("secret")
                .build();
        apiClient = ApiClients.create(options);

        NetworkState snapshot = NetworkState.parseFrom(Files.readAllBytes(fixture.resolve("network-state.pb")));
        NetworkCopy copy = NetworkCopy.of(withReadableScalingModes(snapshot));
        AnsweringView alwaysTheFixture = () -> CompletableFuture.completedFuture(Optional.of(copy));
        startApis(new ReadableCopies(alwaysTheFixture, NETWORK_ID));
    }

    @AfterEach
    void stop() throws IOException {
        controller.close();
    }

    @Test
    void getServerByIdAnswersFromTheCopy() {
        Server server = servers.getServerById(LOBBY_SERVER_ID).join();
        CloudApiException.Reason unknown = failureReason(servers.getServerById(UNKNOWN_ID));

        assertEquals(LOBBY_SERVER_ID, server.getServerId());
        assertEquals(CloudApiException.Reason.NOT_FOUND, unknown);
        assertEquals(0, requests.get());
    }

    @Test
    void findServerByIdAnswersFromTheCopy() {
        Optional<Server> server = servers.findServerById(PROXY_SERVER_ID).join();

        assertEquals(PROXY_SERVER_ID, server.orElseThrow().getServerId());
        assertEquals(0, requests.get());
    }

    @Test
    void getServerByNumericalIdAnswersFromTheCopy() {
        Server server = servers.getServerByNumericalId("lobby", 1).join();
        CloudApiException.Reason unknown = failureReason(servers.getServerByNumericalId("lobby", 9));

        assertEquals(LOBBY_SERVER_ID, server.getServerId());
        assertEquals(CloudApiException.Reason.NOT_FOUND, unknown);
        assertEquals(0, requests.get());
    }

    @Test
    void findServerByNumericalIdAnswersFromTheCopy() {
        Optional<Server> server = servers.findServerByNumericalId("proxy", 1).join();

        assertEquals(PROXY_SERVER_ID, server.orElseThrow().getServerId());
        assertEquals(0, requests.get());
    }

    @Test
    void getServersByGroupAnswersFromTheCopy() {
        List<Server> ofGame = servers.getServersByGroup("game").join();

        assertEquals(List.of(GAME_SERVER_ID), serverIds(ofGame));
        assertEquals(0, requests.get());
    }

    @Test
    void getAllServersWithoutQueryAnswersFromTheCopy() {
        List<Server> all = servers.getAllServers(null).join();

        assertEquals(List.of(LOBBY_SERVER_ID, PROXY_SERVER_ID, GAME_SERVER_ID, HUB_SERVER_ID), serverIds(all));
        assertEquals(0, requests.get());
    }

    @Test
    void getAllServersWithQueryAnswersFromTheCopy() {
        ServerQuery stopping = ServerQuery.create().filterByState(ServerState.STOPPING);

        List<Server> listed = servers.getAllServers(stopping).join();

        assertEquals(List.of(GAME_SERVER_ID), serverIds(listed));
        assertEquals(0, requests.get());
    }

    @Test
    void getAllServersWithAnExactLookupAnswersFromTheCopy() {
        ServerQuery exactLookup = ServerQuery.create().filterByServerGroupName("lobby").filterByNumericalId(1);

        List<Server> listed = servers.getAllServers(exactLookup).join();

        assertEquals(List.of(LOBBY_SERVER_ID), serverIds(listed));
        assertEquals(0, requests.get());
    }

    @Test
    void getGroupByNameAnswersFromTheCopy() {
        Group group = groups.getGroupByName("lobby").join();
        CloudApiException.Reason unknown = failureReason(groups.getGroupByName("missing"));

        assertEquals(LOBBY_GROUP_ID, group.getServerGroupId());
        assertEquals(CloudApiException.Reason.NOT_FOUND, unknown);
        assertEquals(0, requests.get());
    }

    @Test
    void findGroupByNameAnswersFromTheCopy() {
        Optional<Group> group = groups.findGroupByName("proxy").join();

        assertEquals(PROXY_GROUP_ID, group.orElseThrow().getServerGroupId());
        assertEquals(0, requests.get());
    }

    @Test
    void getGroupByIdAnswersFromTheCopy() {
        Group group = groups.getGroupById(GAME_GROUP_ID).join();
        CloudApiException.Reason unknown = failureReason(groups.getGroupById(UNKNOWN_ID));

        assertEquals("game", group.getName());
        assertEquals(CloudApiException.Reason.NOT_FOUND, unknown);
        assertEquals(0, requests.get());
    }

    @Test
    void findGroupByIdAnswersFromTheCopy() {
        Optional<Group> group = groups.findGroupById(LOBBY_GROUP_ID).join();

        assertEquals("lobby", group.orElseThrow().getName());
        assertEquals(0, requests.get());
    }

    @Test
    void getAllGroupsWithoutQueryAnswersFromTheCopy() {
        List<Group> all = groups.getAllGroups(null).join();

        assertEquals(List.of(GAME_GROUP_ID, PROXY_GROUP_ID, LOBBY_GROUP_ID), groupIds(all));
        assertEquals(0, requests.get());
    }

    @Test
    void getAllGroupsWithQueryAnswersFromTheCopy() {
        GroupQuery proxies = GroupQuery.create().filterByType(GroupServerType.PROXY);

        List<Group> listed = groups.getAllGroups(proxies).join();

        assertEquals(List.of(PROXY_GROUP_ID), groupIds(listed));
        assertEquals(0, requests.get());
    }

    @Test
    void getPersistentServerByIdAnswersFromTheCopy() {
        PersistentServer persistentServer = persistentServers.getPersistentServerById(HUB_ID).join();
        CloudApiException.Reason unknown = failureReason(persistentServers.getPersistentServerById(UNKNOWN_ID));

        assertEquals("hub", persistentServer.getName());
        assertEquals(CloudApiException.Reason.NOT_FOUND, unknown);
        assertEquals(0, requests.get());
    }

    @Test
    void findPersistentServerByIdAnswersFromTheCopy() {
        Optional<PersistentServer> persistentServer = persistentServers.findPersistentServerById(HUB_ID).join();

        assertEquals("hub", persistentServer.orElseThrow().getName());
        assertEquals(0, requests.get());
    }

    @Test
    void getPersistentServerByNameAnswersFromTheCopy() {
        PersistentServer persistentServer = persistentServers.getPersistentServerByName("hub").join();
        CloudApiException.Reason unknown = failureReason(persistentServers.getPersistentServerByName("missing"));

        assertEquals(HUB_ID, persistentServer.getPersistentServerId());
        assertEquals(CloudApiException.Reason.NOT_FOUND, unknown);
        assertEquals(0, requests.get());
    }

    @Test
    void findPersistentServerByNameAnswersFromTheCopy() {
        Optional<PersistentServer> persistentServer = persistentServers.findPersistentServerByName("hub").join();

        assertEquals(HUB_ID, persistentServer.orElseThrow().getPersistentServerId());
        assertEquals(0, requests.get());
    }

    @Test
    void getAllPersistentServersWithoutQueryAnswersFromTheCopy() {
        List<PersistentServer> all = persistentServers.getAllPersistentServers(null).join();

        assertEquals(List.of(HUB_ID), persistentServerIds(all));
        assertEquals(0, requests.get());
    }

    @Test
    void getAllPersistentServersWithQueryAnswersFromTheCopy() {
        PersistentServerQuery active = PersistentServerQuery.create().filterByActive(true);

        List<PersistentServer> listed = persistentServers.getAllPersistentServers(active).join();

        assertEquals(List.of(HUB_ID), persistentServerIds(listed));
        assertEquals(0, requests.get());
    }

    @Test
    void aReadNoCopyAnswersGoesToTheController() {
        startApis(ReadableCopies.none());

        List<Server> all = servers.getAllServers(null).join();

        assertEquals(List.of(LOBBY_SERVER_ID, PROXY_SERVER_ID, GAME_SERVER_ID, HUB_SERVER_ID), serverIds(all));
        assertEquals(1, requests.get());
    }

    private void startApis(ReadableCopies copies) {
        servers = new ServerApiImpl(options, new NoOpQueryCache(), apiClient, CALLER_THREAD, copies);
        groups = new GroupApiImpl(options, new NoOpQueryCache(), apiClient, CALLER_THREAD, copies);
        persistentServers = new PersistentServerApiImpl(options, new NoOpQueryCache(), apiClient, CALLER_THREAD,
                copies);
    }

    private static CloudApiException.Reason failureReason(CompletableFuture<?> read) {
        CompletionException thrown = assertThrows(CompletionException.class, read::join);
        CloudApiException failure = assertInstanceOf(CloudApiException.class, thrown.getCause());
        return failure.getReason();
    }

    private static List<String> serverIds(List<Server> listed) {
        return listed.stream().map(Server::getServerId).toList();
    }

    private static List<String> groupIds(List<Group> listed) {
        return listed.stream().map(Group::getServerGroupId).toList();
    }

    private static List<String> persistentServerIds(List<PersistentServer> listed) {
        return listed.stream().map(PersistentServer::getPersistentServerId).toList();
    }

    /**
     * Two of the fixture's groups have an empty scaling mode, which the generated client refuses, so no group
     * read could be answered without it.
     */
    private static NetworkState withReadableScalingModes(NetworkState snapshot) {
        NetworkState.Builder readable = snapshot.toBuilder();
        for (int index = 0; index < readable.getServerGroupsCount(); index++) {
            StateServerGroup group = readable.getServerGroups(index);
            String summary = group.getSummaryJson().toStringUtf8()
                    .replace("\"scaling_mode\":\"\"", "\"scaling_mode\":\"SLOTS\"");
            StateServerGroup readableGroup = group.toBuilder()
                    .setSummaryJson(ByteString.copyFromUtf8(summary))
                    .build();
            readable.setServerGroups(index, readableGroup);
        }
        return readable.build();
    }

    private static Path fixtureDirectory() throws URISyntaxException {
        URL root = Objects.requireNonNull(CopyAnsweredReadsTest.class.getResource("/network-state/blueprint-sources"));
        return Path.of(root.toURI());
    }

    private final class CountingDispatcher extends Dispatcher {

        @Override
        public @NotNull MockResponse dispatch(@NotNull RecordedRequest request) {
            requests.incrementAndGet();
            boolean serverList = "/v0/servers".equals(request.getRequestUrl().encodedPath());
            if (!serverList) {
                return new MockResponse().setResponseCode(404);
            }
            return new MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody(serverListBody);
        }
    }
}
