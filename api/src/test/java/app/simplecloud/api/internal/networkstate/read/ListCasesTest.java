package app.simplecloud.api.internal.networkstate.read;

import app.simplecloud.api.group.Group;
import app.simplecloud.api.group.GroupQuery;
import app.simplecloud.api.group.GroupServerType;
import app.simplecloud.api.internal.group.ServerGroupSource;
import app.simplecloud.api.internal.networkstate.NetworkCopy;
import app.simplecloud.api.internal.persistentserver.PersistentServerSource;
import app.simplecloud.api.internal.server.ServerSource;
import app.simplecloud.api.persistentserver.PersistentServer;
import app.simplecloud.api.persistentserver.PersistentServerQuery;
import app.simplecloud.api.server.Server;
import app.simplecloud.api.server.ServerQuery;
import app.simplecloud.api.server.ServerState;
import build.buf.gen.simplecloud.controller.v2.StatePersistentServer;
import build.buf.gen.simplecloud.controller.v2.StateServer;
import build.buf.gen.simplecloud.controller.v2.StateServerGroup;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.protobuf.ByteString;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Runs the shared filter, order and lookup cases of {@code list-cases.json} against a readable copy, each
 * with the query or lookup a plugin sends for it. Each case expects the listed keys in order, or
 * {@code "missed"} when the controller answers with nothing.
 *
 * <p>The controller runs the same cases against its real list routes on Postgres
 * ({@code TestListRoutesAnswerTheSharedListCases}); {@code SOURCE} next to the file names the commit.
 */
class ListCasesTest {

    private static final String MISSED = "missed";

    /**
     * The ids a case's query or lookup lists from a copy, in order.
     */
    @FunctionalInterface
    private interface CaseRead {

        List<String> idsIn(ReadableCopy copy);
    }

    private record TypedCase(String name, CaseRead read) {
    }

    @TestFactory
    List<DynamicTest> theCopyAnswersAsTheController() throws IOException {
        JsonObject world = readWorld();
        ReadableCopy copy = new ReadableCopy(copyOf(world), world.get("network_id").getAsString());
        Map<String, String> keysById = keysById(world);
        Map<String, TypedCase> typedCases = typedCasesByName();

        List<DynamicTest> tests = new ArrayList<>();
        Set<String> recordedNames = new HashSet<>();
        for (JsonElement element : world.getAsJsonArray("cases")) {
            JsonObject listCase = element.getAsJsonObject();
            String name = listCase.get("route").getAsString() + ": " + listCase.get("name").getAsString();
            recordedNames.add(name);
            List<String> expected = expectedKeys(listCase.get("expected"));
            TypedCase typedCase = typedCases.get(name);
            tests.add(DynamicTest.dynamicTest(name, () -> assertListed(expected, typedCase, copy, keysById)));
        }
        tests.add(DynamicTest.dynamicTest("every query has a recorded case",
                () -> assertEquals(recordedNames, typedCases.keySet())));
        return tests;
    }

    private static Map<String, TypedCase> typedCasesByName() {
        List<TypedCase> cases = List.of(
                servers("default order: groups by id bytes, then numerical id, persistent servers last by id", null),
                servers("states", ServerQuery.create().filterByState(ServerState.AVAILABLE, ServerState.INGAME)),
                servers("a name matches a group or a persistent server",
                        ServerQuery.create().filterByServerGroupName("lobby")),
                servers("a name compares exactly, and a name alone picks no server",
                        ServerQuery.create().filterByServerGroupName("Lobby")),
                servers("a name and a numerical id",
                        ServerQuery.create().filterByServerGroupName("lobby").filterByNumericalId(1)),
                servers("a numerical id nobody has is a missed lookup",
                        ServerQuery.create().filterByServerGroupName("lobby").filterByNumericalId(9)),
                serverWithId("a server id", "c0000000-0000-4000-8000-000000000201"),
                serverWithId("a server id in upper case", "C0000000-0000-4000-8000-000000000201"),
                serverWithId("an unknown server id is a missed lookup", "c0000000-0000-4000-8000-000000000299"),
                servers("several group ids", ServerQuery.create().filterByServerGroupId(
                        "0a000000-0000-4000-8000-000000000302", "70000000-0000-4000-8000-000000000303")),
                servers("a persistent server id",
                        ServerQuery.create().filterByPersistentServerId("e0000000-0000-4000-8000-000000000401")),
                servers("a serverhost id",
                        ServerQuery.create().filterByServerhostId("0d000000-0000-4000-8000-000000000011")),
                servers("a type matches a group or a persistent server",
                        ServerQuery.create().filterByServerGroupType(GroupServerType.PROXY)),
                servers("a tag of a group or a persistent server", ServerQuery.create().filterByServerGroupTags("hub")),
                servers("one tag in common is enough",
                        ServerQuery.create().filterByServerGroupTags("minigame", "nothing")),
                servers("filters combine", ServerQuery.create()
                        .filterByState(ServerState.AVAILABLE)
                        .filterByServerGroupType(GroupServerType.SERVER)
                        .filterByServerGroupTags("main")),
                servers("numerical id descending, ties in the default order",
                        ServerQuery.create().sortBy("numerical_id").sortOrder("desc")),
                servers("created_at ascending, ties in the default order", ServerQuery.create().sortBy("created_at")),
                servers("updated_at descending", ServerQuery.create().sortBy("updated_at").sortOrder("desc")),
                servers("state, trimmed, by bytes", ServerQuery.create().sortBy(" state ")),
                servers("a sort order alone keeps the default order", ServerQuery.create().sortOrder("desc")),
                serverGroups("default order: created_at descending, then id bytes", null),
                serverGroupWithName("a name", "lobby"),
                serverGroupWithName("a name compares exactly, and a missed name is a missed lookup", "Lobby"),
                serverGroupWithId("an id", "f0000000-0000-4000-8000-000000000301"),
                serverGroupWithId("an unknown id is a missed lookup", "f0000000-0000-4000-8000-000000000399"),
                serverGroups("a type", GroupQuery.create().filterByType(GroupServerType.SERVER)),
                serverGroups("a tag", GroupQuery.create().filterByTag("hub")),
                serverGroups("a tag nobody has", GroupQuery.create().filterByTag("nothing")),
                serverGroups("a limit", GroupQuery.create().limit(2)),
                persistentServers("default order: created_at descending, then id bytes", null),
                persistentServers("inactive", PersistentServerQuery.create().filterByActive(false)),
                persistentServers("active", PersistentServerQuery.create().filterByActive(true)),
                persistentServers("a serverhost id",
                        PersistentServerQuery.create().filterByServerhostId("0d000000-0000-4000-8000-000000000011")),
                persistentServers("tags", PersistentServerQuery.create().filterByTags("hub", "main")),
                persistentServerWithName("a name", "survival"),
                persistentServerWithName("a missed name is a missed lookup", "missing"),
                persistentServerWithId("an id", "10000000-0000-4000-8000-000000000402"),
                persistentServers("a tag nobody has", PersistentServerQuery.create().limit(1).filterByTags("nothing")));
        Map<String, TypedCase> byName = new LinkedHashMap<>();
        for (TypedCase typedCase : cases) {
            byName.put(typedCase.name(), typedCase);
        }
        return byName;
    }

    private static void assertListed(
            List<String> expected,
            @Nullable TypedCase typedCase,
            ReadableCopy copy,
            Map<String, String> keysById
    ) {
        assertNotNull(typedCase, "no query for the recorded case");
        List<String> ids = typedCase.read().idsIn(copy);
        List<String> listed = keysOf(ids, keysById);
        assertEquals(expected, listed);
    }

    private static TypedCase servers(String name, @Nullable ServerQuery query) {
        return new TypedCase("servers: " + name, copy -> serverIds(copy, query));
    }

    private static TypedCase serverWithId(String name, String id) {
        return new TypedCase("servers: " + name, copy -> serverIdWithId(copy, id));
    }

    private static TypedCase serverGroups(String name, @Nullable GroupQuery query) {
        return new TypedCase("server-groups: " + name, copy -> groupIds(copy, query));
    }

    private static TypedCase serverGroupWithId(String name, String id) {
        return new TypedCase("server-groups: " + name, copy -> groupIdWithId(copy, id));
    }

    private static TypedCase serverGroupWithName(String name, String groupName) {
        return new TypedCase("server-groups: " + name, copy -> groupIdWithName(copy, groupName));
    }

    private static TypedCase persistentServers(String name, @Nullable PersistentServerQuery query) {
        return new TypedCase("persistent-servers: " + name, copy -> persistentServerIds(copy, query));
    }

    private static TypedCase persistentServerWithId(String name, String id) {
        return new TypedCase("persistent-servers: " + name, copy -> persistentServerIdWithId(copy, id));
    }

    private static TypedCase persistentServerWithName(String name, String persistentServerName) {
        return new TypedCase("persistent-servers: " + name,
                copy -> persistentServerIdWithName(copy, persistentServerName));
    }

    private static List<String> serverIds(ReadableCopy copy, @Nullable ServerQuery query) {
        ServerSource source = copy.servers();
        List<Server> servers = source.servers(query).join();
        return serverIds(servers);
    }

    private static List<String> serverIdWithId(ReadableCopy copy, String id) {
        ServerSource source = copy.servers();
        Server server = source.serverWithId(id).join();
        return serverIds(listOf(server));
    }

    private static List<String> groupIds(ReadableCopy copy, @Nullable GroupQuery query) {
        ServerGroupSource source = copy.serverGroups();
        List<Group> groups = source.serverGroups(query).join();
        return groupIds(groups);
    }

    private static List<String> groupIdWithId(ReadableCopy copy, String id) {
        ServerGroupSource source = copy.serverGroups();
        Group group = source.serverGroupWithId(id).join();
        return groupIds(listOf(group));
    }

    private static List<String> groupIdWithName(ReadableCopy copy, String name) {
        ServerGroupSource source = copy.serverGroups();
        Group group = source.serverGroupWithName(name).join();
        return groupIds(listOf(group));
    }

    private static List<String> persistentServerIds(ReadableCopy copy, @Nullable PersistentServerQuery query) {
        PersistentServerSource source = copy.persistentServers();
        List<PersistentServer> persistentServers = source.persistentServers(query).join();
        return persistentServerIds(persistentServers);
    }

    private static List<String> persistentServerIdWithId(ReadableCopy copy, String id) {
        PersistentServerSource source = copy.persistentServers();
        PersistentServer persistentServer = source.persistentServerWithId(id).join();
        return persistentServerIds(listOf(persistentServer));
    }

    private static List<String> persistentServerIdWithName(ReadableCopy copy, String name) {
        PersistentServerSource source = copy.persistentServers();
        PersistentServer persistentServer = source.persistentServerWithName(name).join();
        return persistentServerIds(listOf(persistentServer));
    }

    private static <T> List<T> listOf(@Nullable T found) {
        return found == null ? List.of() : List.of(found);
    }

    private static List<String> serverIds(List<Server> servers) {
        return servers.stream().map(Server::getServerId).toList();
    }

    private static List<String> groupIds(List<Group> groups) {
        return groups.stream().map(Group::getServerGroupId).toList();
    }

    private static List<String> persistentServerIds(List<PersistentServer> persistentServers) {
        return persistentServers.stream().map(PersistentServer::getPersistentServerId).toList();
    }

    private static List<String> expectedKeys(JsonElement expected) {
        if (expected.isJsonPrimitive()) {
            String outcome = expected.getAsString();
            if (!outcome.equals(MISSED)) {
                throw new IllegalArgumentException("Unknown expected outcome " + outcome);
            }
            return List.of();
        }
        return strings(expected.getAsJsonArray());
    }

    private static List<String> keysOf(List<String> ids, Map<String, String> keysById) {
        return ids.stream().map(keysById::get).toList();
    }

    private static JsonObject readWorld() throws IOException {
        try (InputStream stream = Objects.requireNonNull(
                ListCasesTest.class.getResourceAsStream("/network-state/list-cases.json"))) {
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    /**
     * Renders the world as the controller renders items, with only the members the filters and orders read.
     */
    private static NetworkCopy copyOf(JsonObject world) {
        Map<String, String> idsByKey = idsByKey(world);
        Map<ByteString, StateServerGroup> groups = new HashMap<>();
        for (JsonElement element : world.getAsJsonArray("server_groups")) {
            StateServerGroup group = group(element.getAsJsonObject());
            groups.put(group.getId(), group);
        }
        Map<ByteString, StatePersistentServer> persistentServers = new HashMap<>();
        for (JsonElement element : world.getAsJsonArray("persistent_servers")) {
            StatePersistentServer persistentServer = persistentServer(element.getAsJsonObject(), idsByKey);
            persistentServers.put(persistentServer.getId(), persistentServer);
        }
        Map<ByteString, StateServer> servers = new HashMap<>();
        for (JsonElement element : world.getAsJsonArray("servers")) {
            StateServer server = server(element.getAsJsonObject(), idsByKey);
            servers.put(server.getId(), server);
        }
        return new NetworkCopy(1, 1, servers, groups, persistentServers, Map.of(), Map.of());
    }

    private static StateServerGroup group(JsonObject group) {
        String id = group.get("id").getAsString();
        JsonObject info = parentJson(group);
        info.addProperty("id", id);
        JsonObject summary = parentJson(group);
        summary.addProperty("server_group_id", id);
        summary.addProperty("active", true);
        return StateServerGroup.newBuilder()
                .setId(id(id))
                .setInfoJson(ByteString.copyFromUtf8(info.toString()))
                .setSummaryJson(ByteString.copyFromUtf8(summary.toString()))
                .build();
    }

    private static StatePersistentServer persistentServer(JsonObject persistentServer, Map<String, String> idsByKey) {
        String id = persistentServer.get("id").getAsString();
        JsonObject shared = parentJson(persistentServer);
        shared.addProperty("active", persistentServer.get("active").getAsBoolean());
        if (!persistentServer.get("serverhost").isJsonNull()) {
            shared.addProperty("serverhost_id", idsByKey.get(persistentServer.get("serverhost").getAsString()));
        }
        JsonObject info = shared.deepCopy();
        info.addProperty("id", id);
        JsonObject summary = shared.deepCopy();
        summary.addProperty("persistent_server_id", id);
        return StatePersistentServer.newBuilder()
                .setId(id(id))
                .setInfoJson(ByteString.copyFromUtf8(info.toString()))
                .setSummaryJson(ByteString.copyFromUtf8(summary.toString()))
                .build();
    }

    private static JsonObject parentJson(JsonObject parent) {
        JsonObject source = new JsonObject();
        source.addProperty("type", "image");
        source.addProperty("image", "x:latest");
        JsonObject json = new JsonObject();
        json.addProperty("name", parent.get("name").getAsString());
        json.addProperty("type", parent.get("type").getAsString());
        json.add("tags", parent.get("tags"));
        json.add("source", source);
        json.addProperty("created_at", parent.get("created_at").getAsString());
        json.addProperty("updated_at", parent.get("created_at").getAsString());
        return json;
    }

    private static StateServer server(JsonObject server, Map<String, String> idsByKey) {
        StateServer.Builder row = StateServer.newBuilder()
                .setId(id(server.get("id").getAsString()))
                .setNumericalId(server.get("numerical_id").getAsInt())
                .setState(server.get("state").getAsString())
                .setPropertiesJson(ByteString.copyFromUtf8("{}"))
                .setCreatedAtMicros(micros(server.get("created_at").getAsString()))
                .setUpdatedAtMicros(micros(server.get("updated_at").getAsString()));
        if (server.has("group")) {
            row.setServerGroupId(id(idsByKey.get(server.get("group").getAsString())));
        }
        if (server.has("persistent_server")) {
            row.setPersistentServerId(id(idsByKey.get(server.get("persistent_server").getAsString())));
        }
        if (!server.get("serverhost").isJsonNull()) {
            row.setServerhostId(id(idsByKey.get(server.get("serverhost").getAsString())));
        }
        return row.build();
    }

    private static Map<String, String> idsByKey(JsonObject world) {
        Map<String, String> idsByKey = new HashMap<>();
        for (String list : List.of("serverhosts", "server_groups", "persistent_servers", "servers")) {
            for (JsonElement element : world.getAsJsonArray(list)) {
                JsonObject item = element.getAsJsonObject();
                idsByKey.put(item.get("key").getAsString(), item.get("id").getAsString());
            }
        }
        return idsByKey;
    }

    private static Map<String, String> keysById(JsonObject world) {
        Map<String, String> keysById = new HashMap<>();
        for (Map.Entry<String, String> entry : idsByKey(world).entrySet()) {
            keysById.put(entry.getValue(), entry.getKey());
        }
        return keysById;
    }

    private static List<String> strings(JsonArray array) {
        List<String> strings = new ArrayList<>();
        for (JsonElement element : array) {
            strings.add(element.getAsString());
        }
        return strings;
    }

    private static ByteString id(String uuid) {
        return ByteString.fromHex(uuid.replace("-", ""));
    }

    private static long micros(String timestamp) {
        return ChronoUnit.MICROS.between(Instant.EPOCH, Instant.parse(timestamp));
    }
}
