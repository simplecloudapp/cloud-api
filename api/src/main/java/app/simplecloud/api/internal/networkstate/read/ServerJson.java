package app.simplecloud.api.internal.networkstate.read;

import app.simplecloud.api.internal.networkstate.NetworkCopy;
import build.buf.gen.simplecloud.controller.v2.StateBlueprint;
import build.buf.gen.simplecloud.controller.v2.StatePersistentServer;
import build.buf.gen.simplecloud.controller.v2.StateServer;
import build.buf.gen.simplecloud.controller.v2.StateServerGroup;
import build.buf.gen.simplecloud.controller.v2.StateServerMetrics;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.protobuf.ByteString;

/**
 * Renders a server as the controller's server list does ({@code buildServerSummary} in
 * {@code internal/web/server_mapper.go}), from five parts of a copy: the server's row, its group's info, the
 * group's blueprint, its persistent server's info, and its metrics. The group and persistent server info
 * come rendered by the controller, so only the row's own fields and the metrics are mapped here.
 */
final class ServerJson {

    private ServerJson() {
    }

    /**
     * @param networkId the copy's network, which the rows do not carry
     * @throws IllegalStateException if a reference of the server does not resolve in the copy, which a view's
     *                               copy rules out
     * @throws IllegalArgumentException if a metric is not a finite number, which the controller cannot encode
     */
    static JsonObject render(StateServer server, NetworkCopy copy, ByteString networkId) {
        JsonObject json = rowJson(server, networkId);
        addMetrics(json, server, copy);
        if (server.hasServerGroupId()) {
            json.add("server_group", groupInfo(server.getServerGroupId(), copy));
        }
        if (server.hasPersistentServerId()) {
            json.add("persistent_server", persistentServerInfo(server.getPersistentServerId(), copy));
        }
        return json;
    }

    /**
     * Writes the members that come from the row, leaving out a NULL column where Go's {@code omitempty}
     * leaves out its member.
     */
    private static JsonObject rowJson(StateServer server, ByteString networkId) {
        JsonObject json = new JsonObject();
        json.addProperty("server_id", ControllerJson.uuid(server.getId()));
        json.addProperty("network_id", ControllerJson.uuid(networkId));
        if (server.hasServerGroupId()) {
            json.addProperty("server_group_id", ControllerJson.uuid(server.getServerGroupId()));
        }
        if (server.hasPersistentServerId()) {
            json.addProperty("persistent_server_id", ControllerJson.uuid(server.getPersistentServerId()));
        }
        if (server.hasServerhostId()) {
            json.addProperty("serverhost_id", ControllerJson.uuid(server.getServerhostId()));
        }
        json.addProperty("numerical_id", server.getNumericalId());
        if (server.hasIp()) {
            json.addProperty("ip", server.getIp());
        }
        if (server.hasPort()) {
            json.addProperty("port", server.getPort());
        }
        json.addProperty("min_memory", server.getMinMemory());
        json.addProperty("max_memory", server.getMaxMemory());
        json.addProperty("max_players", server.getMaxPlayers());
        json.addProperty("state", server.getState());
        json.add("properties", ControllerJson.parse(server.getPropertiesJson()));
        json.addProperty("created_at", ControllerJson.timestamp(ControllerJson.fromMicros(server.getCreatedAtMicros())));
        json.addProperty("updated_at", ControllerJson.timestamp(ControllerJson.fromMicros(server.getUpdatedAtMicros())));
        return json;
    }

    /**
     * Writes zeros and an empty last activity for a server the pulse left out, as the controller does for
     * stale metrics.
     */
    private static void addMetrics(JsonObject json, StateServer server, NetworkCopy copy) {
        StateServerMetrics metrics = copy.metrics().getOrDefault(server.getId(), StateServerMetrics.getDefaultInstance());
        json.addProperty("player_count", OnlinePlayers.reportedBy(server, copy));
        json.add("cpu_usage", ControllerJson.goFloat(metrics.getCpuUsage()));
        json.add("memory_usage", ControllerJson.goFloat(metrics.getMemoryUsage()));
        String lastActivity = metrics.hasLastActivityNanos()
                ? ControllerJson.timestamp(ControllerJson.fromNanos(metrics.getLastActivityNanos()))
                : "";
        json.addProperty("last_activity", lastActivity);
    }

    /**
     * Joins the group's blueprint into its info, where the controller's join puts it: into {@code source},
     * whenever the group names a blueprint, also for an image source.
     */
    private static JsonObject groupInfo(ByteString groupId, NetworkCopy copy) {
        StateServerGroup group = CopyReferences.resolve(copy.serverGroups(), groupId, "server group");
        JsonObject info = ControllerJson.parseObject(group.getInfoJson());
        if (group.hasBlueprintId()) {
            StateBlueprint blueprint = CopyReferences.resolve(copy.blueprints(), group.getBlueprintId(), "blueprint");
            JsonObject source = info.getAsJsonObject("source");
            if (source == null) {
                throw new IllegalStateException("Server group " + ControllerJson.uuid(groupId) + " has no source");
            }
            source.add("blueprint", ControllerJson.parse(blueprint.getInfoJson()));
        }
        return info;
    }

    private static JsonElement persistentServerInfo(ByteString persistentServerId, NetworkCopy copy) {
        StatePersistentServer persistentServer = CopyReferences.resolve(copy.persistentServers(), persistentServerId,
                "persistent server");
        return ControllerJson.parse(persistentServer.getInfoJson());
    }
}
