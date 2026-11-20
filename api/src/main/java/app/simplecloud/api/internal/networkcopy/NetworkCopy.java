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
import org.jetbrains.annotations.Nullable;

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
 * The local copy of one network: what the controller sent about it, by id, at one revision.
 * Changed only by {@link NetworkCopySynchronizer}, under its lock.
 * JSON that the generated model classes cannot read makes the methods throw a {@link RuntimeException}.
 */
final class NetworkCopy {
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
    private final Map<String, ServerItem> serverItemsById = new HashMap<>();
    private final Map<String, GroupItem> groupItemsById = new HashMap<>();
    private final Map<String, PersistentServerItem> persistentServerItemsById = new HashMap<>();
    private Map<String, StateServerMetrics> metricsByServerId = new HashMap<>();

    // Keeps the message, not a built summary: a server's summary also shows its metrics and its group's or persistent server's info, which change without the server.
    private record ServerItem(StateServer stateServer, Map<String, Object> properties) {
    }

    private record GroupItem(ModelsServerGroupSummary summary, ModelsServerGroupInfo infoInsideServers, Instant createdAt) {
    }

    private record PersistentServerItem(ModelsPersistentServerSummary summary,
                                        ModelsPersistentServerInfo infoInsideServers,
                                        Instant createdAt) {
    }

    private NetworkCopy(String networkId, long epoch, long revision) {
        this.networkId = networkId;
        this.epoch = epoch;
        this.revision = revision;
    }

    /**
     * What a full snapshot holds.
     */
    static NetworkCopy createFromSnapshot(NetworkState snapshot, String networkId) {
        NetworkCopy networkCopy = new NetworkCopy(networkId, snapshot.getEpoch(), snapshot.getRevision());
        for (StateServer stateServer : snapshot.getServersList()) {
            networkCopy.putServer(stateServer);
        }
        for (StateServerGroup stateServerGroup : snapshot.getServerGroupsList()) {
            networkCopy.putGroup(stateServerGroup);
        }
        for (StatePersistentServer statePersistentServer : snapshot.getPersistentServersList()) {
            networkCopy.putPersistentServer(statePersistentServer);
        }
        networkCopy.setMetrics(snapshot.getMetricsList());
        return networkCopy;
    }

    long epoch() {
        return epoch;
    }

    long revision() {
        return revision;
    }

    /**
     * A batch that cannot be read makes this throw with a part of it already applied; the caller must then drop the copy.
     */
    void applyBatch(StateBatch batch) {
        for (StateServer stateServer : batch.getServersList()) {
            putServer(stateServer);
        }
        for (ByteString id : batch.getDeletedServerIdsList()) {
            serverItemsById.remove(toUuidText(id));
        }
        for (StateServerGroup stateServerGroup : batch.getServerGroupsList()) {
            putGroup(stateServerGroup);
        }
        for (ByteString id : batch.getDeletedServerGroupIdsList()) {
            groupItemsById.remove(toUuidText(id));
        }
        for (StatePersistentServer statePersistentServer : batch.getPersistentServersList()) {
            putPersistentServer(statePersistentServer);
        }
        for (ByteString id : batch.getDeletedPersistentServerIdsList()) {
            persistentServerItemsById.remove(toUuidText(id));
        }
        revision = batch.getRevision();
    }

    /**
     * Replaces all server metrics.
     */
    void setMetrics(List<StateServerMetrics> metrics) {
        Map<String, StateServerMetrics> newMetricsByServerId = new HashMap<>();
        for (StateServerMetrics serverMetrics : metrics) {
            newMetricsByServerId.put(toUuidText(serverMetrics.getServerId()), serverMetrics);
        }
        metricsByServerId = newMetricsByServerId;
    }

    /**
     * Builds the three lists from this copy, in the controller's default order.
     */
    NetworkContents buildContents() {
        List<ModelsServerSummary> serverSummaries = new ArrayList<>(serverItemsById.size());
        for (ServerItem serverItem : serverItemsById.values()) {
            serverSummaries.add(buildServerSummary(serverItem));
        }
        serverSummaries.sort(NetworkCopy::compareServerSummaries);

        List<GroupItem> groupItems = new ArrayList<>(groupItemsById.values());
        groupItems.sort(NetworkCopy::compareGroupItems);
        List<ModelsServerGroupSummary> groupSummaries = new ArrayList<>(groupItems.size());
        for (GroupItem groupItem : groupItems) {
            groupSummaries.add(groupItem.summary());
        }

        Map<String, Integer> playerCountsByPersistentServerId = countPlayersByPersistentServerId(serverSummaries);
        List<PersistentServerItem> persistentServerItems = new ArrayList<>(persistentServerItemsById.values());
        persistentServerItems.sort(NetworkCopy::comparePersistentServerItems);
        List<ModelsPersistentServerSummary> persistentServerSummaries = new ArrayList<>(persistentServerItems.size());
        for (PersistentServerItem persistentServerItem : persistentServerItems) {
            String persistentServerId = persistentServerItem.summary().getPersistentServerId();
            int playerCount = playerCountsByPersistentServerId.getOrDefault(persistentServerId, 0);
            persistentServerSummaries.add(buildPersistentServerSummary(persistentServerItem, playerCount));
        }

        return new NetworkContents(epoch, revision, serverSummaries, groupSummaries, persistentServerSummaries);
    }

    private void putServer(StateServer stateServer) {
        String propertiesJson = stateServer.getPropertiesJson().toStringUtf8();
        Map<String, Object> properties = JSON.deserialize(propertiesJson, PROPERTIES_TYPE);
        serverItemsById.put(toUuidText(stateServer.getId()), new ServerItem(stateServer, properties));
    }

    // The group's info JSON comes without its blueprint. The public API reads only the blueprint's id.
    private void putGroup(StateServerGroup stateServerGroup) {
        String summaryJson = stateServerGroup.getSummaryJson().toStringUtf8();
        ModelsServerGroupSummary summary = JSON.deserialize(summaryJson, ModelsServerGroupSummary.class);
        String infoJson = stateServerGroup.getInfoJson().toStringUtf8();
        ModelsServerGroupInfo info = JSON.deserialize(infoJson, ModelsServerGroupInfo.class);
        boolean showsBlueprint = stateServerGroup.hasBlueprintId() && info.getSource() != null;
        if (showsBlueprint) {
            ModelsBlueprintInfo blueprint = new ModelsBlueprintInfo().id(toUuidText(stateServerGroup.getBlueprintId()));
            info.getSource().setBlueprint(blueprint);
        }
        Instant createdAt = Instant.parse(summary.getCreatedAt());
        groupItemsById.put(toUuidText(stateServerGroup.getId()), new GroupItem(summary, info, createdAt));
    }

    private void putPersistentServer(StatePersistentServer statePersistentServer) {
        String summaryJson = statePersistentServer.getSummaryJson().toStringUtf8();
        ModelsPersistentServerSummary summary = JSON.deserialize(summaryJson, ModelsPersistentServerSummary.class);
        String infoJson = statePersistentServer.getInfoJson().toStringUtf8();
        ModelsPersistentServerInfo info = JSON.deserialize(infoJson, ModelsPersistentServerInfo.class);
        Instant createdAt = Instant.parse(summary.getCreatedAt());
        persistentServerItemsById.put(toUuidText(statePersistentServer.getId()), new PersistentServerItem(summary, info, createdAt));
    }

    private ModelsServerSummary buildServerSummary(ServerItem serverItem) {
        StateServer stateServer = serverItem.stateServer();
        String id = toUuidText(stateServer.getId());
        ModelsServerSummary summary = new ModelsServerSummary();
        summary.setServerId(id);
        summary.setNetworkId(networkId);
        if (stateServer.hasServerGroupId()) {
            String groupId = toUuidText(stateServer.getServerGroupId());
            summary.setServerGroupId(groupId);
            GroupItem groupItem = groupItemsById.get(groupId);
            if (groupItem != null) {
                summary.setServerGroup(groupItem.infoInsideServers());
            }
        }
        if (stateServer.hasPersistentServerId()) {
            String persistentServerId = toUuidText(stateServer.getPersistentServerId());
            summary.setPersistentServerId(persistentServerId);
            PersistentServerItem persistentServerItem = persistentServerItemsById.get(persistentServerId);
            if (persistentServerItem != null) {
                summary.setPersistentServer(persistentServerItem.infoInsideServers());
            }
        }
        if (stateServer.hasServerhostId()) {
            summary.setServerhostId(toUuidText(stateServer.getServerhostId()));
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
        summary.setProperties(serverItem.properties());
        Instant createdAt = microsToInstant(stateServer.getCreatedAtMicros());
        summary.setCreatedAt(formatTimestamp(createdAt));
        Instant updatedAt = microsToInstant(stateServer.getUpdatedAtMicros());
        summary.setUpdatedAt(formatTimestamp(updatedAt));
        fillMetrics(summary, metricsByServerId.get(id));
        return summary;
    }

    // Without fresh metrics the controller shows zeros and an empty last activity.
    private static void fillMetrics(ModelsServerSummary summary, @Nullable StateServerMetrics serverMetrics) {
        if (serverMetrics == null) {
            summary.setPlayerCount(0);
            summary.setCpuUsage(BigDecimal.ZERO);
            summary.setMemoryUsage(BigDecimal.ZERO);
            summary.setLastActivity("");
            return;
        }
        summary.setPlayerCount(serverMetrics.getPlayerCount());
        summary.setCpuUsage(BigDecimal.valueOf(serverMetrics.getCpuUsage()));
        summary.setMemoryUsage(BigDecimal.valueOf(serverMetrics.getMemoryUsage()));
        if (serverMetrics.hasLastActivityNanos()) {
            Instant lastActivity = nanosToInstant(serverMetrics.getLastActivityNanos());
            summary.setLastActivity(formatTimestamp(lastActivity));
        } else {
            summary.setLastActivity("");
        }
    }

    // A new object, because the parsed summary is shared by every contents built from this copy.
    private static ModelsPersistentServerSummary buildPersistentServerSummary(PersistentServerItem persistentServerItem, int playerCount) {
        ModelsPersistentServerSummary parsedSummary = persistentServerItem.summary();
        ModelsPersistentServerSummary summary = new ModelsPersistentServerSummary();
        summary.setActive(parsedSummary.getActive());
        summary.setCreatedAt(parsedSummary.getCreatedAt());
        summary.setMaxMemory(parsedSummary.getMaxMemory());
        summary.setMaxPlayers(parsedSummary.getMaxPlayers());
        summary.setMinMemory(parsedSummary.getMinMemory());
        summary.setName(parsedSummary.getName());
        summary.setPersistentServerId(parsedSummary.getPersistentServerId());
        summary.setPlayerCount(playerCount);
        summary.setPriority(parsedSummary.getPriority());
        summary.setProperties(parsedSummary.getProperties());
        summary.setServerhostId(parsedSummary.getServerhostId());
        summary.setSource(parsedSummary.getSource());
        summary.setTags(parsedSummary.getTags());
        summary.setType(parsedSummary.getType());
        summary.setUpdatedAt(parsedSummary.getUpdatedAt());
        summary.setWorkflows(parsedSummary.getWorkflows());
        return summary;
    }

    // The controller counts the players of servers that are AVAILABLE or INGAME.
    private static Map<String, Integer> countPlayersByPersistentServerId(List<ModelsServerSummary> serverSummaries) {
        Map<String, Integer> playerCountsByPersistentServerId = new HashMap<>();
        for (ModelsServerSummary serverSummary : serverSummaries) {
            ModelsServerSummary.StateEnum state = serverSummary.getState();
            boolean isCounted = state == ModelsServerSummary.StateEnum.AVAILABLE || state == ModelsServerSummary.StateEnum.INGAME;
            String persistentServerId = serverSummary.getPersistentServerId();
            if (isCounted && persistentServerId != null) {
                int countedSoFar = playerCountsByPersistentServerId.getOrDefault(persistentServerId, 0);
                playerCountsByPersistentServerId.put(persistentServerId, countedSoFar + serverSummary.getPlayerCount());
            }
        }
        return playerCountsByPersistentServerId;
    }

    // Group id with servers without a group last, then numerical id, then id.
    private static int compareServerSummaries(ModelsServerSummary first, ModelsServerSummary second) {
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

    private static int compareGroupItems(GroupItem first, GroupItem second) {
        return compareNewestFirst(first.createdAt(), first.summary().getServerGroupId(),
                second.createdAt(), second.summary().getServerGroupId());
    }

    private static int comparePersistentServerItems(PersistentServerItem first, PersistentServerItem second) {
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
    private static String toUuidText(ByteString id) {
        ByteBuffer bytes = id.asReadOnlyByteBuffer();
        UUID uuid = new UUID(bytes.getLong(), bytes.getLong());
        return uuid.toString();
    }

    private static Instant microsToInstant(long micros) {
        long seconds = Math.floorDiv(micros, 1_000_000L);
        long microsOfSecond = Math.floorMod(micros, 1_000_000L);
        return Instant.ofEpochSecond(seconds, microsOfSecond * 1_000L);
    }

    private static Instant nanosToInstant(long nanos) {
        long seconds = Math.floorDiv(nanos, 1_000_000_000L);
        long nanosOfSecond = Math.floorMod(nanos, 1_000_000_000L);
        return Instant.ofEpochSecond(seconds, nanosOfSecond);
    }

    private static String formatTimestamp(Instant time) {
        return TIMESTAMP_FORMAT.format(time);
    }
}
