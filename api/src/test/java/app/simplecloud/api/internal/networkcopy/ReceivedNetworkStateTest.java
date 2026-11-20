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

class ReceivedNetworkStateTest {

    private static final String NETWORK_ID = "0d000000-0000-4000-8000-000000000001";
    private static final String GROUP_A_ID = "0a000000-0000-4000-8000-000000000301";
    private static final String GROUP_B_ID = "0b000000-0000-4000-8000-000000000302";
    private static final String PERSISTENT_SERVER_ID = "0c000000-0000-4000-8000-000000000401";

    // The only fixture without groups, so neither group statistics nor blueprints need to be removed.
    @Test
    void snapshotListsEqualTheControllerLists() throws IOException {
        NetworkState snapshot = NetworkState.parseFrom(fixture("persistent-servers", "network-state.pb"));
        String serverList = fixtureText("persistent-servers", "servers.json");
        String groupList = fixtureText("persistent-servers", "server-groups.json");
        String persistentServerList = fixtureText("persistent-servers", "persistent-servers.json");
        ModelsListServersResponse expectedServers = JSON.deserialize(serverList, ModelsListServersResponse.class);
        ModelsListServerGroupsResponse expectedGroups = JSON.deserialize(groupList, ModelsListServerGroupsResponse.class);
        ModelsListPersistentServersResponse expectedPersistentServers =
                JSON.deserialize(persistentServerList, ModelsListPersistentServersResponse.class);

        NetworkContents contents = ReceivedNetworkState.fromSnapshot(snapshot, NETWORK_ID).buildContents();

        assertEquals(expectedServers.getServers(), contents.listServerSummaries(null));
        assertNull(expectedGroups.getServerGroups());
        assertEquals(List.of(), contents.listGroupSummaries(null));
        assertEquals(expectedPersistentServers.getPersistentServers(), contents.listPersistentServerSummaries(null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"blueprint-sources", "nulls-and-images", "properties", "several-groups", "timestamps"})
    void snapshotListsEqualTheControllerListsWithoutTheExpectedDifferences(String fixtureCase) throws IOException {
        NetworkState recordedSnapshot = NetworkState.parseFrom(fixture(fixtureCase, "network-state.pb"));
        NetworkState snapshot = withSlotsScalingMode(recordedSnapshot);
        String serverList = fixtureText(fixtureCase, "servers.json");
        String groupList = withSlotsScalingMode(fixtureText(fixtureCase, "server-groups.json"));
        String persistentServerList = fixtureText(fixtureCase, "persistent-servers.json");
        ModelsListServersResponse recordedServers = JSON.deserialize(serverList, ModelsListServersResponse.class);
        ModelsListServerGroupsResponse recordedGroups = JSON.deserialize(groupList, ModelsListServerGroupsResponse.class);
        ModelsListPersistentServersResponse recordedPersistentServers =
                JSON.deserialize(persistentServerList, ModelsListPersistentServersResponse.class);
        List<ModelsServerSummary> expectedServers = recordedServers.getServers();
        List<ModelsServerGroupSummary> expectedGroups = recordedGroups.getServerGroups();
        List<ModelsPersistentServerSummary> expectedPersistentServers = recordedPersistentServers.getPersistentServers();
        keepOnlyBlueprintIds(expectedServers);
        removeGroupStatistics(expectedGroups);

        NetworkContents contents = ReceivedNetworkState.fromSnapshot(snapshot, NETWORK_ID).buildContents();

        assertEquals(expectedServers, contents.listServerSummaries(null));
        assertEquals(expectedGroups, contents.listGroupSummaries(null));
        assertEquals(expectedPersistentServers, contents.listPersistentServerSummaries(null));
    }

    @Test
    void emptyScalingModeIsRefusedOnBothSides() throws IOException {
        NetworkState snapshot = NetworkState.parseFrom(fixture("several-groups", "network-state.pb"));
        String groupList = fixtureText("several-groups", "server-groups.json");

        assertThrows(RuntimeException.class, () -> JSON.deserialize(groupList, ModelsListServerGroupsResponse.class));
        assertThrows(RuntimeException.class, () -> ReceivedNetworkState.fromSnapshot(snapshot, NETWORK_ID));
    }

    @Test
    void serversAreOrderedByGroupThenNumericalIdThenId() {
        NetworkState snapshot = NetworkState.newBuilder()
                .addServers(groupServer("00000000-0000-4000-8000-000000000201", GROUP_B_ID, 1))
                .addServers(serverOfPersistentServer("00000000-0000-4000-8000-000000000202"))
                .addServers(groupServer("00000000-0000-4000-8000-000000000204", GROUP_A_ID, 2))
                .addServers(groupServer("00000000-0000-4000-8000-000000000205", GROUP_A_ID, 1))
                .addServers(groupServer("00000000-0000-4000-8000-000000000203", GROUP_A_ID, 1))
                .build();

        NetworkContents contents = ReceivedNetworkState.fromSnapshot(snapshot, NETWORK_ID).buildContents();

        List<String> expected = List.of(
                "00000000-0000-4000-8000-000000000203",
                "00000000-0000-4000-8000-000000000205",
                "00000000-0000-4000-8000-000000000204",
                "00000000-0000-4000-8000-000000000201",
                "00000000-0000-4000-8000-000000000202"
        );
        assertEquals(expected, serverIds(contents));
    }

    @Test
    void groupsAreOrderedNewestFirstThenById() {
        NetworkState snapshot = NetworkState.newBuilder()
                .addServerGroups(group(GROUP_B_ID, "older", "2026-11-17T09:00:00Z"))
                .addServerGroups(group(GROUP_A_ID, "newer", "2026-11-17T09:00:00.5Z"))
                .addServerGroups(group("0a000000-0000-4000-8000-000000000300", "newer too", "2026-11-17T09:00:00.5Z"))
                .build();

        NetworkContents contents = ReceivedNetworkState.fromSnapshot(snapshot, NETWORK_ID).buildContents();

        List<String> names = new ArrayList<>();
        for (ModelsServerGroupSummary group : contents.listGroupSummaries(null)) {
            names.add(group.getName());
        }
        assertEquals(List.of("newer too", "newer", "older"), names);
    }

    @Test
    void timestampsKeepOnlyTheFractionDigitsTheyNeed() {
        StateServer server = groupServer("00000000-0000-4000-8000-000000000201", GROUP_A_ID, 1).toBuilder()
                .setCreatedAtMicros(1794909600000000L)
                .setUpdatedAtMicros(1794909600000001L)
                .build();
        StateServerMetrics metrics = metrics(server.getId(), 0).toBuilder()
                .setLastActivityNanos(1794916740500000000L)
                .build();
        NetworkState snapshot = NetworkState.newBuilder().addServers(server).addMetrics(metrics).build();

        ModelsServerSummary summary = ReceivedNetworkState.fromSnapshot(snapshot, NETWORK_ID).buildContents().listServerSummaries(null).getFirst();

        assertEquals("2026-11-17T10:00:00Z", summary.getCreatedAt());
        assertEquals("2026-11-17T10:00:00.000001Z", summary.getUpdatedAt());
        assertEquals("2026-11-17T11:59:00.5Z", summary.getLastActivity());
    }

    @Test
    void serverGroupInfoShowsTheGroupsBlueprintById() {
        String blueprintId = "0e000000-0000-4000-8000-000000000101";
        String info = "{\"id\":\"" + GROUP_A_ID + "\",\"source\":{\"type\":\"blueprint\"}}";
        StateServerGroup group = group(GROUP_A_ID, "lobby", "2026-11-17T09:00:00Z").toBuilder()
                .setBlueprintId(uuidBytes(blueprintId))
                .setInfoJson(ByteString.copyFromUtf8(info))
                .build();
        NetworkState snapshot = NetworkState.newBuilder()
                .addServers(groupServer("00000000-0000-4000-8000-000000000201", GROUP_A_ID, 1))
                .addServerGroups(group)
                .build();

        ModelsServerSummary server = ReceivedNetworkState.fromSnapshot(snapshot, NETWORK_ID).buildContents().listServerSummaries(null).getFirst();

        assertEquals(blueprintId, server.getServerGroup().getSource().getBlueprint().getId());
    }

    @Test
    void batchChangesAddsAndDeletesItemsOfEachKind() {
        String changedServer = "00000000-0000-4000-8000-000000000201";
        String deletedServer = "00000000-0000-4000-8000-000000000202";
        String newServer = "00000000-0000-4000-8000-000000000203";
        String deletedPersistentServer = "0c000000-0000-4000-8000-000000000402";
        String newPersistentServer = "0c000000-0000-4000-8000-000000000403";
        NetworkState snapshot = NetworkState.newBuilder()
                .setEpoch(4)
                .setRevision(10)
                .addServers(groupServer(changedServer, GROUP_A_ID, 1))
                .addServers(groupServer(deletedServer, GROUP_A_ID, 2))
                .addServerGroups(group(GROUP_A_ID, "lobby", "2026-11-17T09:00:00Z"))
                .addServerGroups(group(GROUP_B_ID, "game", "2026-11-17T09:00:00Z"))
                .addPersistentServers(persistentServer(PERSISTENT_SERVER_ID, "survival"))
                .addPersistentServers(persistentServer(deletedPersistentServer, "archive"))
                .build();
        StateBatch batch = StateBatch.newBuilder()
                .setEpoch(4)
                .setBaseRevision(10)
                .setRevision(12)
                .addServers(groupServer(changedServer, GROUP_A_ID, 7))
                .addServers(groupServer(newServer, GROUP_A_ID, 3))
                .addDeletedServerIds(uuidBytes(deletedServer))
                .addServerGroups(group(GROUP_A_ID, "lobby renamed", "2026-11-17T09:00:00Z"))
                .addServerGroups(group("0d000000-0000-4000-8000-000000000303", "new group", "2026-11-17T08:00:00Z"))
                .addDeletedServerGroupIds(uuidBytes(GROUP_B_ID))
                .addPersistentServers(persistentServer(PERSISTENT_SERVER_ID, "survival renamed"))
                .addPersistentServers(persistentServer(newPersistentServer, "creative"))
                .addDeletedPersistentServerIds(uuidBytes(deletedPersistentServer))
                .build();
        ReceivedNetworkState received = ReceivedNetworkState.fromSnapshot(snapshot, NETWORK_ID);

        received.apply(batch);
        NetworkContents contents = received.buildContents();

        assertEquals(12, received.revision());
        assertEquals(List.of(newServer, changedServer), serverIds(contents));
        assertEquals(7, contents.findServerSummaryById(changedServer).getNumericalId());
        assertEquals("lobby renamed", contents.findServerSummaryById(changedServer).getServerGroup().getName());
        assertEquals("lobby renamed", contents.findGroupSummaryById(GROUP_A_ID).getName());
        assertEquals("new group", contents.findGroupSummaryByName("new group").getName());
        assertNull(contents.findGroupSummaryById(GROUP_B_ID));
        assertEquals("survival renamed", contents.findPersistentServerSummaryById(PERSISTENT_SERVER_ID).getName());
        assertEquals("creative", contents.findPersistentServerSummaryById(newPersistentServer).getName());
        assertNull(contents.findPersistentServerSummaryById(deletedPersistentServer));
    }

    @Test
    void newMetricsReplaceAllOldMetrics() {
        StateServer first = groupServer("00000000-0000-4000-8000-000000000201", GROUP_A_ID, 1);
        StateServer second = groupServer("00000000-0000-4000-8000-000000000202", GROUP_A_ID, 2);
        NetworkState snapshot = NetworkState.newBuilder()
                .addServers(first)
                .addServers(second)
                .addMetrics(metrics(first.getId(), 5))
                .build();
        ReceivedNetworkState received = ReceivedNetworkState.fromSnapshot(snapshot, NETWORK_ID);

        received.setMetrics(List.of(metrics(second.getId(), 8)));
        List<ModelsServerSummary> servers = received.buildContents().listServerSummaries(null);

        assertEquals(0, servers.get(0).getPlayerCount());
        assertEquals("", servers.get(0).getLastActivity());
        assertEquals(8, servers.get(1).getPlayerCount());
    }

    @Test
    void persistentServerCountsThePlayersOfItsAvailableAndIngameServers() {
        StateServer available = serverOfPersistentServer("00000000-0000-4000-8000-000000000201");
        StateServer ingame = serverOfPersistentServer("00000000-0000-4000-8000-000000000202").toBuilder().setState("INGAME").build();
        StateServer starting = serverOfPersistentServer("00000000-0000-4000-8000-000000000203").toBuilder().setState("STARTING").build();
        NetworkState snapshot = NetworkState.newBuilder()
                .addServers(available)
                .addServers(ingame)
                .addServers(starting)
                .addPersistentServers(persistentServer(PERSISTENT_SERVER_ID, "survival"))
                .addMetrics(metrics(available.getId(), 3))
                .addMetrics(metrics(ingame.getId(), 4))
                .addMetrics(metrics(starting.getId(), 5))
                .build();

        NetworkContents contents = ReceivedNetworkState.fromSnapshot(snapshot, NETWORK_ID).buildContents();

        ModelsPersistentServerSummary summary = contents.findPersistentServerSummaryById(PERSISTENT_SERVER_ID);
        assertEquals(7, summary.getPlayerCount());
    }

    @Test
    void unreadableJsonIsRefused() {
        StateServerGroup unreadable = group(GROUP_A_ID, "lobby", "2026-11-17T09:00:00Z").toBuilder()
                .setSummaryJson(ByteString.copyFromUtf8("{not json"))
                .build();
        NetworkState snapshot = NetworkState.newBuilder().addServerGroups(unreadable).build();

        assertThrows(RuntimeException.class, () -> ReceivedNetworkState.fromSnapshot(snapshot, NETWORK_ID));
    }

    private static StateServer groupServer(String id, String groupId, int numericalId) {
        return StateServer.newBuilder()
                .setId(uuidBytes(id))
                .setServerGroupId(uuidBytes(groupId))
                .setNumericalId(numericalId)
                .setState("AVAILABLE")
                .setPropertiesJson(ByteString.copyFromUtf8("{}"))
                .build();
    }

    private static StateServer serverOfPersistentServer(String id) {
        return StateServer.newBuilder()
                .setId(uuidBytes(id))
                .setPersistentServerId(uuidBytes(PERSISTENT_SERVER_ID))
                .setNumericalId(1)
                .setState("AVAILABLE")
                .setPropertiesJson(ByteString.copyFromUtf8("{}"))
                .build();
    }

    private static StateServerGroup group(String id, String name, String createdAt) {
        String summary = "{\"server_group_id\":\"" + id + "\",\"name\":\"" + name + "\",\"created_at\":\"" + createdAt + "\"}";
        String info = "{\"id\":\"" + id + "\",\"name\":\"" + name + "\"}";
        return StateServerGroup.newBuilder()
                .setId(uuidBytes(id))
                .setSummaryJson(ByteString.copyFromUtf8(summary))
                .setInfoJson(ByteString.copyFromUtf8(info))
                .build();
    }

    private static StatePersistentServer persistentServer(String id, String name) {
        String summary = "{\"persistent_server_id\":\"" + id + "\",\"name\":\"" + name + "\",\"created_at\":\"2026-11-17T09:00:00Z\"}";
        String info = "{\"id\":\"" + id + "\",\"name\":\"" + name + "\"}";
        return StatePersistentServer.newBuilder()
                .setId(uuidBytes(id))
                .setSummaryJson(ByteString.copyFromUtf8(summary))
                .setInfoJson(ByteString.copyFromUtf8(info))
                .build();
    }

    private static StateServerMetrics metrics(ByteString serverId, int playerCount) {
        return StateServerMetrics.newBuilder()
                .setServerId(serverId)
                .setPlayerCount(playerCount)
                .build();
    }

    // The copy has no group statistics: the public Group has no method that reads them.
    private static void removeGroupStatistics(List<ModelsServerGroupSummary> groups) {
        for (ModelsServerGroupSummary group : groups) {
            group.setStats(null);
        }
    }

    // The copy knows a group's blueprint only by its id: the public API reads nothing else of it.
    private static void keepOnlyBlueprintIds(List<ModelsServerSummary> servers) {
        for (ModelsServerSummary server : servers) {
            ModelsServerGroupInfo group = server.getServerGroup();
            boolean showsBlueprint = group != null && group.getSource() != null && group.getSource().getBlueprint() != null;
            if (showsBlueprint) {
                String blueprintId = group.getSource().getBlueprint().getId();
                group.getSource().setBlueprint(new ModelsBlueprintInfo().id(blueprintId));
            }
        }
    }

    // The controller's test groups have an empty scaling mode, which the generated group class refuses.
    private static String withSlotsScalingMode(String groupJson) {
        return groupJson.replace("\"scaling_mode\":\"\"", "\"scaling_mode\":\"SLOTS\"");
    }

    private static NetworkState withSlotsScalingMode(NetworkState snapshot) {
        NetworkState.Builder changed = snapshot.toBuilder().clearServerGroups();
        for (StateServerGroup group : snapshot.getServerGroupsList()) {
            String summaryJson = withSlotsScalingMode(group.getSummaryJson().toStringUtf8());
            StateServerGroup changedGroup = group.toBuilder().setSummaryJson(ByteString.copyFromUtf8(summaryJson)).build();
            changed.addServerGroups(changedGroup);
        }
        return changed.build();
    }

    private static List<String> serverIds(NetworkContents contents) {
        List<String> ids = new ArrayList<>();
        for (ModelsServerSummary server : contents.listServerSummaries(null)) {
            ids.add(server.getServerId());
        }
        return ids;
    }

    private static ByteString uuidBytes(String id) {
        UUID uuid = UUID.fromString(id);
        ByteBuffer bytes = ByteBuffer.allocate(16);
        bytes.putLong(uuid.getMostSignificantBits());
        bytes.putLong(uuid.getLeastSignificantBits());
        return ByteString.copyFrom(bytes.array());
    }

    private static String fixtureText(String fixtureCase, String file) throws IOException {
        return new String(fixture(fixtureCase, file), StandardCharsets.UTF_8);
    }

    private static byte[] fixture(String fixtureCase, String file) throws IOException {
        String path = "/network-state/" + fixtureCase + "/" + file;
        try (InputStream stream = ReceivedNetworkStateTest.class.getResourceAsStream(path)) {
            return stream.readAllBytes();
        }
    }
}
