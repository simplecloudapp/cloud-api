package app.simplecloud.api.internal.networkstate.read;

import app.simplecloud.api.internal.networkstate.NetworkCopy;
import app.simplecloud.api.web.models.ModelsListPersistentServersResponse;
import app.simplecloud.api.web.models.ModelsListServerGroupsResponse;
import app.simplecloud.api.web.models.ModelsListServersResponse;
import app.simplecloud.api.web.models.ModelsPersistentServerSummary;
import app.simplecloud.api.web.models.ModelsServerGroupSummary;
import app.simplecloud.api.web.models.ModelsServerSummary;
import build.buf.gen.simplecloud.controller.v2.StatePersistentServer;
import build.buf.gen.simplecloud.controller.v2.StateServer;
import build.buf.gen.simplecloud.controller.v2.StateServerGroup;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.protobuf.ByteString;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Runs the shared filter, order and paging cases of {@code list-cases.json} against the copy's lists. Each
 * case expects the listed keys in order, {@code "rejected"} when the controller answers with an error, or
 * {@code "missed"} when it answers a lookup with nothing. The copy answers neither of the last two, so the
 * caller asks the controller.
 *
 * <p>The controller runs the same file against its real list routes on Postgres
 * ({@code TestListRoutesAnswerTheSharedListCases}); {@code SOURCE} next to the file names the commit.
 */
class ListCasesTest {

    private static final String REJECTED = "rejected";
    private static final String MISSED = "missed";

    @TestFactory
    List<DynamicTest> copyListsAnswerAsTheController() throws IOException {
        JsonObject world = readWorld();
        NetworkCopy copy = copyOf(world);
        CopyServerList serverList = new CopyServerList(world.get("network_id").getAsString());
        Map<String, String> keysById = keysById(world);

        List<DynamicTest> tests = new ArrayList<>();
        for (JsonElement element : world.getAsJsonArray("cases")) {
            JsonObject listCase = element.getAsJsonObject();
            String route = listCase.get("route").getAsString();
            String name = route + ": " + listCase.get("name").getAsString();
            JsonObject query = listCase.getAsJsonObject("parameters");
            Optional<List<String>> expected = expectedKeys(listCase.get("expected"));
            tests.add(DynamicTest.dynamicTest(name, () -> {
                Optional<List<String>> listed = listedKeys(route, query, copy, serverList, keysById);
                assertEquals(expected, listed);
            }));
        }
        return tests;
    }

    private static Optional<List<String>> expectedKeys(JsonElement expected) {
        if (expected.isJsonPrimitive()) {
            String outcome = expected.getAsString();
            if (!outcome.equals(REJECTED) && !outcome.equals(MISSED)) {
                throw new IllegalArgumentException("Unknown expected outcome " + outcome);
            }
            return Optional.empty();
        }
        return Optional.of(strings(expected.getAsJsonArray()));
    }

    private static Optional<List<String>> listedKeys(
            String route,
            JsonObject query,
            NetworkCopy copy,
            CopyServerList serverList,
            Map<String, String> keysById
    ) {
        return switch (route) {
            case "servers" -> serverList.answer(copy, ListParameters.servers(query))
                    .map(response -> serverKeys(response, keysById));
            case "server-groups" -> new CopyServerGroupList().answer(copy, ListParameters.serverGroups(query))
                    .map(response -> serverGroupKeys(response, keysById));
            case "persistent-servers" -> new CopyPersistentServerList()
                    .answer(copy, ListParameters.persistentServers(query))
                    .map(response -> persistentServerKeys(response, keysById));
            default -> throw new IllegalArgumentException("Unknown route " + route);
        };
    }

    private static List<String> serverKeys(ModelsListServersResponse response, Map<String, String> keysById) {
        List<String> keys = new ArrayList<>();
        for (ModelsServerSummary server : Objects.requireNonNullElse(response.getServers(), List.<ModelsServerSummary>of())) {
            keys.add(keysById.get(server.getServerId()));
        }
        return keys;
    }

    private static List<String> serverGroupKeys(ModelsListServerGroupsResponse response, Map<String, String> keysById) {
        List<String> keys = new ArrayList<>();
        for (ModelsServerGroupSummary group : Objects.requireNonNullElse(response.getServerGroups(),
                List.<ModelsServerGroupSummary>of())) {
            keys.add(keysById.get(group.getServerGroupId()));
        }
        return keys;
    }

    private static List<String> persistentServerKeys(
            ModelsListPersistentServersResponse response,
            Map<String, String> keysById
    ) {
        List<String> keys = new ArrayList<>();
        for (ModelsPersistentServerSummary persistentServer : response.getPersistentServers()) {
            keys.add(keysById.get(persistentServer.getPersistentServerId()));
        }
        return keys;
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
