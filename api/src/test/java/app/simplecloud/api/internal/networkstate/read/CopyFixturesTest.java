package app.simplecloud.api.internal.networkstate.read;

import app.simplecloud.api.internal.group.GroupImpl;
import app.simplecloud.api.internal.networkstate.NetworkCopy;
import app.simplecloud.api.internal.persistentserver.PersistentServerImpl;
import app.simplecloud.api.internal.server.ServerImpl;
import app.simplecloud.api.web.JSON;
import app.simplecloud.api.web.models.ModelsListPersistentServersResponse;
import app.simplecloud.api.web.models.ModelsListServerGroupsResponse;
import app.simplecloud.api.web.models.ModelsListServersResponse;
import app.simplecloud.api.web.models.ModelsPersistentServerSummary;
import app.simplecloud.api.web.models.ModelsServerGroupSummary;
import app.simplecloud.api.web.models.ModelsServerSummary;
import build.buf.gen.simplecloud.controller.v2.NetworkState;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Answers each controller fixture's lists from its snapshot and compares the answers with the controller's
 * HTTP answers for the same database state: the JSON bodies, what the HTTP client reads from them (the
 * models, or the exception), and every public getter of the objects. The fixtures come from the controller's
 * {@code TestNetworkStateFixtures}; each case's {@code SOURCE} names the commit.
 */
class CopyFixturesTest {

    /**
     * {@code fixtureNetworkID} of the controller's fixture test.
     */
    private static final String FIXTURE_NETWORK_ID = "0d000000-0000-4000-8000-000000000001";
    private static final CopyServerList SERVER_LIST = new CopyServerList(FIXTURE_NETWORK_ID);

    @TestFactory
    List<DynamicContainer> copyAnswersEqualTheControllersAnswers() throws IOException, URISyntaxException {
        List<DynamicContainer> cases = new ArrayList<>();
        for (Path directory : caseDirectories()) {
            List<DynamicTest> routes = List.of(
                    DynamicTest.dynamicTest("servers", () -> serversEqual(directory)),
                    DynamicTest.dynamicTest("server groups", () -> serverGroupsEqual(directory)),
                    DynamicTest.dynamicTest("persistent servers", () -> persistentServersEqual(directory)));
            cases.add(DynamicContainer.dynamicContainer(directory.getFileName().toString(), routes));
        }
        assertFalse(cases.isEmpty(), "no fixture cases found");
        return cases;
    }

    private static void serversEqual(Path directory) throws IOException {
        NetworkCopy copy = copyOf(directory);
        ServerListParameters noFilter = ListParameters.servers();

        Optional<JsonObject> bodyFromCopy = SERVER_LIST.responseBody(copy, noFilter);

        assertEquals(Optional.of(readBody(directory, "servers.json")), bodyFromCopy);
        Callable<ModelsListServersResponse> overHttp = () -> readHttpAnswer(directory, "servers.json",
                ModelsListServersResponse.class);
        Callable<ModelsListServersResponse> fromCopy = () -> SERVER_LIST.answer(copy, noFilter).orElseThrow();
        assertEquals(Outcome.of(overHttp), Outcome.of(fromCopy));
        assertEquals(Outcome.of(() -> PublicGetters.read(servers(overHttp.call()))),
                Outcome.of(() -> PublicGetters.read(servers(fromCopy.call()))));
    }

    private static void serverGroupsEqual(Path directory) throws IOException {
        NetworkCopy copy = copyOf(directory);
        ServerGroupListParameters noFilter = ListParameters.serverGroups();

        Optional<JsonObject> bodyFromCopy = CopyServerGroupList.responseBody(copy, noFilter);

        JsonObject bodyOverHttp = readBody(directory, "server-groups.json");
        assertEquals(Optional.of(bodyOverHttp), bodyFromCopy);
        assertEquals(stateOrders(bodyOverHttp), stateOrders(bodyFromCopy.orElseThrow()));
        Callable<ModelsListServerGroupsResponse> overHttp = () -> readHttpAnswer(directory, "server-groups.json",
                ModelsListServerGroupsResponse.class);
        Callable<ModelsListServerGroupsResponse> fromCopy = () -> new CopyServerGroupList().answer(copy, noFilter)
                .orElseThrow();
        assertEquals(Outcome.of(overHttp), Outcome.of(fromCopy));
        assertEquals(Outcome.of(() -> PublicGetters.read(groups(overHttp.call()))),
                Outcome.of(() -> PublicGetters.read(groups(fromCopy.call()))));
    }

    private static void persistentServersEqual(Path directory) throws IOException {
        NetworkCopy copy = copyOf(directory);
        PersistentServerListParameters noFilter = ListParameters.persistentServers();

        Optional<JsonObject> bodyFromCopy = CopyPersistentServerList.responseBody(copy, noFilter);

        assertEquals(Optional.of(readBody(directory, "persistent-servers.json")), bodyFromCopy);
        Callable<ModelsListPersistentServersResponse> overHttp = () -> readHttpAnswer(directory,
                "persistent-servers.json", ModelsListPersistentServersResponse.class);
        Callable<ModelsListPersistentServersResponse> fromCopy = () -> new CopyPersistentServerList()
                .answer(copy, noFilter)
                .orElseThrow();
        assertEquals(Outcome.of(overHttp), Outcome.of(fromCopy));
        assertEquals(Outcome.of(() -> PublicGetters.read(persistentServers(overHttp.call()))),
                Outcome.of(() -> PublicGetters.read(persistentServers(fromCopy.call()))));
    }

    /**
     * Lists each group's {@code servers_by_state} keys in their order, which JSON equality ignores but the
     * parsed map keeps.
     */
    private static List<List<String>> stateOrders(JsonObject body) {
        List<List<String>> orders = new ArrayList<>();
        JsonElement groups = body.get("server_groups");
        if (groups.isJsonNull()) {
            return orders;
        }
        for (JsonElement group : groups.getAsJsonArray()) {
            JsonObject serversByState = group.getAsJsonObject().getAsJsonObject("stats").getAsJsonObject("servers_by_state");
            orders.add(List.copyOf(serversByState.keySet()));
        }
        return orders;
    }

    private static JsonObject readBody(Path directory, String file) throws IOException {
        return JsonParser.parseString(Files.readString(directory.resolve(file))).getAsJsonObject();
    }

    /**
     * Reads the way the generated client reads a response body ({@code ApiClient.deserialize}).
     */
    private static <T> T readHttpAnswer(Path directory, String file, Class<T> model) throws IOException {
        try (InputStream body = Files.newInputStream(directory.resolve(file))) {
            return JSON.deserialize(body, model);
        }
    }

    private static NetworkCopy copyOf(Path directory) throws IOException {
        NetworkState snapshot = NetworkState.parseFrom(Files.readAllBytes(directory.resolve("network-state.pb")));
        return Copies.of(snapshot);
    }

    private static List<ServerImpl> servers(ModelsListServersResponse response) {
        List<ModelsServerSummary> summaries = Objects.requireNonNullElse(response.getServers(), List.of());
        return summaries.stream().map(ServerImpl::new).toList();
    }

    private static List<GroupImpl> groups(ModelsListServerGroupsResponse response) {
        List<ModelsServerGroupSummary> summaries = Objects.requireNonNullElse(response.getServerGroups(), List.of());
        return summaries.stream().map(GroupImpl::new).toList();
    }

    private static List<PersistentServerImpl> persistentServers(ModelsListPersistentServersResponse response) {
        List<ModelsPersistentServerSummary> summaries = response.getPersistentServers();
        return summaries.stream().map(PersistentServerImpl::new).toList();
    }

    private static List<Path> caseDirectories() throws IOException, URISyntaxException {
        URL root = Objects.requireNonNull(CopyFixturesTest.class.getResource("/network-state"));
        try (Stream<Path> entries = Files.list(Path.of(root.toURI()))) {
            return entries.filter(Files::isDirectory).sorted().toList();
        }
    }
}
