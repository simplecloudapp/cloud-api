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
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.protobuf.ByteString;
import okhttp3.HttpUrl;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Every server, group and persistent server read of the API classes, once answered from a copy of the
 * {@code blueprint-sources} fixture while no request reaches the controller, and once without a copy, answered
 * by the controller from the fixture's lists after one request that carries the read's filter.
 */
class ApiReadsTest {

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
    private static final String SERVERS_PATH = "/v0/servers";
    private static final String GROUPS_PATH = "/v0/server-groups";
    private static final String PERSISTENT_SERVERS_PATH = "/v0/persistent-servers";

    private final MockWebServer controller = new MockWebServer();
    private final AtomicInteger requests = new AtomicInteger();
    private final Map<String, String> answersByPath = new ConcurrentHashMap<>();
    private String serverListBody;
    private String groupListBody;
    private String persistentServerListBody;
    private ServerApiImpl servers;
    private GroupApiImpl groups;
    private PersistentServerApiImpl persistentServers;
    private CloudApiOptions options;
    private ApiClient apiClient;

    @BeforeEach
    void start() throws IOException, URISyntaxException {
        Path fixture = fixtureDirectory();
        serverListBody = Files.readString(fixture.resolve("servers.json"));
        String groupListWithEmptyScalingModes = Files.readString(fixture.resolve("server-groups.json"));
        groupListBody = withReadableScalingModes(groupListWithEmptyScalingModes);
        persistentServerListBody = Files.readString(fixture.resolve("persistent-servers.json"));
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

    @Nested
    class WithoutACopy {

        @BeforeEach
        void startApisWithoutACopy() {
            startApis(ReadableCopies.none());
        }

        @Test
        void getServerByIdAsksTheControllerForTheId() throws InterruptedException {
            controllerAnswersServers(LOBBY_SERVER_ID);

            Server server = servers.getServerById(LOBBY_SERVER_ID).join();
            RecordedRequest request = onlyRequest();

            assertEquals(LOBBY_SERVER_ID, server.getServerId());
            assertEquals(SERVERS_PATH, pathOf(request));
            assertEquals(Map.of("server_id", LOBBY_SERVER_ID), queryOf(request));
        }

        @Test
        void findServerByIdAsksTheControllerForTheId() throws InterruptedException {
            controllerAnswersServers(PROXY_SERVER_ID);

            Optional<Server> server = servers.findServerById(PROXY_SERVER_ID).join();
            RecordedRequest request = onlyRequest();

            assertEquals(PROXY_SERVER_ID, server.orElseThrow().getServerId());
            assertEquals(SERVERS_PATH, pathOf(request));
            assertEquals(Map.of("server_id", PROXY_SERVER_ID), queryOf(request));
        }

        @Test
        void getServerByNumericalIdAsksTheControllerForGroupNameAndNumericalId() throws InterruptedException {
            controllerAnswersServers(LOBBY_SERVER_ID);

            Server server = servers.getServerByNumericalId("lobby", 1).join();
            RecordedRequest request = onlyRequest();

            assertEquals(LOBBY_SERVER_ID, server.getServerId());
            assertEquals(SERVERS_PATH, pathOf(request));
            assertEquals(Map.of("name", "lobby", "numerical_id", "1"), queryOf(request));
        }

        @Test
        void findServerByNumericalIdAsksTheControllerForGroupNameAndNumericalId() throws InterruptedException {
            controllerAnswersServers(PROXY_SERVER_ID);

            Optional<Server> server = servers.findServerByNumericalId("proxy", 1).join();
            RecordedRequest request = onlyRequest();

            assertEquals(PROXY_SERVER_ID, server.orElseThrow().getServerId());
            assertEquals(SERVERS_PATH, pathOf(request));
            assertEquals(Map.of("name", "proxy", "numerical_id", "1"), queryOf(request));
        }

        @Test
        void getServersByGroupAsksTheControllerForTheGroupName() throws InterruptedException {
            controllerAnswersServers(GAME_SERVER_ID);

            List<Server> ofGame = servers.getServersByGroup("game").join();
            RecordedRequest request = onlyRequest();

            assertEquals(List.of(GAME_SERVER_ID), serverIds(ofGame));
            assertEquals(SERVERS_PATH, pathOf(request));
            assertEquals(Map.of("name", "game"), queryOf(request));
        }

        @Test
        void getAllServersWithoutQueryAsksTheControllerWithoutFilter() throws InterruptedException {
            controllerAnswersServers(LOBBY_SERVER_ID, PROXY_SERVER_ID, GAME_SERVER_ID, HUB_SERVER_ID);

            List<Server> all = servers.getAllServers(null).join();
            RecordedRequest request = onlyRequest();

            assertEquals(List.of(LOBBY_SERVER_ID, PROXY_SERVER_ID, GAME_SERVER_ID, HUB_SERVER_ID), serverIds(all));
            assertEquals(SERVERS_PATH, pathOf(request));
            assertEquals(Map.of(), queryOf(request));
        }

        @Test
        void getAllServersWithQueryAsksTheControllerForTheState() throws InterruptedException {
            controllerAnswersServers(GAME_SERVER_ID);
            ServerQuery stopping = ServerQuery.create().filterByState(ServerState.STOPPING);

            List<Server> listed = servers.getAllServers(stopping).join();
            RecordedRequest request = onlyRequest();

            assertEquals(List.of(GAME_SERVER_ID), serverIds(listed));
            assertEquals(SERVERS_PATH, pathOf(request));
            assertEquals(Map.of("state", "STOPPING"), queryOf(request));
        }

        @Test
        void getAllServersWithAnExactLookupAsksTheControllerForGroupNameAndNumericalId()
                throws InterruptedException {
            controllerAnswersServers(LOBBY_SERVER_ID);
            ServerQuery exactLookup = ServerQuery.create().filterByServerGroupName("lobby").filterByNumericalId(1);

            List<Server> listed = servers.getAllServers(exactLookup).join();
            RecordedRequest request = onlyRequest();

            assertEquals(List.of(LOBBY_SERVER_ID), serverIds(listed));
            assertEquals(SERVERS_PATH, pathOf(request));
            assertEquals(Map.of("name", "lobby", "numerical_id", "1"), queryOf(request));
        }

        @Test
        void getGroupByNameAsksTheControllerForTheName() throws InterruptedException {
            controllerAnswersGroups(LOBBY_GROUP_ID);

            Group group = groups.getGroupByName("lobby").join();
            RecordedRequest request = onlyRequest();

            assertEquals(LOBBY_GROUP_ID, group.getServerGroupId());
            assertEquals(GROUPS_PATH, pathOf(request));
            assertEquals(Map.of("name", "lobby"), queryOf(request));
        }

        @Test
        void findGroupByNameAsksTheControllerForTheName() throws InterruptedException {
            controllerAnswersGroups(PROXY_GROUP_ID);

            Optional<Group> group = groups.findGroupByName("proxy").join();
            RecordedRequest request = onlyRequest();

            assertEquals(PROXY_GROUP_ID, group.orElseThrow().getServerGroupId());
            assertEquals(GROUPS_PATH, pathOf(request));
            assertEquals(Map.of("name", "proxy"), queryOf(request));
        }

        @Test
        void getGroupByIdAsksTheControllerForTheId() throws InterruptedException {
            controllerAnswersGroups(GAME_GROUP_ID);

            Group group = groups.getGroupById(GAME_GROUP_ID).join();
            RecordedRequest request = onlyRequest();

            assertEquals("game", group.getName());
            assertEquals(GROUPS_PATH, pathOf(request));
            assertEquals(Map.of("server_group_id", GAME_GROUP_ID), queryOf(request));
        }

        @Test
        void findGroupByIdAsksTheControllerForTheId() throws InterruptedException {
            controllerAnswersGroups(LOBBY_GROUP_ID);

            Optional<Group> group = groups.findGroupById(LOBBY_GROUP_ID).join();
            RecordedRequest request = onlyRequest();

            assertEquals("lobby", group.orElseThrow().getName());
            assertEquals(GROUPS_PATH, pathOf(request));
            assertEquals(Map.of("server_group_id", LOBBY_GROUP_ID), queryOf(request));
        }

        @Test
        void getAllGroupsWithoutQueryAsksTheControllerWithoutFilter() throws InterruptedException {
            controllerAnswersGroups(GAME_GROUP_ID, PROXY_GROUP_ID, LOBBY_GROUP_ID);

            List<Group> all = groups.getAllGroups(null).join();
            RecordedRequest request = onlyRequest();

            assertEquals(List.of(GAME_GROUP_ID, PROXY_GROUP_ID, LOBBY_GROUP_ID), groupIds(all));
            assertEquals(GROUPS_PATH, pathOf(request));
            assertEquals(Map.of(), queryOf(request));
        }

        @Test
        void getAllGroupsWithQueryAsksTheControllerForTheType() throws InterruptedException {
            controllerAnswersGroups(PROXY_GROUP_ID);
            GroupQuery proxies = GroupQuery.create().filterByType(GroupServerType.PROXY);

            List<Group> listed = groups.getAllGroups(proxies).join();
            RecordedRequest request = onlyRequest();

            assertEquals(List.of(PROXY_GROUP_ID), groupIds(listed));
            assertEquals(GROUPS_PATH, pathOf(request));
            assertEquals(Map.of("type", "PROXY"), queryOf(request));
        }

        @Test
        void getPersistentServerByIdAsksTheControllerForTheId() throws InterruptedException {
            controllerAnswersPersistentServers(HUB_ID);

            PersistentServer persistentServer = persistentServers.getPersistentServerById(HUB_ID).join();
            RecordedRequest request = onlyRequest();

            assertEquals("hub", persistentServer.getName());
            assertEquals(PERSISTENT_SERVERS_PATH, pathOf(request));
            assertEquals(Map.of("persistent_server_id", HUB_ID), queryOf(request));
        }

        @Test
        void findPersistentServerByIdAsksTheControllerForTheId() throws InterruptedException {
            controllerAnswersPersistentServers(HUB_ID);

            Optional<PersistentServer> persistentServer = persistentServers.findPersistentServerById(HUB_ID).join();
            RecordedRequest request = onlyRequest();

            assertEquals("hub", persistentServer.orElseThrow().getName());
            assertEquals(PERSISTENT_SERVERS_PATH, pathOf(request));
            assertEquals(Map.of("persistent_server_id", HUB_ID), queryOf(request));
        }

        @Test
        void getPersistentServerByNameAsksTheControllerForTheName() throws InterruptedException {
            controllerAnswersPersistentServers(HUB_ID);

            PersistentServer persistentServer = persistentServers.getPersistentServerByName("hub").join();
            RecordedRequest request = onlyRequest();

            assertEquals(HUB_ID, persistentServer.getPersistentServerId());
            assertEquals(PERSISTENT_SERVERS_PATH, pathOf(request));
            assertEquals(Map.of("name", "hub"), queryOf(request));
        }

        @Test
        void findPersistentServerByNameAsksTheControllerForTheName() throws InterruptedException {
            controllerAnswersPersistentServers(HUB_ID);

            Optional<PersistentServer> persistentServer = persistentServers.findPersistentServerByName("hub").join();
            RecordedRequest request = onlyRequest();

            assertEquals(HUB_ID, persistentServer.orElseThrow().getPersistentServerId());
            assertEquals(PERSISTENT_SERVERS_PATH, pathOf(request));
            assertEquals(Map.of("name", "hub"), queryOf(request));
        }

        @Test
        void getAllPersistentServersWithoutQueryAsksTheControllerWithoutFilter() throws InterruptedException {
            controllerAnswersPersistentServers(HUB_ID);

            List<PersistentServer> all = persistentServers.getAllPersistentServers(null).join();
            RecordedRequest request = onlyRequest();

            assertEquals(List.of(HUB_ID), persistentServerIds(all));
            assertEquals(PERSISTENT_SERVERS_PATH, pathOf(request));
            assertEquals(Map.of(), queryOf(request));
        }

        @Test
        void getAllPersistentServersWithQueryAsksTheControllerForTheActiveFlag() throws InterruptedException {
            controllerAnswersPersistentServers(HUB_ID);
            PersistentServerQuery active = PersistentServerQuery.create().filterByActive(true);

            List<PersistentServer> listed = persistentServers.getAllPersistentServers(active).join();
            RecordedRequest request = onlyRequest();

            assertEquals(List.of(HUB_ID), persistentServerIds(listed));
            assertEquals(PERSISTENT_SERVERS_PATH, pathOf(request));
            assertEquals(Map.of("active", "true"), queryOf(request));
        }
    }

    private void startApis(ReadableCopies copies) {
        servers = new ServerApiImpl(options, new NoOpQueryCache(), apiClient, CALLER_THREAD, copies);
        groups = new GroupApiImpl(options, new NoOpQueryCache(), apiClient, CALLER_THREAD, copies);
        persistentServers = new PersistentServerApiImpl(options, new NoOpQueryCache(), apiClient, CALLER_THREAD,
                copies);
    }

    private void controllerAnswersServers(String... serverIds) {
        String answer = listWithOnly(serverListBody, "servers", "server_id", List.of(serverIds));
        answersByPath.put(SERVERS_PATH, answer);
    }

    private void controllerAnswersGroups(String... groupIds) {
        String answer = listWithOnly(groupListBody, "server_groups", "server_group_id", List.of(groupIds));
        answersByPath.put(GROUPS_PATH, answer);
    }

    private void controllerAnswersPersistentServers(String... persistentServerIds) {
        List<String> ids = List.of(persistentServerIds);
        String answer = listWithOnly(persistentServerListBody, "persistent_servers", "persistent_server_id", ids);
        answersByPath.put(PERSISTENT_SERVERS_PATH, answer);
    }

    /**
     * Keeps the fixture's order, which is the controller's.
     */
    private static String listWithOnly(String fixtureList, String listName, String idName, List<String> ids) {
        JsonObject fixture = JsonParser.parseString(fixtureList).getAsJsonObject();
        JsonArray items = fixture.getAsJsonArray(listName);
        JsonArray kept = new JsonArray();
        for (JsonElement item : items) {
            JsonObject fields = item.getAsJsonObject();
            String id = fields.get(idName).getAsString();
            if (ids.contains(id)) {
                kept.add(item);
            }
        }
        JsonObject answer = new JsonObject();
        answer.add(listName, kept);
        answer.addProperty("count", kept.size());
        return answer.toString();
    }

    /**
     * Also checks that the read sent no second request, as a retry would.
     */
    private RecordedRequest onlyRequest() throws InterruptedException {
        RecordedRequest request = controller.takeRequest(1, TimeUnit.SECONDS);
        assertEquals(1, requests.get(), "one request to the controller");
        return Objects.requireNonNull(request, "the controller saw no request");
    }

    private static String pathOf(RecordedRequest request) {
        HttpUrl url = Objects.requireNonNull(request.getRequestUrl());
        return url.encodedPath();
    }

    private static Map<String, String> queryOf(RecordedRequest request) {
        HttpUrl url = Objects.requireNonNull(request.getRequestUrl());
        Map<String, String> query = new HashMap<>();
        for (String name : url.queryParameterNames()) {
            query.put(name, url.queryParameter(name));
        }
        return query;
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

    private static NetworkState withReadableScalingModes(NetworkState snapshot) {
        NetworkState.Builder readable = snapshot.toBuilder();
        for (int index = 0; index < readable.getServerGroupsCount(); index++) {
            StateServerGroup group = readable.getServerGroups(index);
            String summaryJson = group.getSummaryJson().toStringUtf8();
            String summary = withReadableScalingModes(summaryJson);
            StateServerGroup readableGroup = group.toBuilder()
                    .setSummaryJson(ByteString.copyFromUtf8(summary))
                    .build();
            readable.setServerGroups(index, readableGroup);
        }
        return readable.build();
    }

    /**
     * Two of the fixture's groups have an empty scaling mode, which the generated client refuses, so no group
     * read could be answered without it.
     */
    private static String withReadableScalingModes(String groupJson) {
        return groupJson.replace("\"scaling_mode\":\"\"", "\"scaling_mode\":\"SLOTS\"");
    }

    private static Path fixtureDirectory() throws URISyntaxException {
        URL root = Objects.requireNonNull(ApiReadsTest.class.getResource("/network-state/blueprint-sources"));
        return Path.of(root.toURI());
    }

    private final class CountingDispatcher extends Dispatcher {

        @Override
        public @NotNull MockResponse dispatch(@NotNull RecordedRequest request) {
            requests.incrementAndGet();
            String answer = answersByPath.get(pathOf(request));
            if (answer == null) {
                return new MockResponse().setResponseCode(404);
            }
            return new MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody(answer);
        }
    }
}
