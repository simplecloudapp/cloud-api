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

    // NetworkContents checks only the sort field, the sort order and a limit below 1. HTTP refuses these.
    private static final Set<String> CASES_ONLY_HTTP_REFUSES = Set.of(
            "servers: a state cloud-api knows but the controller does not is refused",
            "servers: a name with a NUL character fails in Postgres",
            "persistent-servers: a serverhost id that does not parse is refused",
            "persistent-servers: a limit above 500 is refused"
    );

    private static final JsonObject CASE_FILE = readCaseFile();
    private static final Map<String, String> IDS_BY_KEY = readIdsByKey();
    private static final NetworkContents CONTENTS = buildContentsFromCaseFile();

    @ParameterizedTest(name = "{0}")
    @MethodSource("listServerCases")
    void serverListCase(String caseName, JsonObject parameters, JsonElement expected) {
        ServerQuery query = buildServerQuery(parameters);

        if (isRejected(expected)) {
            assertInvalidRequest(() -> CONTENTS.listServerSummaries(query));
            return;
        }
        List<String> ids = new ArrayList<>();
        for (ModelsServerSummary serverSummary : CONTENTS.listServerSummaries(query)) {
            ids.add(serverSummary.getServerId());
        }
        assertEquals(listExpectedIds(expected), ids);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("listGroupCases")
    void groupListCase(String caseName, JsonObject parameters, JsonElement expected) {
        GroupQuery query = buildGroupQuery(parameters);

        List<String> ids = new ArrayList<>();
        for (ModelsServerGroupSummary groupSummary : CONTENTS.listGroupSummaries(query)) {
            ids.add(groupSummary.getServerGroupId());
        }
        assertEquals(listExpectedIds(expected), ids);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("listPersistentServerCases")
    void persistentServerListCase(String caseName, JsonObject parameters, JsonElement expected) {
        PersistentServerQuery query = buildPersistentServerQuery(parameters);

        List<String> ids = new ArrayList<>();
        for (ModelsPersistentServerSummary persistentServerSummary : CONTENTS.listPersistentServerSummaries(query)) {
            ids.add(persistentServerSummary.getPersistentServerId());
        }
        assertEquals(listExpectedIds(expected), ids);
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
        List<ModelsServerSummary> allServerSummaries = CONTENTS.listServerSummaries(null);
        List<ModelsServerSummary> queriedServerSummaries = CONTENTS.listServerSummaries(ServerQuery.create());
        List<ModelsServerGroupSummary> allGroupSummaries = CONTENTS.listGroupSummaries(null);
        List<ModelsServerGroupSummary> queriedGroupSummaries = CONTENTS.listGroupSummaries(GroupQuery.create());
        List<ModelsPersistentServerSummary> allPersistentServerSummaries = CONTENTS.listPersistentServerSummaries(null);
        List<ModelsPersistentServerSummary> queriedPersistentServerSummaries = CONTENTS.listPersistentServerSummaries(PersistentServerQuery.create());

        assertThrows(UnsupportedOperationException.class, allServerSummaries::clear);
        assertThrows(UnsupportedOperationException.class, queriedServerSummaries::clear);
        assertThrows(UnsupportedOperationException.class, allGroupSummaries::clear);
        assertThrows(UnsupportedOperationException.class, queriedGroupSummaries::clear);
        assertThrows(UnsupportedOperationException.class, allPersistentServerSummaries::clear);
        assertThrows(UnsupportedOperationException.class, queriedPersistentServerSummaries::clear);
    }

    @Test
    void serverByIdMatchesOnlyTheExactIdText() {
        String id = IDS_BY_KEY.get("s1");
        String upperCaseId = id.toUpperCase();

        assertEquals(id, CONTENTS.findServerSummaryById(id).getServerId());
        assertNull(CONTENTS.findServerSummaryById(upperCaseId));
    }

    @Test
    void groupIsFoundByIdAndOnlyByTheExactName() {
        String id = IDS_BY_KEY.get("game");

        assertEquals(id, CONTENTS.findGroupSummaryById(id).getServerGroupId());
        assertEquals(id, CONTENTS.findGroupSummaryByName("game").getServerGroupId());
        assertNull(CONTENTS.findGroupSummaryByName("Game"));
    }

    @Test
    void persistentServerIsFoundByIdAndOnlyByTheExactName() {
        String id = IDS_BY_KEY.get("survival");

        assertEquals(id, CONTENTS.findPersistentServerSummaryById(id).getPersistentServerId());
        assertEquals(id, CONTENTS.findPersistentServerSummaryByName("survival").getPersistentServerId());
        assertNull(CONTENTS.findPersistentServerSummaryByName("Survival"));
    }

    static List<Arguments> listServerCases() {
        return listCasesOfRoute("servers");
    }

    static List<Arguments> listGroupCases() {
        return listCasesOfRoute("server-groups");
    }

    static List<Arguments> listPersistentServerCases() {
        return listCasesOfRoute("persistent-servers");
    }

    private static List<Arguments> listCasesOfRoute(String route) {
        List<Arguments> cases = new ArrayList<>();
        for (JsonElement element : CASE_FILE.getAsJsonArray("cases")) {
            JsonObject listCase = element.getAsJsonObject();
            String caseRoute = listCase.get("route").getAsString();
            String name = listCase.get("name").getAsString();
            String fullName = caseRoute + ": " + name;
            boolean isLeftOut = DEFAULT_ORDER_CASES.contains(fullName)
                    || CASES_JAVA_QUERIES_CANNOT_ASK.contains(fullName)
                    || CASES_ONLY_HTTP_REFUSES.contains(fullName);
            boolean isOfRoute = caseRoute.equals(route);
            if (isOfRoute && !isLeftOut) {
                cases.add(Arguments.of(fullName, listCase.getAsJsonObject("parameters"), listCase.get("expected")));
            }
        }
        return cases;
    }

    private static ServerQuery buildServerQuery(JsonObject parameters) {
        ServerQuery query = ServerQuery.create();
        for (String parameter : parameters.keySet()) {
            String value = parameters.get(parameter).getAsString();
            String[] values = value.split(",");
            switch (parameter) {
                case "server_group_id" -> query.filterByServerGroupId(values);
                case "state" -> query.filterByState(parseStates(values));
                case "serverhost_id" -> query.filterByServerhostId(value);
                case "persistent_server_id" -> query.filterByPersistentServerId(value);
                case "type" -> query.filterByServerGroupType(parseTypes(values));
                case "name" -> query.filterByServerGroupName(values);
                case "tags" -> query.filterByServerGroupTags(values);
                case "numerical_id" -> query.filterByNumericalId(parseNumbers(values));
                case "sort_by" -> query.sortBy(value);
                case "sort_order" -> query.sortOrder(value);
                default -> throw new IllegalArgumentException("ServerQuery has no " + parameter);
            }
        }
        return query;
    }

    private static GroupQuery buildGroupQuery(JsonObject parameters) {
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

    private static PersistentServerQuery buildPersistentServerQuery(JsonObject parameters) {
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

    private static ServerState[] parseStates(String[] names) {
        ServerState[] states = new ServerState[names.length];
        for (int i = 0; i < names.length; i++) {
            states[i] = ServerState.valueOf(names[i]);
        }
        return states;
    }

    private static GroupServerType[] parseTypes(String[] names) {
        GroupServerType[] types = new GroupServerType[names.length];
        for (int i = 0; i < names.length; i++) {
            types[i] = GroupServerType.valueOf(names[i]);
        }
        return types;
    }

    private static Integer[] parseNumbers(String[] texts) {
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
    private static List<String> listExpectedIds(JsonElement expected) {
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

    private static NetworkContents buildContentsFromCaseFile() {
        Map<String, JsonObject> groupJsonByKey = mapByKey(CASE_FILE.getAsJsonArray("server_groups"));
        Map<String, JsonObject> persistentServerJsonByKey = mapByKey(CASE_FILE.getAsJsonArray("persistent_servers"));
        Map<String, JsonObject> serverJsonByKey = mapByKey(CASE_FILE.getAsJsonArray("servers"));

        List<ModelsServerSummary> serverSummaries = new ArrayList<>();
        for (String key : listDefaultOrderKeys("servers")) {
            serverSummaries.add(buildServerSummary(serverJsonByKey.get(key), groupJsonByKey, persistentServerJsonByKey));
        }
        List<ModelsServerGroupSummary> groupSummaries = new ArrayList<>();
        for (String key : listDefaultOrderKeys("server-groups")) {
            groupSummaries.add(buildGroupSummary(groupJsonByKey.get(key)));
        }
        List<ModelsPersistentServerSummary> persistentServerSummaries = new ArrayList<>();
        for (String key : listDefaultOrderKeys("persistent-servers")) {
            persistentServerSummaries.add(buildPersistentServerSummary(persistentServerJsonByKey.get(key)));
        }
        return new NetworkContents(1, 1, serverSummaries, groupSummaries, persistentServerSummaries);
    }

    private static ModelsServerSummary buildServerSummary(JsonObject serverJson,
                                                          Map<String, JsonObject> groupJsonByKey,
                                                          Map<String, JsonObject> persistentServerJsonByKey) {
        ModelsServerSummary summary = new ModelsServerSummary()
                .serverId(serverJson.get("id").getAsString())
                .numericalId(serverJson.get("numerical_id").getAsInt())
                .state(ModelsServerSummary.StateEnum.fromValue(serverJson.get("state").getAsString()))
                .serverhostId(IDS_BY_KEY.get(getTextOrNull(serverJson, "serverhost")))
                .createdAt(serverJson.get("created_at").getAsString())
                .updatedAt(serverJson.get("updated_at").getAsString());
        if (serverJson.has("group")) {
            JsonObject groupJson = groupJsonByKey.get(serverJson.get("group").getAsString());
            ModelsServerGroupInfo groupInfo = new ModelsServerGroupInfo()
                    .name(groupJson.get("name").getAsString())
                    .type(groupJson.get("type").getAsString())
                    .tags(listTexts(groupJson.getAsJsonArray("tags")));
            summary.setServerGroupId(groupJson.get("id").getAsString());
            summary.setServerGroup(groupInfo);
        }
        if (serverJson.has("persistent_server")) {
            JsonObject persistentServerJson = persistentServerJsonByKey.get(serverJson.get("persistent_server").getAsString());
            ModelsPersistentServerInfo persistentServerInfo = new ModelsPersistentServerInfo()
                    .name(persistentServerJson.get("name").getAsString())
                    .type(persistentServerJson.get("type").getAsString())
                    .tags(listTexts(persistentServerJson.getAsJsonArray("tags")));
            summary.setPersistentServerId(persistentServerJson.get("id").getAsString());
            summary.setPersistentServer(persistentServerInfo);
        }
        return summary;
    }

    private static ModelsServerGroupSummary buildGroupSummary(JsonObject groupJson) {
        return new ModelsServerGroupSummary()
                .serverGroupId(groupJson.get("id").getAsString())
                .name(groupJson.get("name").getAsString())
                .type(groupJson.get("type").getAsString())
                .tags(listTexts(groupJson.getAsJsonArray("tags")))
                .createdAt(groupJson.get("created_at").getAsString());
    }

    private static ModelsPersistentServerSummary buildPersistentServerSummary(JsonObject persistentServerJson) {
        return new ModelsPersistentServerSummary()
                .persistentServerId(persistentServerJson.get("id").getAsString())
                .name(persistentServerJson.get("name").getAsString())
                .type(persistentServerJson.get("type").getAsString())
                .tags(listTexts(persistentServerJson.getAsJsonArray("tags")))
                .active(persistentServerJson.get("active").getAsBoolean())
                .serverhostId(IDS_BY_KEY.get(getTextOrNull(persistentServerJson, "serverhost")))
                .createdAt(persistentServerJson.get("created_at").getAsString());
    }

    private static List<String> listDefaultOrderKeys(String route) {
        for (JsonElement element : CASE_FILE.getAsJsonArray("cases")) {
            JsonObject listCase = element.getAsJsonObject();
            String caseRoute = listCase.get("route").getAsString();
            String name = listCase.get("name").getAsString();
            boolean isOfRoute = caseRoute.equals(route);
            boolean isDefaultOrderCase = name.startsWith("default order");
            if (isOfRoute && isDefaultOrderCase) {
                return listTexts(listCase.getAsJsonArray("expected"));
            }
        }
        throw new IllegalStateException("the case file has no default order for " + route);
    }

    private static Map<String, String> readIdsByKey() {
        Map<String, String> idsByKey = new HashMap<>();
        for (String kind : List.of("serverhosts", "server_groups", "persistent_servers", "servers")) {
            for (JsonElement element : CASE_FILE.getAsJsonArray(kind)) {
                JsonObject jsonObject = element.getAsJsonObject();
                idsByKey.put(jsonObject.get("key").getAsString(), jsonObject.get("id").getAsString());
            }
        }
        return idsByKey;
    }

    private static Map<String, JsonObject> mapByKey(JsonArray jsonObjects) {
        Map<String, JsonObject> jsonObjectsByKey = new HashMap<>();
        for (JsonElement element : jsonObjects) {
            JsonObject jsonObject = element.getAsJsonObject();
            jsonObjectsByKey.put(jsonObject.get("key").getAsString(), jsonObject);
        }
        return jsonObjectsByKey;
    }

    private static String getTextOrNull(JsonObject jsonObject, String field) {
        JsonElement value = jsonObject.get(field);
        if (value == null || value.isJsonNull()) {
            return null;
        }
        return value.getAsString();
    }

    private static List<String> listTexts(JsonArray array) {
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
