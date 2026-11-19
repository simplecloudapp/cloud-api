package app.simplecloud.api.internal.networkstate.read;

import app.simplecloud.api.group.Group;
import app.simplecloud.api.internal.group.GroupImpl;
import app.simplecloud.api.internal.group.ServerGroupSource;
import app.simplecloud.api.internal.persistentserver.PersistentServerImpl;
import app.simplecloud.api.internal.persistentserver.PersistentServerSource;
import app.simplecloud.api.internal.server.ServerImpl;
import app.simplecloud.api.internal.server.ServerSource;
import app.simplecloud.api.persistentserver.PersistentServer;
import app.simplecloud.api.server.Server;
import app.simplecloud.api.web.JSON;
import app.simplecloud.api.web.models.ModelsListPersistentServersResponse;
import app.simplecloud.api.web.models.ModelsListServerGroupsResponse;
import app.simplecloud.api.web.models.ModelsListServersResponse;
import app.simplecloud.api.web.models.ModelsPersistentServerSummary;
import app.simplecloud.api.web.models.ModelsServerGroupSummary;
import app.simplecloud.api.web.models.ModelsServerSummary;
import build.buf.gen.simplecloud.controller.v2.NetworkState;
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
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Builds each controller fixture's servers, groups and persistent servers from its snapshot and compares
 * them with what the HTTP client builds from the controller's HTTP answers for the same database state: every
 * public getter of every object, in the same order, or the same exception. The fixtures come from the
 * controller's {@code TestNetworkStateFixtures}; each case's {@code SOURCE} names the commit.
 */
class CopyFixturesTest {

    /**
     * {@code fixtureNetworkID} of the controller's fixture test.
     */
    private static final String FIXTURE_NETWORK_ID = "0d000000-0000-4000-8000-000000000001";

    @TestFactory
    List<DynamicContainer> copyObjectsEqualTheObjectsOverHttp() throws IOException, URISyntaxException {
        List<DynamicContainer> cases = new ArrayList<>();
        for (Path directory : caseDirectories()) {
            List<DynamicTest> kinds = List.of(
                    DynamicTest.dynamicTest("servers", () -> serversEqual(directory)),
                    DynamicTest.dynamicTest("server groups", () -> serverGroupsEqual(directory)),
                    DynamicTest.dynamicTest("persistent servers", () -> persistentServersEqual(directory)));
            cases.add(DynamicContainer.dynamicContainer(directory.getFileName().toString(), kinds));
        }
        assertFalse(cases.isEmpty(), "no fixture cases found");
        return cases;
    }

    private static void serversEqual(Path directory) throws IOException {
        ReadableCopy copy = readableCopyOf(directory);

        Outcome overHttp = Outcome.of(() -> serversOverHttp(directory));
        Outcome fromCopy = Outcome.of(() -> serversFromCopy(copy));

        assertEquals(overHttp, fromCopy);
    }

    private static void serverGroupsEqual(Path directory) throws IOException {
        ReadableCopy copy = readableCopyOf(directory);

        Outcome overHttp = Outcome.of(() -> serverGroupsOverHttp(directory));
        Outcome fromCopy = Outcome.of(() -> serverGroupsFromCopy(copy));

        assertEquals(overHttp, fromCopy);
    }

    private static void persistentServersEqual(Path directory) throws IOException {
        ReadableCopy copy = readableCopyOf(directory);

        Outcome overHttp = Outcome.of(() -> persistentServersOverHttp(directory));
        Outcome fromCopy = Outcome.of(() -> persistentServersFromCopy(copy));

        assertEquals(overHttp, fromCopy);
    }

    private static Object serversOverHttp(Path directory) throws IOException {
        ModelsListServersResponse answer = readHttpAnswer(directory, "servers.json", ModelsListServersResponse.class);
        List<ServerImpl> servers = servers(answer);
        return PublicGetters.read(servers);
    }

    private static Object serversFromCopy(ReadableCopy copy) {
        ServerSource source = copy.servers();
        List<Server> servers = source.servers(null).join();
        return PublicGetters.read(servers);
    }

    private static Object serverGroupsOverHttp(Path directory) throws IOException {
        ModelsListServerGroupsResponse answer = readHttpAnswer(directory, "server-groups.json",
                ModelsListServerGroupsResponse.class);
        List<GroupImpl> groups = groups(answer);
        return PublicGetters.read(groups);
    }

    private static Object serverGroupsFromCopy(ReadableCopy copy) {
        ServerGroupSource source = copy.serverGroups();
        List<Group> groups = source.serverGroups(null).join();
        return PublicGetters.read(groups);
    }

    private static Object persistentServersOverHttp(Path directory) throws IOException {
        ModelsListPersistentServersResponse answer = readHttpAnswer(directory, "persistent-servers.json",
                ModelsListPersistentServersResponse.class);
        List<PersistentServerImpl> persistentServers = persistentServers(answer);
        return PublicGetters.read(persistentServers);
    }

    private static Object persistentServersFromCopy(ReadableCopy copy) {
        PersistentServerSource source = copy.persistentServers();
        List<PersistentServer> persistentServers = source.persistentServers(null).join();
        return PublicGetters.read(persistentServers);
    }

    /**
     * Reads the way the generated client reads a response body ({@code ApiClient.deserialize}).
     */
    private static <T> T readHttpAnswer(Path directory, String file, Class<T> model) throws IOException {
        try (InputStream body = Files.newInputStream(directory.resolve(file))) {
            return JSON.deserialize(body, model);
        }
    }

    private static ReadableCopy readableCopyOf(Path directory) throws IOException {
        NetworkState snapshot = NetworkState.parseFrom(Files.readAllBytes(directory.resolve("network-state.pb")));
        return new ReadableCopy(Copies.of(snapshot), FIXTURE_NETWORK_ID);
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
