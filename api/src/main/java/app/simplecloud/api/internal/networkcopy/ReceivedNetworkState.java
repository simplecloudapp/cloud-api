package app.simplecloud.api.internal.networkcopy;

import app.simplecloud.api.web.JSON;
import app.simplecloud.api.web.models.ModelsBlueprintInfo;
import app.simplecloud.api.web.models.ModelsPersistentServerInfo;
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
import com.google.gson.reflect.TypeToken;
import com.google.protobuf.ByteString;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What the controller sent about one network, by id, at one revision.
 * Changed only inside {@code NetworkCopy.onPulse} and {@code NetworkCopy.onBatch}, under the copy's lock.
 * Parts that the generated HTTP classes refuse make the methods throw a {@link RuntimeException}.
 */
final class ReceivedNetworkState {
    private static final Type PROPERTIES_TYPE = new TypeToken<Map<String, Object>>() {
    }.getType();

    // Go's RFC3339Nano in UTC: only as many fraction digits as needed, and no point for whole seconds.
    private static final DateTimeFormatter TIMESTAMP_FORMAT = new DateTimeFormatterBuilder()
            .appendPattern("uuuu-MM-dd'T'HH:mm:ss")
            .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true)
            .appendLiteral('Z')
            .toFormatter()
            .withZone(ZoneOffset.UTC);

    private final String networkId;
    private final long epoch;
    private long revision;
    private final Map<String, ServerItem> serversById = new HashMap<>();
    private final Map<String, GroupItem> groupsById = new HashMap<>();
    private final Map<String, PersistentServerItem> persistentServersById = new HashMap<>();
    private Map<String, StateServerMetrics> metricsByServerId = new HashMap<>();

    private record ServerItem(StateServer stateServer, Map<String, Object> properties) {
    }

    private record GroupItem(ModelsServerGroupSummary summary, ModelsServerGroupInfo infoInsideServers, Instant createdAt) {
    }

    private record PersistentServerItem(ModelsPersistentServerSummary summary,
                                        ModelsPersistentServerInfo infoInsideServers,
                                        Instant createdAt) {
    }

    private ReceivedNetworkState(String networkId, long epoch, long revision) {
        this.networkId = networkId;
        this.epoch = epoch;
        this.revision = revision;
    }

    /**
     * What a full snapshot holds.
     */
    static ReceivedNetworkState fromSnapshot(NetworkState snapshot, String networkId) {
        ReceivedNetworkState received = new ReceivedNetworkState(networkId, snapshot.getEpoch(), snapshot.getRevision());
        for (StateServer server : snapshot.getServersList()) {
            received.putServer(server);
        }
        for (StateServerGroup group : snapshot.getServerGroupsList()) {
            received.putGroup(group);
        }
        for (StatePersistentServer persistentServer : snapshot.getPersistentServersList()) {
            received.putPersistentServer(persistentServer);
        }
        received.setMetrics(snapshot.getMetricsList());
        return received;
    }

    long epoch() {
        return epoch;
    }

    long revision() {
        return revision;
    }

    /**
     * Puts the batch's servers, groups and persistent servers in, removes its deleted ids, and moves to the batch's revision.
     */
    void apply(StateBatch batch) {
        for (StateServer server : batch.getServersList()) {
            putServer(server);
        }
        for (ByteString id : batch.getDeletedServerIdsList()) {
            serversById.remove(uuidText(id));
        }
        for (StateServerGroup group : batch.getServerGroupsList()) {
            putGroup(group);
        }
        for (ByteString id : batch.getDeletedServerGroupIdsList()) {
            groupsById.remove(uuidText(id));
        }
        for (StatePersistentServer persistentServer : batch.getPersistentServersList()) {
            putPersistentServer(persistentServer);
        }
        for (ByteString id : batch.getDeletedPersistentServerIdsList()) {
            persistentServersById.remove(uuidText(id));
        }
        revision = batch.getRevision();
    }

    /**
     * Replaces all server metrics.
     */
    void setMetrics(List<StateServerMetrics> metrics) {
        Map<String, StateServerMetrics> newMetrics = new HashMap<>();
        for (StateServerMetrics serverMetrics : metrics) {
            newMetrics.put(uuidText(serverMetrics.getServerId()), serverMetrics);
        }
        metricsByServerId = newMetrics;
    }

    /**
     * Builds the three lists from this state, in the controller's default order.
     */
    NetworkContents toContents() {
        List<ModelsServerSummary> serverList = new ArrayList<>(serversById.size());
        for (ServerItem server : serversById.values()) {
            serverList.add(serverSummary(server));
        }
        serverList.sort(ReceivedNetworkState::compareServers);

        List<GroupItem> groupItems = new ArrayList<>(groupsById.values());
        groupItems.sort(ReceivedNetworkState::compareGroups);
        List<ModelsServerGroupSummary> groupList = new ArrayList<>(groupItems.size());
        for (GroupItem group : groupItems) {
            groupList.add(group.summary());
        }

        Map<String, Integer> playerCounts = playerCountsByPersistentServerId(serverList);
        List<PersistentServerItem> persistentServerItems = new ArrayList<>(persistentServersById.values());
        persistentServerItems.sort(ReceivedNetworkState::comparePersistentServers);
        List<ModelsPersistentServerSummary> persistentServerList = new ArrayList<>(persistentServerItems.size());
        for (PersistentServerItem persistentServer : persistentServerItems) {
            int playerCount = playerCounts.getOrDefault(persistentServer.summary().getPersistentServerId(), 0);
            persistentServerList.add(persistentServerSummary(persistentServer, playerCount));
        }

        return new NetworkContents(epoch, revision, serverList, groupList, persistentServerList);
    }

    private void putServer(StateServer server) {
        String propertiesJson = server.getPropertiesJson().toStringUtf8();
        Map<String, Object> properties = JSON.deserialize(propertiesJson, PROPERTIES_TYPE);
        serversById.put(uuidText(server.getId()), new ServerItem(server, properties));
    }

    // The group part comes without its blueprint. The public API reads only the blueprint's id.
    private void putGroup(StateServerGroup group) {
        String summaryJson = group.getSummaryJson().toStringUtf8();
        ModelsServerGroupSummary summary = JSON.deserialize(summaryJson, ModelsServerGroupSummary.class);
        String infoJson = group.getInfoJson().toStringUtf8();
        ModelsServerGroupInfo info = JSON.deserialize(infoJson, ModelsServerGroupInfo.class);
        boolean showsBlueprint = group.hasBlueprintId() && info.getSource() != null;
        if (showsBlueprint) {
            ModelsBlueprintInfo blueprint = new ModelsBlueprintInfo().id(uuidText(group.getBlueprintId()));
            info.getSource().setBlueprint(blueprint);
        }
        Instant createdAt = Instant.parse(summary.getCreatedAt());
        groupsById.put(uuidText(group.getId()), new GroupItem(summary, info, createdAt));
    }

    private void putPersistentServer(StatePersistentServer persistentServer) {
        String summaryJson = persistentServer.getSummaryJson().toStringUtf8();
        ModelsPersistentServerSummary summary = JSON.deserialize(summaryJson, ModelsPersistentServerSummary.class);
        String infoJson = persistentServer.getInfoJson().toStringUtf8();
        ModelsPersistentServerInfo info = JSON.deserialize(infoJson, ModelsPersistentServerInfo.class);
        Instant createdAt = Instant.parse(summary.getCreatedAt());
        persistentServersById.put(uuidText(persistentServer.getId()), new PersistentServerItem(summary, info, createdAt));
    }

    private ModelsServerSummary serverSummary(ServerItem item) {
        StateServer stateServer = item.stateServer();
        String id = uuidText(stateServer.getId());
        ModelsServerSummary summary = new ModelsServerSummary();
        summary.setServerId(id);
        summary.setNetworkId(networkId);
        if (stateServer.hasServerGroupId()) {
            String groupId = uuidText(stateServer.getServerGroupId());
            summary.setServerGroupId(groupId);
            GroupItem group = groupsById.get(groupId);
            if (group != null) {
                summary.setServerGroup(group.infoInsideServers());
            }
        }
        if (stateServer.hasPersistentServerId()) {
            String persistentServerId = uuidText(stateServer.getPersistentServerId());
            summary.setPersistentServerId(persistentServerId);
            PersistentServerItem persistentServer = persistentServersById.get(persistentServerId);
            if (persistentServer != null) {
                summary.setPersistentServer(persistentServer.infoInsideServers());
            }
        }
        if (stateServer.hasServerhostId()) {
            summary.setServerhostId(uuidText(stateServer.getServerhostId()));
        }
        summary.setNumericalId(stateServer.getNumericalId());
        if (stateServer.hasIp()) {
            summary.setIp(stateServer.getIp());
        }
        if (stateServer.hasPort()) {
            summary.setPort(Math.toIntExact(stateServer.getPort()));
        }
        summary.setMinMemory(Math.toIntExact(stateServer.getMinMemory()));
        summary.setMaxMemory(Math.toIntExact(stateServer.getMaxMemory()));
        summary.setMaxPlayers(Math.toIntExact(stateServer.getMaxPlayers()));
        summary.setState(ModelsServerSummary.StateEnum.fromValue(stateServer.getState()));
        summary.setProperties(item.properties());
        Instant createdAt = instantOfMicros(stateServer.getCreatedAtMicros());
        summary.setCreatedAt(formatTimestamp(createdAt));
        Instant updatedAt = instantOfMicros(stateServer.getUpdatedAtMicros());
        summary.setUpdatedAt(formatTimestamp(updatedAt));
        fillMetrics(summary, metricsByServerId.get(id));
        return summary;
    }

    // Without fresh metrics the controller shows zeros and an empty last activity.
    private static void fillMetrics(ModelsServerSummary summary, StateServerMetrics metrics) {
        if (metrics == null) {
            summary.setPlayerCount(0);
            summary.setCpuUsage(BigDecimal.ZERO);
            summary.setMemoryUsage(BigDecimal.ZERO);
            summary.setLastActivity("");
            return;
        }
        summary.setPlayerCount(metrics.getPlayerCount());
        summary.setCpuUsage(BigDecimal.valueOf(metrics.getCpuUsage()));
        summary.setMemoryUsage(BigDecimal.valueOf(metrics.getMemoryUsage()));
        if (metrics.hasLastActivityNanos()) {
            Instant lastActivity = instantOfNanos(metrics.getLastActivityNanos());
            summary.setLastActivity(formatTimestamp(lastActivity));
        } else {
            summary.setLastActivity("");
        }
    }

    // A copy, because the parsed summary is shared by every contents built from this state.
    private static ModelsPersistentServerSummary persistentServerSummary(PersistentServerItem item, int playerCount) {
        ModelsPersistentServerSummary parsed = item.summary();
        ModelsPersistentServerSummary summary = new ModelsPersistentServerSummary();
        summary.setActive(parsed.getActive());
        summary.setCreatedAt(parsed.getCreatedAt());
        summary.setMaxMemory(parsed.getMaxMemory());
        summary.setMaxPlayers(parsed.getMaxPlayers());
        summary.setMinMemory(parsed.getMinMemory());
        summary.setName(parsed.getName());
        summary.setPersistentServerId(parsed.getPersistentServerId());
        summary.setPlayerCount(playerCount);
        summary.setPriority(parsed.getPriority());
        summary.setProperties(parsed.getProperties());
        summary.setServerhostId(parsed.getServerhostId());
        summary.setSource(parsed.getSource());
        summary.setTags(parsed.getTags());
        summary.setType(parsed.getType());
        summary.setUpdatedAt(parsed.getUpdatedAt());
        summary.setWorkflows(parsed.getWorkflows());
        return summary;
    }

    // The controller counts the players of servers that are AVAILABLE or INGAME.
    private static Map<String, Integer> playerCountsByPersistentServerId(List<ModelsServerSummary> servers) {
        Map<String, Integer> playerCounts = new HashMap<>();
        for (ModelsServerSummary server : servers) {
            ModelsServerSummary.StateEnum state = server.getState();
            boolean counts = state == ModelsServerSummary.StateEnum.AVAILABLE || state == ModelsServerSummary.StateEnum.INGAME;
            String persistentServerId = server.getPersistentServerId();
            if (counts && persistentServerId != null) {
                int countedSoFar = playerCounts.getOrDefault(persistentServerId, 0);
                playerCounts.put(persistentServerId, countedSoFar + server.getPlayerCount());
            }
        }
        return playerCounts;
    }

    // Group id with servers without a group last, then numerical id, then id.
    private static int compareServers(ModelsServerSummary first, ModelsServerSummary second) {
        int byGroup = compareGroupIds(first.getServerGroupId(), second.getServerGroupId());
        if (byGroup != 0) {
            return byGroup;
        }
        int byNumericalId = Integer.compare(first.getNumericalId(), second.getNumericalId());
        if (byNumericalId != 0) {
            return byNumericalId;
        }
        return first.getServerId().compareTo(second.getServerId());
    }

    private static int compareGroupIds(String first, String second) {
        if (first == null && second == null) {
            return 0;
        }
        if (first == null) {
            return 1;
        }
        if (second == null) {
            return -1;
        }
        return first.compareTo(second);
    }

    private static int compareGroups(GroupItem first, GroupItem second) {
        return compareNewestFirst(first.createdAt(), first.summary().getServerGroupId(),
                second.createdAt(), second.summary().getServerGroupId());
    }

    private static int comparePersistentServers(PersistentServerItem first, PersistentServerItem second) {
        return compareNewestFirst(first.createdAt(), first.summary().getPersistentServerId(),
                second.createdAt(), second.summary().getPersistentServerId());
    }

    private static int compareNewestFirst(Instant firstCreatedAt, String firstId, Instant secondCreatedAt, String secondId) {
        int byCreation = secondCreatedAt.compareTo(firstCreatedAt);
        if (byCreation != 0) {
            return byCreation;
        }
        return firstId.compareTo(secondId);
    }

    // Lower-case text, so the order of the texts is the order of the id bytes.
    private static String uuidText(ByteString id) {
        ByteBuffer bytes = id.asReadOnlyByteBuffer();
        UUID uuid = new UUID(bytes.getLong(), bytes.getLong());
        return uuid.toString();
    }

    private static Instant instantOfMicros(long micros) {
        long seconds = Math.floorDiv(micros, 1_000_000L);
        long microsOfSecond = Math.floorMod(micros, 1_000_000L);
        return Instant.ofEpochSecond(seconds, microsOfSecond * 1_000L);
    }

    private static Instant instantOfNanos(long nanos) {
        long seconds = Math.floorDiv(nanos, 1_000_000_000L);
        long nanosOfSecond = Math.floorMod(nanos, 1_000_000_000L);
        return Instant.ofEpochSecond(seconds, nanosOfSecond);
    }

    private static String formatTimestamp(Instant time) {
        return TIMESTAMP_FORMAT.format(time);
    }
}
