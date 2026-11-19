package app.simplecloud.api.internal.networkstate.read;

import app.simplecloud.api.internal.networkstate.NetworkCopy;
import app.simplecloud.api.web.models.ModelsBlueprintInfo;
import app.simplecloud.api.web.models.ModelsPersistentServerInfo;
import app.simplecloud.api.web.models.ModelsServerGroupInfo;
import app.simplecloud.api.web.models.ModelsServerSummary;
import app.simplecloud.api.web.models.ModelsSourceInfo;
import build.buf.gen.simplecloud.controller.v2.StateBlueprint;
import build.buf.gen.simplecloud.controller.v2.StatePersistentServer;
import build.buf.gen.simplecloud.controller.v2.StateServer;
import build.buf.gen.simplecloud.controller.v2.StateServerGroup;
import build.buf.gen.simplecloud.controller.v2.StateServerMetrics;
import com.google.protobuf.ByteString;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Builds the server summaries of one copy as the controller's server list shows them ({@code buildServerSummary}
 * in {@code internal/web/server_mapper.go}). Reads each group, persistent server and blueprint from its JSON
 * once, however many servers it has.
 */
final class ServerSummaries {

    private final NetworkCopy copy;
    private final String networkIdText;
    private final Map<ByteString, ModelsServerGroupInfo> groups;
    private final Map<ByteString, ModelsPersistentServerInfo> persistentServers;

    /**
     * @param networkId the copy's network, a UUID, which the rows do not carry
     * @throws IllegalStateException if a group's blueprint does not resolve in the copy, which a view's copy
     *                               rules out, or a group that names a blueprint has no source
     */
    ServerSummaries(NetworkCopy copy, String networkId) {
        this.copy = copy;
        this.networkIdText = UUID.fromString(networkId).toString();
        this.groups = groupInfos(copy);
        this.persistentServers = persistentServerInfos(copy);
    }

    /**
     * Sets the members that come from the row and leaves a NULL column unset, as Go's {@code omitempty}
     * leaves out its member.
     *
     * @throws IllegalStateException if the server's group or persistent server does not resolve in the copy,
     *                               which a view's copy rules out
     */
    ModelsServerSummary of(StateServer row) {
        ModelsServerSummary summary = new ModelsServerSummary();
        summary.setServerId(ControllerIds.text(row.getId()));
        summary.setNetworkId(networkIdText);
        if (row.hasServerGroupId()) {
            summary.setServerGroupId(ControllerIds.text(row.getServerGroupId()));
            summary.setServerGroup(resolve(groups, row.getServerGroupId(), "server group"));
        }
        if (row.hasPersistentServerId()) {
            summary.setPersistentServerId(ControllerIds.text(row.getPersistentServerId()));
            summary.setPersistentServer(resolve(persistentServers, row.getPersistentServerId(), "persistent server"));
        }
        if (row.hasServerhostId()) {
            summary.setServerhostId(ControllerIds.text(row.getServerhostId()));
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
        summary.setProperties(ControllerJson.toProperties(row.getPropertiesJson()));
        summary.setCreatedAt(ControllerTimestamps.ofMicros(row.getCreatedAtMicros()));
        summary.setUpdatedAt(ControllerTimestamps.ofMicros(row.getUpdatedAtMicros()));
        setMetrics(summary, row, copy);
        return summary;
    }

    /**
     * Sets zeros and an empty last activity for a server the pulse left out, as the controller does for stale
     * metrics.
     */
    private static void setMetrics(ModelsServerSummary summary, StateServer row, NetworkCopy copy) {
        StateServerMetrics metrics = copy.metricsOf(row.getId());
        summary.setPlayerCount(Math.toIntExact(PlayerCounts.of(metrics)));
        summary.setCpuUsage(finiteOrNull(metrics.getCpuUsage()));
        summary.setMemoryUsage(finiteOrNull(metrics.getMemoryUsage()));
        String lastActivity = metrics.hasLastActivityNanos()
                ? ControllerTimestamps.ofNanos(metrics.getLastActivityNanos())
                : "";
        summary.setLastActivity(lastActivity);
    }

    private static @Nullable BigDecimal finiteOrNull(double metric) {
        return Double.isFinite(metric) ? BigDecimal.valueOf(metric) : null;
    }

    private static Map<ByteString, ModelsServerGroupInfo> groupInfos(NetworkCopy copy) {
        Map<ByteString, ModelsBlueprintInfo> blueprints = new HashMap<>();
        Map<ByteString, ModelsServerGroupInfo> infos = new HashMap<>();
        for (StateServerGroup group : copy.serverGroups().values()) {
            infos.put(group.getId(), groupInfo(group, blueprints, copy));
        }
        return infos;
    }

    /**
     * Joins the group's blueprint into its info, where the controller's join puts it: into {@code source},
     * whenever the group names a blueprint, also for an image source.
     */
    private static ModelsServerGroupInfo groupInfo(
            StateServerGroup group,
            Map<ByteString, ModelsBlueprintInfo> blueprintsReadSoFar,
            NetworkCopy copy
    ) {
        ModelsServerGroupInfo info = ControllerJson.toModel(group.getInfoJson(), ModelsServerGroupInfo.class);
        if (!group.hasBlueprintId()) {
            return info;
        }
        ModelsSourceInfo source = info.getSource();
        if (source == null) {
            throw new IllegalStateException("Server group " + ControllerIds.text(group.getId()) + " has no source");
        }
        ModelsBlueprintInfo blueprint = blueprintsReadSoFar.computeIfAbsent(group.getBlueprintId(),
                blueprintId -> blueprintInfo(blueprintId, copy));
        source.setBlueprint(blueprint);
        return info;
    }

    private static ModelsBlueprintInfo blueprintInfo(ByteString blueprintId, NetworkCopy copy) {
        StateBlueprint blueprint = resolve(copy.blueprints(), blueprintId, "blueprint");
        return ControllerJson.toModel(blueprint.getInfoJson(), ModelsBlueprintInfo.class);
    }

    private static Map<ByteString, ModelsPersistentServerInfo> persistentServerInfos(NetworkCopy copy) {
        Map<ByteString, ModelsPersistentServerInfo> infos = new HashMap<>();
        for (StatePersistentServer persistentServer : copy.persistentServers().values()) {
            ModelsPersistentServerInfo info = ControllerJson.toModel(persistentServer.getInfoJson(),
                    ModelsPersistentServerInfo.class);
            infos.put(persistentServer.getId(), info);
        }
        return infos;
    }

    /**
     * Follows a reference between items of a copy. A view checks after every batch and snapshot that each one
     * resolves, so a missing item here is a bug in the view, not a state of the network.
     */
    private static <T> T resolve(Map<ByteString, T> items, ByteString id, String kind) {
        T item = items.get(id);
        if (item == null) {
            throw new IllegalStateException("The copy has no " + kind + " " + ControllerIds.text(id));
        }
        return item;
    }
}
