package app.simplecloud.api.internal.networkcopy;

import app.simplecloud.api.web.JSON;
import app.simplecloud.api.web.models.ModelsBlueprintInfo;
import app.simplecloud.api.web.models.ModelsListPersistentServersResponse;
import app.simplecloud.api.web.models.ModelsListServerGroupsResponse;
import app.simplecloud.api.web.models.ModelsListServersResponse;
import app.simplecloud.api.web.models.ModelsPersistentServerSummary;
import app.simplecloud.api.web.models.ModelsServerGroupInfo;
import app.simplecloud.api.web.models.ModelsServerGroupSummary;
import app.simplecloud.api.web.models.ModelsServerSummary;
import build.buf.gen.simplecloud.controller.v2.NetworkState;
import build.buf.gen.simplecloud.controller.v2.StateBatch;
import build.buf.gen.simplecloud.controller.v2.StatePersistentServer;
import build.buf.gen.simplecloud.controller.v2.StateServer;
import build.buf.gen.simplecloud.controller.v2.StateServerGroup;
import build.buf.gen.simplecloud.controller.v2.StateServerMetrics;
import com.google.protobuf.ByteString;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NetworkCopyTest {

    private static final String NETWORK_ID = "0d000000-0000-4000-8000-000000000001";
    private static final String GROUP_A_ID = "0a000000-0000-4000-8000-000000000301";
    private static final String GROUP_B_ID = "0b000000-0000-4000-8000-000000000302";
    private static final String PERSISTENT_SERVER_ID = "0c000000-0000-4000-8000-000000000401";

    // The only fixture without groups, so neither group statistics nor blueprints need to be removed.
    @Test
    void snapshotListsEqualTheControllerLists() throws IOException {
        NetworkState snapshot = NetworkState.parseFrom(readFixtureBytes("persistent-servers", "network-state.pb"));
        String serverListJson = readFixtureText("persistent-servers", "servers.json");
        String groupListJson = readFixtureText("persistent-servers", "server-groups.json");
        String persistentServerListJson = readFixtureText("persistent-servers", "persistent-servers.json");
        ModelsListServersResponse expectedServerListResponse = JSON.deserialize(serverListJson, ModelsListServersResponse.class);
        ModelsListServerGroupsResponse expectedGroupListResponse = JSON.deserialize(groupListJson, ModelsListServerGroupsResponse.class);
        ModelsListPersistentServersResponse expectedPersistentServerListResponse =
                JSON.deserialize(persistentServerListJson, ModelsListPersistentServersResponse.class);

        NetworkContents contents = NetworkCopy.createFromSnapshot(snapshot, NETWORK_ID).buildContents();

        assertEquals(expectedServerListResponse.getServers(), contents.listServerSummaries(null));
        assertNull(expectedGroupListResponse.getServerGroups());
        assertEquals(List.of(), contents.listGroupSummaries(null));
        assertEquals(expectedPersistentServerListResponse.getPersistentServers(), contents.listPersistentServerSummaries(null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"blueprint-sources", "nulls-and-images", "properties", "several-groups", "timestamps"})
    void snapshotListsEqualTheControllerListsWithoutTheExpectedDifferences(String fixtureCase) throws IOException {
        NetworkState recordedSnapshot = NetworkState.parseFrom(readFixtureBytes(fixtureCase, "network-state.pb"));
        NetworkState snapshot = replaceEmptyScalingModeWithSlots(recordedSnapshot);
        String serverListJson = readFixtureText(fixtureCase, "servers.json");
        String groupListJson = replaceEmptyScalingModeWithSlots(readFixtureText(fixtureCase, "server-groups.json"));
        String persistentServerListJson = readFixtureText(fixtureCase, "persistent-servers.json");
        ModelsListServersResponse recordedServerListResponse = JSON.deserialize(serverListJson, ModelsListServersResponse.class);
        ModelsListServerGroupsResponse recordedGroupListResponse = JSON.deserialize(groupListJson, ModelsListServerGroupsResponse.class);
        ModelsListPersistentServersResponse recordedPersistentServerListResponse =
                JSON.deserialize(persistentServerListJson, ModelsListPersistentServersResponse.class);
        List<ModelsServerSummary> expectedServerSummaries = recordedServerListResponse.getServers();
        List<ModelsServerGroupSummary> expectedGroupSummaries = recordedGroupListResponse.getServerGroups();
        List<ModelsPersistentServerSummary> expectedPersistentServerSummaries = recordedPersistentServerListResponse.getPersistentServers();
        keepOnlyBlueprintIds(expectedServerSummaries);
        removeGroupStatistics(expectedGroupSummaries);

        NetworkContents contents = NetworkCopy.createFromSnapshot(snapshot, NETWORK_ID).buildContents();

        assertEquals(expectedServerSummaries, contents.listServerSummaries(null));
        assertEquals(expectedGroupSummaries, contents.listGroupSummaries(null));
        assertEquals(expectedPersistentServerSummaries, contents.listPersistentServerSummaries(null));
    }

    @Test
    void emptyScalingModeIsRefusedOnBothSides() throws IOException {
        NetworkState snapshot = NetworkState.parseFrom(readFixtureBytes("several-groups", "network-state.pb"));
        String groupListJson = readFixtureText("several-groups", "server-groups.json");

        assertThrows(RuntimeException.class, () -> JSON.deserialize(groupListJson, ModelsListServerGroupsResponse.class));
        assertThrows(RuntimeException.class, () -> NetworkCopy.createFromSnapshot(snapshot, NETWORK_ID));
    }

    @Test
    void serversAreOrderedByGroupThenNumericalIdThenId() {
        NetworkState snapshot = NetworkState.newBuilder()
                .addServers(buildStateServerOfGroup("00000000-0000-4000-8000-000000000201", GROUP_B_ID, 1))
                .addServers(buildStateServerOfPersistentServer("00000000-0000-4000-8000-000000000202"))
                .addServers(buildStateServerOfGroup("00000000-0000-4000-8000-000000000204", GROUP_A_ID, 2))
                .addServers(buildStateServerOfGroup("00000000-0000-4000-8000-000000000205", GROUP_A_ID, 1))
                .addServers(buildStateServerOfGroup("00000000-0000-4000-8000-000000000203", GROUP_A_ID, 1))
                .build();

        NetworkContents contents = NetworkCopy.createFromSnapshot(snapshot, NETWORK_ID).buildContents();

        List<String> expected = List.of(
                "00000000-0000-4000-8000-000000000203",
                "00000000-0000-4000-8000-000000000205",
                "00000000-0000-4000-8000-000000000204",
                "00000000-0000-4000-8000-000000000201",
                "00000000-0000-4000-8000-000000000202"
        );
        assertEquals(expected, listServerIds(contents));
    }

    @Test
    void groupsAreOrderedNewestFirstThenById() {
        NetworkState snapshot = NetworkState.newBuilder()
                .addServerGroups(buildStateServerGroup(GROUP_B_ID, "older", "2026-11-17T09:00:00Z"))
                .addServerGroups(buildStateServerGroup(GROUP_A_ID, "newer", "2026-11-17T09:00:00.5Z"))
                .addServerGroups(buildStateServerGroup("0a000000-0000-4000-8000-000000000300", "newer too", "2026-11-17T09:00:00.5Z"))
                .build();

        NetworkContents contents = NetworkCopy.createFromSnapshot(snapshot, NETWORK_ID).buildContents();

        List<String> names = new ArrayList<>();
        for (ModelsServerGroupSummary groupSummary : contents.listGroupSummaries(null)) {
            names.add(groupSummary.getName());
        }
        assertEquals(List.of("newer too", "newer", "older"), names);
    }

    @Test
    void timestampsKeepOnlyTheFractionDigitsTheyNeed() {
        StateServer stateServer = buildStateServerOfGroup("00000000-0000-4000-8000-000000000201", GROUP_A_ID, 1).toBuilder()
                .setCreatedAtMicros(1794909600000000L)
                .setUpdatedAtMicros(1794909600000001L)
                .build();
        StateServerMetrics metrics = buildMetrics(stateServer.getId(), 0).toBuilder()
                .setLastActivityNanos(1794916740500000000L)
                .build();
        NetworkState snapshot = NetworkState.newBuilder().addServers(stateServer).addMetrics(metrics).build();

        ModelsServerSummary summary = NetworkCopy.createFromSnapshot(snapshot, NETWORK_ID).buildContents().listServerSummaries(null).getFirst();

        assertEquals("2026-11-17T10:00:00Z", summary.getCreatedAt());
        assertEquals("2026-11-17T10:00:00.000001Z", summary.getUpdatedAt());
        assertEquals("2026-11-17T11:59:00.5Z", summary.getLastActivity());
    }

    @Test
    void serverGroupInfoShowsTheGroupsBlueprintById() {
        String blueprintId = "0e000000-0000-4000-8000-000000000101";
        String infoJson = "{\"id\":\"" + GROUP_A_ID + "\",\"source\":{\"type\":\"blueprint\"}}";
        StateServerGroup stateServerGroup = buildStateServerGroup(GROUP_A_ID, "lobby", "2026-11-17T09:00:00Z").toBuilder()
                .setBlueprintId(toUuidBytes(blueprintId))
                .setInfoJson(ByteString.copyFromUtf8(infoJson))
                .build();
        NetworkState snapshot = NetworkState.newBuilder()
                .addServers(buildStateServerOfGroup("00000000-0000-4000-8000-000000000201", GROUP_A_ID, 1))
                .addServerGroups(stateServerGroup)
                .build();

        ModelsServerSummary serverSummary = NetworkCopy.createFromSnapshot(snapshot, NETWORK_ID).buildContents().listServerSummaries(null).getFirst();

        assertEquals(blueprintId, serverSummary.getServerGroup().getSource().getBlueprint().getId());
    }

    @Test
    void batchChangesAddsAndDeletesItemsOfEachKind() {
        String changedServerId = "00000000-0000-4000-8000-000000000201";
        String deletedServerId = "00000000-0000-4000-8000-000000000202";
        String newServerId = "00000000-0000-4000-8000-000000000203";
        String deletedPersistentServerId = "0c000000-0000-4000-8000-000000000402";
        String newPersistentServerId = "0c000000-0000-4000-8000-000000000403";
        NetworkState snapshot = NetworkState.newBuilder()
                .setEpoch(4)
                .setRevision(10)
                .addServers(buildStateServerOfGroup(changedServerId, GROUP_A_ID, 1))
                .addServers(buildStateServerOfGroup(deletedServerId, GROUP_A_ID, 2))
                .addServerGroups(buildStateServerGroup(GROUP_A_ID, "lobby", "2026-11-17T09:00:00Z"))
                .addServerGroups(buildStateServerGroup(GROUP_B_ID, "game", "2026-11-17T09:00:00Z"))
                .addPersistentServers(buildStatePersistentServer(PERSISTENT_SERVER_ID, "survival"))
                .addPersistentServers(buildStatePersistentServer(deletedPersistentServerId, "archive"))
                .build();
        StateBatch batch = StateBatch.newBuilder()
                .setEpoch(4)
                .setBaseRevision(10)
                .setRevision(12)
                .addServers(buildStateServerOfGroup(changedServerId, GROUP_A_ID, 7))
                .addServers(buildStateServerOfGroup(newServerId, GROUP_A_ID, 3))
                .addDeletedServerIds(toUuidBytes(deletedServerId))
                .addServerGroups(buildStateServerGroup(GROUP_A_ID, "lobby renamed", "2026-11-17T09:00:00Z"))
                .addServerGroups(buildStateServerGroup("0d000000-0000-4000-8000-000000000303", "new group", "2026-11-17T08:00:00Z"))
                .addDeletedServerGroupIds(toUuidBytes(GROUP_B_ID))
                .addPersistentServers(buildStatePersistentServer(PERSISTENT_SERVER_ID, "survival renamed"))
                .addPersistentServers(buildStatePersistentServer(newPersistentServerId, "creative"))
                .addDeletedPersistentServerIds(toUuidBytes(deletedPersistentServerId))
                .build();
        NetworkCopy networkCopy = NetworkCopy.createFromSnapshot(snapshot, NETWORK_ID);

        networkCopy.applyBatch(batch);
        NetworkContents contents = networkCopy.buildContents();

        assertEquals(12, networkCopy.revision());
        assertEquals(List.of(newServerId, changedServerId), listServerIds(contents));
        assertEquals(7, contents.findServerSummaryById(changedServerId).getNumericalId());
        assertEquals("lobby renamed", contents.findServerSummaryById(changedServerId).getServerGroup().getName());
        assertEquals("lobby renamed", contents.findGroupSummaryById(GROUP_A_ID).getName());
        assertEquals("new group", contents.findGroupSummaryByName("new group").getName());
        assertNull(contents.findGroupSummaryById(GROUP_B_ID));
        assertEquals("survival renamed", contents.findPersistentServerSummaryById(PERSISTENT_SERVER_ID).getName());
        assertEquals("creative", contents.findPersistentServerSummaryById(newPersistentServerId).getName());
        assertNull(contents.findPersistentServerSummaryById(deletedPersistentServerId));
    }

    @Test
    void newMetricsReplaceAllOldMetrics() {
        StateServer serverWithOldMetrics = buildStateServerOfGroup("00000000-0000-4000-8000-000000000201", GROUP_A_ID, 1);
        StateServer serverWithNewMetrics = buildStateServerOfGroup("00000000-0000-4000-8000-000000000202", GROUP_A_ID, 2);
        NetworkState snapshot = NetworkState.newBuilder()
                .addServers(serverWithOldMetrics)
                .addServers(serverWithNewMetrics)
                .addMetrics(buildMetrics(serverWithOldMetrics.getId(), 5))
                .build();
        NetworkCopy networkCopy = NetworkCopy.createFromSnapshot(snapshot, NETWORK_ID);

        networkCopy.setMetrics(List.of(buildMetrics(serverWithNewMetrics.getId(), 8)));
        List<ModelsServerSummary> serverSummaries = networkCopy.buildContents().listServerSummaries(null);

        assertEquals(0, serverSummaries.get(0).getPlayerCount());
        assertEquals("", serverSummaries.get(0).getLastActivity());
        assertEquals(8, serverSummaries.get(1).getPlayerCount());
    }

    @Test
    void persistentServerCountsThePlayersOfItsAvailableAndIngameServers() {
        StateServer available = buildStateServerOfPersistentServer("00000000-0000-4000-8000-000000000201");
        StateServer ingame = buildStateServerOfPersistentServer("00000000-0000-4000-8000-000000000202").toBuilder().setState("INGAME").build();
        StateServer starting = buildStateServerOfPersistentServer("00000000-0000-4000-8000-000000000203").toBuilder().setState("STARTING").build();
        NetworkState snapshot = NetworkState.newBuilder()
                .addServers(available)
                .addServers(ingame)
                .addServers(starting)
                .addPersistentServers(buildStatePersistentServer(PERSISTENT_SERVER_ID, "survival"))
                .addMetrics(buildMetrics(available.getId(), 3))
                .addMetrics(buildMetrics(ingame.getId(), 4))
                .addMetrics(buildMetrics(starting.getId(), 5))
                .build();

        NetworkContents contents = NetworkCopy.createFromSnapshot(snapshot, NETWORK_ID).buildContents();

        ModelsPersistentServerSummary summary = contents.findPersistentServerSummaryById(PERSISTENT_SERVER_ID);
        assertEquals(7, summary.getPlayerCount());
    }

    @Test
    void unreadableJsonIsRefused() {
        StateServerGroup unreadableStateServerGroup = buildStateServerGroup(GROUP_A_ID, "lobby", "2026-11-17T09:00:00Z").toBuilder()
                .setSummaryJson(ByteString.copyFromUtf8("{not json"))
                .build();
        NetworkState snapshot = NetworkState.newBuilder().addServerGroups(unreadableStateServerGroup).build();

        assertThrows(RuntimeException.class, () -> NetworkCopy.createFromSnapshot(snapshot, NETWORK_ID));
    }

    private static StateServer buildStateServerOfGroup(String id, String groupId, int numericalId) {
        return StateServer.newBuilder()
                .setId(toUuidBytes(id))
                .setServerGroupId(toUuidBytes(groupId))
                .setNumericalId(numericalId)
                .setState("AVAILABLE")
                .setPropertiesJson(ByteString.copyFromUtf8("{}"))
                .build();
    }

    private static StateServer buildStateServerOfPersistentServer(String id) {
        return StateServer.newBuilder()
                .setId(toUuidBytes(id))
                .setPersistentServerId(toUuidBytes(PERSISTENT_SERVER_ID))
                .setNumericalId(1)
                .setState("AVAILABLE")
                .setPropertiesJson(ByteString.copyFromUtf8("{}"))
                .build();
    }

    private static StateServerGroup buildStateServerGroup(String id, String name, String createdAt) {
        String summaryJson = "{\"server_group_id\":\"" + id + "\",\"name\":\"" + name + "\",\"created_at\":\"" + createdAt + "\"}";
        String infoJson = "{\"id\":\"" + id + "\",\"name\":\"" + name + "\"}";
        return StateServerGroup.newBuilder()
                .setId(toUuidBytes(id))
                .setSummaryJson(ByteString.copyFromUtf8(summaryJson))
                .setInfoJson(ByteString.copyFromUtf8(infoJson))
                .build();
    }

    private static StatePersistentServer buildStatePersistentServer(String id, String name) {
        String summaryJson = "{\"persistent_server_id\":\"" + id + "\",\"name\":\"" + name + "\",\"created_at\":\"2026-11-17T09:00:00Z\"}";
        String infoJson = "{\"id\":\"" + id + "\",\"name\":\"" + name + "\"}";
        return StatePersistentServer.newBuilder()
                .setId(toUuidBytes(id))
                .setSummaryJson(ByteString.copyFromUtf8(summaryJson))
                .setInfoJson(ByteString.copyFromUtf8(infoJson))
                .build();
    }

    private static StateServerMetrics buildMetrics(ByteString serverId, int playerCount) {
        return StateServerMetrics.newBuilder()
                .setServerId(serverId)
                .setPlayerCount(playerCount)
                .build();
    }

    // The copy has no group statistics: the public Group has no method that reads them.
    private static void removeGroupStatistics(List<ModelsServerGroupSummary> groupSummaries) {
        for (ModelsServerGroupSummary groupSummary : groupSummaries) {
            groupSummary.setStats(null);
        }
    }

    // The copy knows a group's blueprint only by its id: the public API reads nothing else of it.
    private static void keepOnlyBlueprintIds(List<ModelsServerSummary> serverSummaries) {
        for (ModelsServerSummary serverSummary : serverSummaries) {
            ModelsServerGroupInfo groupInfo = serverSummary.getServerGroup();
            boolean showsBlueprint = groupInfo != null && groupInfo.getSource() != null && groupInfo.getSource().getBlueprint() != null;
            if (showsBlueprint) {
                String blueprintId = groupInfo.getSource().getBlueprint().getId();
                groupInfo.getSource().setBlueprint(new ModelsBlueprintInfo().id(blueprintId));
            }
        }
    }

    // The controller's test groups have an empty scaling mode, which the generated group class refuses.
    private static String replaceEmptyScalingModeWithSlots(String groupJson) {
        return groupJson.replace("\"scaling_mode\":\"\"", "\"scaling_mode\":\"SLOTS\"");
    }

    private static NetworkState replaceEmptyScalingModeWithSlots(NetworkState snapshot) {
        NetworkState.Builder changedSnapshot = snapshot.toBuilder().clearServerGroups();
        for (StateServerGroup stateServerGroup : snapshot.getServerGroupsList()) {
            String summaryJson = replaceEmptyScalingModeWithSlots(stateServerGroup.getSummaryJson().toStringUtf8());
            StateServerGroup changedStateServerGroup = stateServerGroup.toBuilder().setSummaryJson(ByteString.copyFromUtf8(summaryJson)).build();
            changedSnapshot.addServerGroups(changedStateServerGroup);
        }
        return changedSnapshot.build();
    }

    private static List<String> listServerIds(NetworkContents contents) {
        List<String> ids = new ArrayList<>();
        for (ModelsServerSummary serverSummary : contents.listServerSummaries(null)) {
            ids.add(serverSummary.getServerId());
        }
        return ids;
    }

    private static ByteString toUuidBytes(String id) {
        UUID uuid = UUID.fromString(id);
        ByteBuffer bytes = ByteBuffer.allocate(16);
        bytes.putLong(uuid.getMostSignificantBits());
        bytes.putLong(uuid.getLeastSignificantBits());
        return ByteString.copyFrom(bytes.array());
    }

    private static String readFixtureText(String fixtureCase, String fileName) throws IOException {
        return new String(readFixtureBytes(fixtureCase, fileName), StandardCharsets.UTF_8);
    }

    private static byte[] readFixtureBytes(String fixtureCase, String fileName) throws IOException {
        String path = "/network-state/" + fixtureCase + "/" + fileName;
        try (InputStream stream = NetworkCopyTest.class.getResourceAsStream(path)) {
            return stream.readAllBytes();
        }
    }
}
