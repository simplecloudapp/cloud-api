package app.simplecloud.api.internal.networkstate.read;

import app.simplecloud.api.CloudApiException;
import app.simplecloud.api.CloudApiOptions;
import app.simplecloud.api.cache.CacheConfig;
import app.simplecloud.api.cache.QueryCache;
import app.simplecloud.api.group.Group;
import app.simplecloud.api.internal.cache.NoOpQueryCache;
import app.simplecloud.api.internal.cache.QueryCacheImpl;
import app.simplecloud.api.internal.group.GroupApiImpl;
import app.simplecloud.api.internal.nats.FakeNatsConnection;
import app.simplecloud.api.internal.networkstate.CopyReads;
import app.simplecloud.api.internal.networkstate.NetworkCopy;
import app.simplecloud.api.internal.networkstate.NetworkViewShell;
import app.simplecloud.api.internal.networkstate.RecordingView;
import app.simplecloud.api.internal.networkstate.SnapshotLoader;
import app.simplecloud.api.internal.persistentserver.PersistentServerApiImpl;
import app.simplecloud.api.internal.server.ServerApiImpl;
import app.simplecloud.api.internal.web.ApiClients;
import app.simplecloud.api.persistentserver.PersistentServer;
import app.simplecloud.api.server.Server;
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
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The server, group and persistent server reads that a view can answer: from its copy while it may, and
 * through the query cache and HTTP otherwise. The controller's side is a fixture's HTTP bodies, which the
 * copy's answers must equal.
 */
class ReadRoutingTest {

    private static final String FIXTURE_CASE = "properties";
    /**
     * {@code fixtureNetworkID} of the controller's fixture test.
     */
    private static final String NETWORK_ID = "0d000000-0000-4000-8000-000000000001";
    private static final String UNKNOWN_ID = "0d000000-0000-4000-8000-0000000000ff";
    private static final String EMPTY_SERVER_LIST = "{\"servers\":[],\"total\":0}";
    private static final Executor CALLER_THREAD = Runnable::run;
    private static final Executor NEVER_RUNS = command -> {
    };
    private static final Duration CACHE_TIMEOUT = Duration.ofMillis(50);

    private final MockWebServer controller = new MockWebServer();
    private final Map<String, String> bodiesByPath = new ConcurrentHashMap<>();
    private final AtomicInteger requests = new AtomicInteger();
    private RecordingView view;
    private NetworkViewShell shell;
    private CopyReads copyReads;
    private CloudApiOptions options;
    private ApiClient apiClient;

    @BeforeEach
    void start() throws IOException, URISyntaxException {
        Path fixture = fixtureDirectory();
        bodiesByPath.put("/v0/servers", Files.readString(fixture.resolve("servers.json")));
        String groupsBody = Files.readString(fixture.resolve("server-groups.json"));
        bodiesByPath.put("/v0/server-groups", withReadableScalingMode(groupsBody));
        bodiesByPath.put("/v0/persistent-servers", Files.readString(fixture.resolve("persistent-servers.json")));
        controller.setDispatcher(new FixtureDispatcher());
        controller.start();
        String controllerUrl = "http://" + controller.getHostName() + ":" + controller.getPort();
        options = CloudApiOptions.builder()
                .controllerUrl(controllerUrl)
                .networkId(NETWORK_ID)
                .networkSecret("secret")
                .build();
        apiClient = ApiClients.create(options);

        NetworkState snapshot = NetworkState.parseFrom(Files.readAllBytes(fixture.resolve("network-state.pb")));
        NetworkCopy copy = Copies.of(withReadableScalingModes(snapshot));
        view = new RecordingView(copy);
        SnapshotLoader unusedSnapshots = new SnapshotLoader(apiClient, controllerUrl, NETWORK_ID);
        FakeNatsConnection nats = FakeNatsConnection.withoutRequests();
        shell = NetworkViewShell.start(NETWORK_ID, nats.connection(), unusedSnapshots, view, () -> 0);
        copyReads = CopyReads.of(shell, NETWORK_ID);
    }

    @AfterEach
    void stop() throws IOException {
        shell.close();
        controller.close();
    }

    @Test
    void listsFromTheCopyEqualTheListsOverHttp() {
        ServerApiImpl servers = serverApi(new NoOpQueryCache());
        GroupApiImpl groups = new GroupApiImpl(options, new NoOpQueryCache(), apiClient, CALLER_THREAD, copyReads);
        PersistentServerApiImpl persistentServers = new PersistentServerApiImpl(options, new NoOpQueryCache(),
                apiClient, CALLER_THREAD, copyReads);

        view.answer(false);
        List<Server> serversOverHttp = servers.getAllServers(null).join();
        List<Group> groupsOverHttp = groups.getAllGroups(null).join();
        List<PersistentServer> persistentServersOverHttp = persistentServers.getAllPersistentServers(null).join();
        int requestsOverHttp = requests.get();
        view.answer(true);
        List<Server> serversFromCopy = servers.getAllServers(null).join();
        List<Group> groupsFromCopy = groups.getAllGroups(null).join();
        List<PersistentServer> persistentServersFromCopy = persistentServers.getAllPersistentServers(null).join();

        assertEquals(3, requestsOverHttp, "a view that may not answer leaves every read to HTTP");
        assertEquals(requestsOverHttp, requests.get(), "a view that may answer answers these lists itself");
        assertEquals(PublicGetters.read(serversOverHttp), PublicGetters.read(serversFromCopy));
        assertEquals(PublicGetters.read(groupsOverHttp), PublicGetters.read(groupsFromCopy));
        assertEquals(PublicGetters.read(persistentServersOverHttp), PublicGetters.read(persistentServersFromCopy));
    }

    @Test
    void lookupsFromTheCopyFindWhatTheHttpListHolds() {
        ServerApiImpl servers = serverApi(new NoOpQueryCache());
        GroupApiImpl groups = new GroupApiImpl(options, new NoOpQueryCache(), apiClient, CALLER_THREAD, copyReads);
        PersistentServerApiImpl persistentServers = new PersistentServerApiImpl(options, new NoOpQueryCache(),
                apiClient, CALLER_THREAD, copyReads);
        view.answer(false);
        List<Server> serversOverHttp = servers.getAllServers(null).join();
        Server server = firstGroupServer(serversOverHttp);
        Group group = groups.findGroupById(server.getServerGroupId()).join().orElseThrow();
        PersistentServer persistentServer = persistentServers.getAllPersistentServers(null).join().getFirst();
        List<Server> serversOfGroup = serversOfGroup(serversOverHttp, group);
        int requestsOverHttp = requests.get();

        view.answer(true);
        Optional<Server> serverById = servers.findServerById(server.getServerId()).join();
        Optional<Server> serverByNumericalId = servers.findServerByNumericalId(group.getName(),
                server.getNumericalId()).join();
        List<Server> serversByGroup = servers.getServersByGroup(group.getName()).join();
        Optional<Group> groupById = groups.findGroupById(group.getServerGroupId()).join();
        Optional<Group> groupByName = groups.findGroupByName(group.getName()).join();
        Optional<PersistentServer> persistentServerById = persistentServers.findPersistentServerById(
                persistentServer.getPersistentServerId()).join();
        Optional<PersistentServer> persistentServerByName = persistentServers.findPersistentServerByName(
                persistentServer.getName()).join();

        assertEquals(requestsOverHttp, requests.get(), "a view that may answer answers these lookups itself");
        assertEquals(PublicGetters.read(server), PublicGetters.read(serverById.orElseThrow()));
        assertEquals(PublicGetters.read(server), PublicGetters.read(serverByNumericalId.orElseThrow()));
        assertEquals(PublicGetters.read(serversOfGroup), PublicGetters.read(serversByGroup));
        assertEquals(PublicGetters.read(group), PublicGetters.read(groupById.orElseThrow()));
        assertEquals(PublicGetters.read(group), PublicGetters.read(groupByName.orElseThrow()));
        assertEquals(PublicGetters.read(persistentServer), PublicGetters.read(persistentServerById.orElseThrow()));
        assertEquals(PublicGetters.read(persistentServer), PublicGetters.read(persistentServerByName.orElseThrow()));
    }

    @Test
    void aReadTheCopyAnswersIsCompleteWhenTheCallReturns() {
        ServerApiImpl servers = new ServerApiImpl(options, new NoOpQueryCache(), apiClient, NEVER_RUNS, copyReads);
        GroupApiImpl groups = new GroupApiImpl(options, new NoOpQueryCache(), apiClient, NEVER_RUNS, copyReads);
        PersistentServerApiImpl persistentServers = new PersistentServerApiImpl(options, new NoOpQueryCache(),
                apiClient, NEVER_RUNS, copyReads);
        view.answer(true);

        CompletableFuture<List<Server>> serverList = servers.getAllServers(null);
        CompletableFuture<List<Group>> groupList = groups.getAllGroups(null);
        CompletableFuture<List<PersistentServer>> persistentServerList = persistentServers.getAllPersistentServers(null);

        assertTrue(serverList.isDone(), "a read over HTTP would wait for an executor that never runs");
        assertTrue(groupList.isDone());
        assertTrue(persistentServerList.isDone());
    }

    @Test
    void aCacheTimeoutReachesTheCallerUnwrappedWhenTheCopyDoesNotAnswer() throws Exception {
        QueryCacheImpl timingOutCache = new QueryCacheImpl(CacheConfig.DEFAULT, CACHE_TIMEOUT);
        GroupApiImpl groups = new GroupApiImpl(options, timingOutCache, apiClient, NEVER_RUNS, copyReads);
        view.answer(false);

        CompletableFuture<Throwable> failure = groups.getAllGroups(null).handle((listed, error) -> error);
        Throwable seen = failure.get(5, TimeUnit.SECONDS);
        timingOutCache.shutdown();

        assertInstanceOf(CloudApiException.class, seen, "the cache's own exception, as without a view");
    }

    @Test
    void aCacheTimeoutReachesTheCallerUnwrappedAfterTheReadWaited() throws Exception {
        QueryCacheImpl timingOutCache = new QueryCacheImpl(CacheConfig.DEFAULT, CACHE_TIMEOUT);
        GroupApiImpl groups = new GroupApiImpl(options, timingOutCache, apiClient, NEVER_RUNS, copyReads);
        view.awaitOwnWrite(true);

        CompletableFuture<Throwable> failure = groups.getAllGroups(null).handle((listed, error) -> error);
        boolean waited = !failure.isDone();
        view.awaitOwnWrite(false);
        Throwable seen = failure.get(5, TimeUnit.SECONDS);
        timingOutCache.shutdown();

        assertTrue(waited, "the read waits for the copy");
        assertInstanceOf(CloudApiException.class, seen, "the cache's own exception, relayed as it is");
    }

    @Test
    void aLookupOfAServerTheCopyLacksGoesOverHttp() {
        ServerApiImpl servers = serverApi(new NoOpQueryCache());
        view.answer(true);

        Optional<Server> unknown = servers.findServerById(UNKNOWN_ID).join();

        assertTrue(unknown.isEmpty());
        assertTrue(requests.get() > 0, "a server the copy lacks may be newer than the copy");
    }

    @Test
    void theCopyAnswersBeforeTheQueryCache() {
        QueryCacheImpl cache = new QueryCacheImpl(CacheConfig.DEFAULT, Duration.ofSeconds(10));
        ServerApiImpl servers = serverApi(cache);
        bodiesByPath.put("/v0/servers", EMPTY_SERVER_LIST);
        view.answer(false);
        List<Server> cachedOverHttp = servers.getAllServers(null).join();
        int requestsOverHttp = requests.get();

        view.answer(true);
        List<Server> fromCopy = servers.getAllServers(null).join();
        cache.shutdown();

        assertEquals(List.of(), cachedOverHttp);
        assertEquals(1, requestsOverHttp);
        assertEquals(6, fromCopy.size(), "the copy's servers, not the cached empty list");
        assertEquals(requestsOverHttp, requests.get());
    }

    private ServerApiImpl serverApi(QueryCache cache) {
        return new ServerApiImpl(options, cache, apiClient, CALLER_THREAD, copyReads);
    }

    private static Server firstGroupServer(List<Server> servers) {
        for (Server server : servers) {
            if (server.getServerGroupId() != null) {
                return server;
            }
        }
        throw new AssertionError("the fixture has no server in a group");
    }

    private static List<Server> serversOfGroup(List<Server> servers, Group group) {
        return servers.stream()
                .filter(server -> group.getServerGroupId().equals(server.getServerGroupId()))
                .toList();
    }

    /**
     * The fixture's group has an empty scaling mode, which the generated client refuses over HTTP and from
     * the copy alike. Both sides get one it reads, so that the group reads have an answer to compare.
     */
    private static String withReadableScalingMode(String json) {
        return json.replace("\"scaling_mode\":\"\"", "\"scaling_mode\":\"SLOTS\"");
    }

    private static NetworkState withReadableScalingModes(NetworkState snapshot) {
        NetworkState.Builder readable = snapshot.toBuilder();
        for (int index = 0; index < readable.getServerGroupsCount(); index++) {
            StateServerGroup group = readable.getServerGroups(index);
            String summary = withReadableScalingMode(group.getSummaryJson().toStringUtf8());
            StateServerGroup readableGroup = group.toBuilder()
                    .setSummaryJson(ByteString.copyFromUtf8(summary))
                    .build();
            readable.setServerGroups(index, readableGroup);
        }
        return readable.build();
    }

    private static Path fixtureDirectory() throws URISyntaxException {
        URL root = Objects.requireNonNull(ReadRoutingTest.class.getResource("/network-state/" + FIXTURE_CASE));
        return Path.of(root.toURI());
    }

    private final class FixtureDispatcher extends Dispatcher {

        @Override
        public @NotNull MockResponse dispatch(@NotNull RecordedRequest request) {
            requests.incrementAndGet();
            String body = bodiesByPath.get(request.getRequestUrl().encodedPath());
            if (body == null) {
                return new MockResponse().setResponseCode(404);
            }
            return new MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody(body);
        }
    }
}
