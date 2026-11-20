package app.simplecloud.api.internal.networkcopy;

import app.simplecloud.api.CloudApiException;
import app.simplecloud.api.group.GroupQuery;
import app.simplecloud.api.group.GroupServerType;
import app.simplecloud.api.persistentserver.PersistentServerQuery;
import app.simplecloud.api.server.ServerQuery;
import app.simplecloud.api.server.ServerState;
import app.simplecloud.api.web.models.ModelsPersistentServerInfo;
import app.simplecloud.api.web.models.ModelsPersistentServerSummary;
import app.simplecloud.api.web.models.ModelsServerGroupInfo;
import app.simplecloud.api.web.models.ModelsServerGroupSummary;
import app.simplecloud.api.web.models.ModelsServerSummary;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Runs the controller's shared list cases against contents built from the case file.
 */
class NetworkContentsTest {

    // NetworkContents gets its lists in the default order, so these cases would only check the test itself.
    private static final Set<String> DEFAULT_ORDER_CASES = Set.of(
            "servers: default order: groups by id bytes, then numerical id, persistent servers last by id",
            "server-groups: default order: created_at descending, then id bytes",
            "persistent-servers: default order: created_at descending, then id bytes"
    );

    // The Java query types cannot ask these: they have no id, name, offset or server limit filter,
    // states and types are enum values, and numerical ids are ints.
    private static final Set<String> CASES_JAVA_QUERIES_CANNOT_ASK = Set.of(
            "servers: an empty or blank parameter does not filter",
            "servers: states with spaces and empty parts",
            "servers: a state between a no-break space and an ideographic space, which Go trims",
            "servers: a lowercase state is refused",
            "servers: a numerical id with a non-ASCII digit is refused",
            "servers: a numerical id beyond int32 is refused",
            "servers: a server id",
            "servers: a server id in upper case",
            "servers: a server id without dashes",
            "servers: a server id with other characters where the dashes go",
            "servers: a server id one character short is refused",
            "servers: an unknown server id is a missed lookup",
            "servers: a server id beyond the page is a missed lookup",
            "servers: a lowercase type is refused",
            "servers: a limit",
            "servers: a limit and an offset",
            "servers: an offset past the end",
            "servers: the largest limit",
            "servers: an offset without a limit is refused",
            "servers: a limit of 0 is refused",
            "servers: a limit above 500 is refused",
            "servers: a negative offset is refused",
            "server-groups: a name",
            "server-groups: several names",
            "server-groups: a name compares exactly, and a missed name is a missed lookup",
            "server-groups: an id",
            "server-groups: an unknown id is a missed lookup",
            "server-groups: an id that does not parse is refused",
            "server-groups: a lowercase type is refused",
            "server-groups: a limit and an offset",
            "server-groups: an offset past the end",
            "server-groups: an offset without a limit is refused",
            "persistent-servers: a name",
            "persistent-servers: a missed name is a missed lookup",
            "persistent-servers: an id",
            "persistent-servers: a limit and an offset"
    );

    // The copy checks only the sort field, the sort order and a limit below 1. HTTP refuses these.
    private static final Set<String> VALUES_THE_COPY_DOES_NOT_CHECK = Set.of(
            "servers: a state cloud-api knows but the controller does not is refused",
            "servers: a name with a NUL character fails in Postgres",
            "persistent-servers: a serverhost id that does not parse is refused",
            "persistent-servers: a limit above 500 is refused"
    );

    private static final JsonObject CASE_FILE = readCaseFile();
    private static final Map<String, String> IDS_BY_KEY = idsByKey();
    private static final NetworkContents CONTENTS = contentsFromCaseFile();

    @ParameterizedTest(name = "{0}")
    @MethodSource("serverCases")
    void serverListCase(String name, JsonObject parameters, JsonElement expected) {
        ServerQuery query = serverQuery(parameters);

        if (isRejected(expected)) {
            assertInvalidRequest(() -> CONTENTS.listServerSummaries(query));
            return;
        }
        List<String> ids = new ArrayList<>();
        for (ModelsServerSummary server : CONTENTS.listServerSummaries(query)) {
            ids.add(server.getServerId());
        }
        assertEquals(expectedIds(expected), ids);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("groupCases")
    void groupListCase(String name, JsonObject parameters, JsonElement expected) {
        GroupQuery query = groupQuery(parameters);

        List<String> ids = new ArrayList<>();
        for (ModelsServerGroupSummary group : CONTENTS.listGroupSummaries(query)) {
            ids.add(group.getServerGroupId());
        }
        assertEquals(expectedIds(expected), ids);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("persistentServerCases")
    void persistentServerListCase(String name, JsonObject parameters, JsonElement expected) {
        PersistentServerQuery query = persistentServerQuery(parameters);

        List<String> ids = new ArrayList<>();
        for (ModelsPersistentServerSummary persistentServer : CONTENTS.listPersistentServerSummaries(query)) {
            ids.add(persistentServer.getPersistentServerId());
        }
        assertEquals(expectedIds(expected), ids);
    }

    @Test
    void groupLimitBelowOneIsRefused() {
        GroupQuery query = GroupQuery.create().limit(0);

        assertInvalidRequest(() -> CONTENTS.listGroupSummaries(query));
    }

    @Test
    void persistentServerLimitBelowOneIsRefused() {
        PersistentServerQuery query = PersistentServerQuery.create().limit(0);

        assertInvalidRequest(() -> CONTENTS.listPersistentServerSummaries(query));
    }

    @Test
    void listsCannotBeChangedWithOrWithoutAQuery() {
        List<ModelsServerSummary> allServers = CONTENTS.listServerSummaries(null);
        List<ModelsServerSummary> queriedServers = CONTENTS.listServerSummaries(ServerQuery.create());
        List<ModelsServerGroupSummary> allGroups = CONTENTS.listGroupSummaries(null);
        List<ModelsServerGroupSummary> queriedGroups = CONTENTS.listGroupSummaries(GroupQuery.create());
        List<ModelsPersistentServerSummary> allPersistentServers = CONTENTS.listPersistentServerSummaries(null);
        List<ModelsPersistentServerSummary> queriedPersistentServers = CONTENTS.listPersistentServerSummaries(PersistentServerQuery.create());

        assertThrows(UnsupportedOperationException.class, allServers::clear);
        assertThrows(UnsupportedOperationException.class, queriedServers::clear);
        assertThrows(UnsupportedOperationException.class, allGroups::clear);
        assertThrows(UnsupportedOperationException.class, queriedGroups::clear);
        assertThrows(UnsupportedOperationException.class, allPersistentServers::clear);
        assertThrows(UnsupportedOperationException.class, queriedPersistentServers::clear);
    }

    @Test
    void serverByIdMatchesOnlyTheExactIdText() {
        String id = IDS_BY_KEY.get("s1");
        String upperCaseId = id.toUpperCase();

        assertEquals(id, CONTENTS.findServerSummaryById(id).getServerId());
        assertNull(CONTENTS.findServerSummaryById(upperCaseId));
    }

    @Test
    void groupLookupsByIdAndExactName() {
        String id = IDS_BY_KEY.get("game");

        assertEquals(id, CONTENTS.findGroupSummaryById(id).getServerGroupId());
        assertEquals(id, CONTENTS.findGroupSummaryByName("game").getServerGroupId());
        assertNull(CONTENTS.findGroupSummaryByName("Game"));
    }

    @Test
    void persistentServerLookupsByIdAndExactName() {
        String id = IDS_BY_KEY.get("survival");

        assertEquals(id, CONTENTS.findPersistentServerSummaryById(id).getPersistentServerId());
        assertEquals(id, CONTENTS.findPersistentServerSummaryByName("survival").getPersistentServerId());
        assertNull(CONTENTS.findPersistentServerSummaryByName("Survival"));
    }

    static List<Arguments> serverCases() {
        return casesOfRoute("servers");
    }

    static List<Arguments> groupCases() {
        return casesOfRoute("server-groups");
    }

    static List<Arguments> persistentServerCases() {
        return casesOfRoute("persistent-servers");
    }

    private static List<Arguments> casesOfRoute(String route) {
        List<Arguments> cases = new ArrayList<>();
        for (JsonElement element : CASE_FILE.getAsJsonArray("cases")) {
            JsonObject listCase = element.getAsJsonObject();
            String fullName = listCase.get("route").getAsString() + ": " + listCase.get("name").getAsString();
            boolean leftOut = DEFAULT_ORDER_CASES.contains(fullName)
                    || CASES_JAVA_QUERIES_CANNOT_ASK.contains(fullName)
                    || VALUES_THE_COPY_DOES_NOT_CHECK.contains(fullName);
            boolean ofRoute = listCase.get("route").getAsString().equals(route);
            if (ofRoute && !leftOut) {
                cases.add(Arguments.of(fullName, listCase.getAsJsonObject("parameters"), listCase.get("expected")));
            }
        }
        return cases;
    }

    private static ServerQuery serverQuery(JsonObject parameters) {
        ServerQuery query = ServerQuery.create();
        for (String parameter : parameters.keySet()) {
            String value = parameters.get(parameter).getAsString();
            String[] values = value.split(",");
            switch (parameter) {
                case "server_group_id" -> query.filterByServerGroupId(values);
                case "state" -> query.filterByState(states(values));
                case "serverhost_id" -> query.filterByServerhostId(value);
                case "persistent_server_id" -> query.filterByPersistentServerId(value);
                case "type" -> query.filterByServerGroupType(types(values));
                case "name" -> query.filterByServerGroupName(values);
                case "tags" -> query.filterByServerGroupTags(values);
                case "numerical_id" -> query.filterByNumericalId(numbers(values));
                case "sort_by" -> query.sortBy(value);
                case "sort_order" -> query.sortOrder(value);
                default -> throw new IllegalArgumentException("ServerQuery has no " + parameter);
            }
        }
        return query;
    }

    private static GroupQuery groupQuery(JsonObject parameters) {
        GroupQuery query = GroupQuery.create();
        for (String parameter : parameters.keySet()) {
            String value = parameters.get(parameter).getAsString();
            switch (parameter) {
                case "type" -> query.filterByType(GroupServerType.valueOf(value));
                case "tags" -> query.filterByTag(value);
                case "limit" -> query.limit(Integer.valueOf(value));
                default -> throw new IllegalArgumentException("GroupQuery has no " + parameter);
            }
        }
        return query;
    }

    private static PersistentServerQuery persistentServerQuery(JsonObject parameters) {
        PersistentServerQuery query = PersistentServerQuery.create();
        for (String parameter : parameters.keySet()) {
            String value = parameters.get(parameter).getAsString();
            switch (parameter) {
                case "active" -> query.filterByActive(Boolean.parseBoolean(value));
                case "serverhost_id" -> query.filterByServerhostId(value);
                case "tags" -> query.filterByTags(value.split(","));
                case "limit" -> query.limit(Integer.parseInt(value));
                default -> throw new IllegalArgumentException("PersistentServerQuery has no " + parameter);
            }
        }
        return query;
    }

    private static ServerState[] states(String[] names) {
        ServerState[] states = new ServerState[names.length];
        for (int i = 0; i < names.length; i++) {
            states[i] = ServerState.valueOf(names[i]);
        }
        return states;
    }

    private static GroupServerType[] types(String[] names) {
        GroupServerType[] types = new GroupServerType[names.length];
        for (int i = 0; i < names.length; i++) {
            types[i] = GroupServerType.valueOf(names[i]);
        }
        return types;
    }

    private static Integer[] numbers(String[] texts) {
        Integer[] numbers = new Integer[texts.length];
        for (int i = 0; i < texts.length; i++) {
            numbers[i] = Integer.valueOf(texts[i]);
        }
        return numbers;
    }

    private static boolean isRejected(JsonElement expected) {
        return expected.isJsonPrimitive() && expected.getAsString().equals("rejected");
    }

    // A missed lookup is an empty list here; the controller only counts it differently.
    private static List<String> expectedIds(JsonElement expected) {
        List<String> ids = new ArrayList<>();
        if (!expected.isJsonArray()) {
            return ids;
        }
        for (JsonElement key : expected.getAsJsonArray()) {
            ids.add(IDS_BY_KEY.get(key.getAsString()));
        }
        return ids;
    }

    private static void assertInvalidRequest(Runnable read) {
        CloudApiException refusal = assertThrows(CloudApiException.class, read::run);
        assertEquals(CloudApiException.Reason.INVALID_REQUEST, refusal.getReason());
    }

    private static NetworkContents contentsFromCaseFile() {
        Map<String, JsonObject> groupsByKey = byKey(CASE_FILE.getAsJsonArray("server_groups"));
        Map<String, JsonObject> persistentServersByKey = byKey(CASE_FILE.getAsJsonArray("persistent_servers"));
        Map<String, JsonObject> serversByKey = byKey(CASE_FILE.getAsJsonArray("servers"));

        List<ModelsServerSummary> servers = new ArrayList<>();
        for (String key : defaultOrder("servers")) {
            servers.add(serverSummary(serversByKey.get(key), groupsByKey, persistentServersByKey));
        }
        List<ModelsServerGroupSummary> groups = new ArrayList<>();
        for (String key : defaultOrder("server-groups")) {
            groups.add(groupSummary(groupsByKey.get(key)));
        }
        List<ModelsPersistentServerSummary> persistentServers = new ArrayList<>();
        for (String key : defaultOrder("persistent-servers")) {
            persistentServers.add(persistentServerSummary(persistentServersByKey.get(key)));
        }
        return new NetworkContents(1, 1, servers, groups, persistentServers);
    }

    private static ModelsServerSummary serverSummary(JsonObject server,
                                                     Map<String, JsonObject> groupsByKey,
                                                     Map<String, JsonObject> persistentServersByKey) {
        ModelsServerSummary summary = new ModelsServerSummary()
                .serverId(server.get("id").getAsString())
                .numericalId(server.get("numerical_id").getAsInt())
                .state(ModelsServerSummary.StateEnum.fromValue(server.get("state").getAsString()))
                .serverhostId(IDS_BY_KEY.get(textOrNull(server, "serverhost")))
                .createdAt(server.get("created_at").getAsString())
                .updatedAt(server.get("updated_at").getAsString());
        if (server.has("group")) {
            JsonObject group = groupsByKey.get(server.get("group").getAsString());
            ModelsServerGroupInfo info = new ModelsServerGroupInfo()
                    .name(group.get("name").getAsString())
                    .type(group.get("type").getAsString())
                    .tags(texts(group.getAsJsonArray("tags")));
            summary.serverGroupId(group.get("id").getAsString()).serverGroup(info);
        }
        if (server.has("persistent_server")) {
            JsonObject persistentServer = persistentServersByKey.get(server.get("persistent_server").getAsString());
            ModelsPersistentServerInfo info = new ModelsPersistentServerInfo()
                    .name(persistentServer.get("name").getAsString())
                    .type(persistentServer.get("type").getAsString())
                    .tags(texts(persistentServer.getAsJsonArray("tags")));
            summary.persistentServerId(persistentServer.get("id").getAsString()).persistentServer(info);
        }
        return summary;
    }

    private static ModelsServerGroupSummary groupSummary(JsonObject group) {
        return new ModelsServerGroupSummary()
                .serverGroupId(group.get("id").getAsString())
                .name(group.get("name").getAsString())
                .type(group.get("type").getAsString())
                .tags(texts(group.getAsJsonArray("tags")))
                .createdAt(group.get("created_at").getAsString());
    }

    private static ModelsPersistentServerSummary persistentServerSummary(JsonObject persistentServer) {
        return new ModelsPersistentServerSummary()
                .persistentServerId(persistentServer.get("id").getAsString())
                .name(persistentServer.get("name").getAsString())
                .type(persistentServer.get("type").getAsString())
                .tags(texts(persistentServer.getAsJsonArray("tags")))
                .active(persistentServer.get("active").getAsBoolean())
                .serverhostId(IDS_BY_KEY.get(textOrNull(persistentServer, "serverhost")))
                .createdAt(persistentServer.get("created_at").getAsString());
    }

    private static List<String> defaultOrder(String route) {
        for (JsonElement element : CASE_FILE.getAsJsonArray("cases")) {
            JsonObject listCase = element.getAsJsonObject();
            boolean ofRoute = listCase.get("route").getAsString().equals(route);
            boolean defaultOrder = listCase.get("name").getAsString().startsWith("default order");
            if (ofRoute && defaultOrder) {
                return texts(listCase.getAsJsonArray("expected"));
            }
        }
        throw new IllegalStateException("the case file has no default order for " + route);
    }

    private static Map<String, String> idsByKey() {
        Map<String, String> ids = new HashMap<>();
        for (String kind : List.of("serverhosts", "server_groups", "persistent_servers", "servers")) {
            for (JsonElement element : CASE_FILE.getAsJsonArray(kind)) {
                JsonObject item = element.getAsJsonObject();
                ids.put(item.get("key").getAsString(), item.get("id").getAsString());
            }
        }
        return ids;
    }

    private static Map<String, JsonObject> byKey(JsonArray items) {
        Map<String, JsonObject> itemsByKey = new HashMap<>();
        for (JsonElement element : items) {
            JsonObject item = element.getAsJsonObject();
            itemsByKey.put(item.get("key").getAsString(), item);
        }
        return itemsByKey;
    }

    private static String textOrNull(JsonObject item, String field) {
        JsonElement value = item.get(field);
        if (value == null || value.isJsonNull()) {
            return null;
        }
        return value.getAsString();
    }

    private static List<String> texts(JsonArray array) {
        List<String> texts = new ArrayList<>();
        for (JsonElement element : array) {
            texts.add(element.getAsString());
        }
        return texts;
    }

    private static JsonObject readCaseFile() {
        try (InputStream stream = NetworkContentsTest.class.getResourceAsStream("/network-state/list-cases.json")) {
            String text = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            return JsonParser.parseString(text).getAsJsonObject();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
