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
 * Changed only by the thread that runs {@code NetworkCopy.onPulse} and {@code NetworkCopy.onBatch}.
 * Parts that the generated HTTP classes refuse make the methods throw a {@link RuntimeException}.
 */
final class NetworkItems {
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
    private final Map<String, ServerItem> servers = new HashMap<>();
    private final Map<String, GroupItem> groups = new HashMap<>();
    private final Map<String, PersistentServerItem> persistentServers = new HashMap<>();
    private Map<String, StateServerMetrics> metricsByServerId = new HashMap<>();

    private record ServerItem(StateServer row, Map<String, Object> properties) {
    }

    private record GroupItem(ModelsServerGroupSummary summary, ModelsServerGroupInfo infoInsideServers, Instant createdAt) {
    }

    private record PersistentServerItem(ModelsPersistentServerSummary summary,
                                        ModelsPersistentServerInfo infoInsideServers,
                                        Instant createdAt) {
    }

    private NetworkItems(String networkId, long epoch, long revision) {
        this.networkId = networkId;
        this.epoch = epoch;
        this.revision = revision;
    }

    /**
     * The items of a full state.
     */
    static NetworkItems fromSnapshot(NetworkState snapshot, String networkId) {
        NetworkItems items = new NetworkItems(networkId, snapshot.getEpoch(), snapshot.getRevision());
        for (StateServer server : snapshot.getServersList()) {
            items.putServer(server);
        }
        for (StateServerGroup group : snapshot.getServerGroupsList()) {
            items.putGroup(group);
        }
        for (StatePersistentServer persistentServer : snapshot.getPersistentServersList()) {
            items.putPersistentServer(persistentServer);
        }
        items.setMetrics(snapshot.getMetricsList());
        return items;
    }

    long epoch() {
        return epoch;
    }

    long revision() {
        return revision;
    }

    /**
     * Puts the batch's items in, removes its deleted ids, and moves to the batch's revision.
     */
    void apply(StateBatch batch) {
        for (StateServer server : batch.getServersList()) {
            putServer(server);
        }
        for (ByteString id : batch.getDeletedServerIdsList()) {
            servers.remove(uuidText(id));
        }
        for (StateServerGroup group : batch.getServerGroupsList()) {
            putGroup(group);
        }
        for (ByteString id : batch.getDeletedServerGroupIdsList()) {
            groups.remove(uuidText(id));
        }
        for (StatePersistentServer persistentServer : batch.getPersistentServersList()) {
            putPersistentServer(persistentServer);
        }
        for (ByteString id : batch.getDeletedPersistentServerIdsList()) {
            persistentServers.remove(uuidText(id));
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
     * Builds the three lists from the items, in the controller's default order.
     */
    NetworkContents toContents() {
        List<ModelsServerSummary> serverList = new ArrayList<>(servers.size());
        for (ServerItem server : servers.values()) {
            serverList.add(serverSummary(server));
        }
        serverList.sort(NetworkItems::compareServers);

        List<GroupItem> groupItems = new ArrayList<>(groups.values());
        groupItems.sort(NetworkItems::compareGroups);
        List<ModelsServerGroupSummary> groupList = new ArrayList<>(groupItems.size());
        for (GroupItem group : groupItems) {
            groupList.add(group.summary());
        }

        Map<String, Integer> playerCounts = playerCountsByPersistentServerId(serverList);
        List<PersistentServerItem> persistentServerItems = new ArrayList<>(persistentServers.values());
        persistentServerItems.sort(NetworkItems::comparePersistentServers);
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
        servers.put(uuidText(server.getId()), new ServerItem(server, properties));
    }

    // The group part comes without its blueprint. The public API reads only the blueprint's id.
    private void putGroup(StateServerGroup group) {
        ModelsServerGroupSummary summary = JSON.deserialize(group.getSummaryJson().toStringUtf8(), ModelsServerGroupSummary.class);
        ModelsServerGroupInfo info = JSON.deserialize(group.getInfoJson().toStringUtf8(), ModelsServerGroupInfo.class);
        boolean showsBlueprint = group.hasBlueprintId() && info.getSource() != null;
        if (showsBlueprint) {
            ModelsBlueprintInfo blueprint = new ModelsBlueprintInfo().id(uuidText(group.getBlueprintId()));
            info.getSource().setBlueprint(blueprint);
        }
        Instant createdAt = Instant.parse(summary.getCreatedAt());
        groups.put(uuidText(group.getId()), new GroupItem(summary, info, createdAt));
    }

    private void putPersistentServer(StatePersistentServer persistentServer) {
        String summaryJson = persistentServer.getSummaryJson().toStringUtf8();
        ModelsPersistentServerSummary summary = JSON.deserialize(summaryJson, ModelsPersistentServerSummary.class);
        String infoJson = persistentServer.getInfoJson().toStringUtf8();
        ModelsPersistentServerInfo info = JSON.deserialize(infoJson, ModelsPersistentServerInfo.class);
        Instant createdAt = Instant.parse(summary.getCreatedAt());
        persistentServers.put(uuidText(persistentServer.getId()), new PersistentServerItem(summary, info, createdAt));
    }

    private ModelsServerSummary serverSummary(ServerItem item) {
        StateServer row = item.row();
        String id = uuidText(row.getId());
        ModelsServerSummary summary = new ModelsServerSummary();
        summary.setServerId(id);
        summary.setNetworkId(networkId);
        if (row.hasServerGroupId()) {
            String groupId = uuidText(row.getServerGroupId());
            summary.setServerGroupId(groupId);
            GroupItem group = groups.get(groupId);
            if (group != null) {
                summary.setServerGroup(group.infoInsideServers());
            }
        }
        if (row.hasPersistentServerId()) {
            String persistentServerId = uuidText(row.getPersistentServerId());
            summary.setPersistentServerId(persistentServerId);
            PersistentServerItem persistentServer = persistentServers.get(persistentServerId);
            if (persistentServer != null) {
                summary.setPersistentServer(persistentServer.infoInsideServers());
            }
        }
        if (row.hasServerhostId()) {
            summary.setServerhostId(uuidText(row.getServerhostId()));
        }
        summary.setNumericalId(row.getNumericalId());
        if (row.hasIp()) {
            summary.setIp(row.getIp());
        }
        if (row.hasPort()) {
            summary.setPort(Math.toIntExact(row.getPort()));
        }
        summary.setMinMemory(Math.toIntExact(row.getMinMemory()));
        summary.setMaxMemory(Math.toIntExact(row.getMaxMemory()));
        summary.setMaxPlayers(Math.toIntExact(row.getMaxPlayers()));
        summary.setState(ModelsServerSummary.StateEnum.fromValue(row.getState()));
        summary.setProperties(item.properties());
        summary.setCreatedAt(formatTimestamp(instantOfMicros(row.getCreatedAtMicros())));
        summary.setUpdatedAt(formatTimestamp(instantOfMicros(row.getUpdatedAtMicros())));
        setMetricsOf(summary, metricsByServerId.get(id));
        return summary;
    }

    // Without fresh metrics the controller shows zeros and an empty last activity.
    private static void setMetricsOf(ModelsServerSummary summary, StateServerMetrics metrics) {
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
            summary.setLastActivity(formatTimestamp(instantOfNanos(metrics.getLastActivityNanos())));
        } else {
            summary.setLastActivity("");
        }
    }

    // A copy, because the parsed summary is shared by every contents built from these items.
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
