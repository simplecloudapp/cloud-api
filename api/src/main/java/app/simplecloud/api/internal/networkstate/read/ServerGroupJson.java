package app.simplecloud.api.internal.networkstate.read;

import app.simplecloud.api.internal.networkstate.NetworkCopy;
import build.buf.gen.simplecloud.controller.v2.StateServer;
import build.buf.gen.simplecloud.controller.v2.StateServerGroup;
import com.google.gson.JsonObject;
import com.google.protobuf.ByteString;

import java.util.Map;
import java.util.TreeMap;

/**
 * Renders a group as the controller's group list does: the group's {@code summary_json} with the
 * {@code stats} the copy computes, because they change with every server.
 */
final class ServerGroupJson {

    private ServerGroupJson() {
    }

    static JsonObject render(StateServerGroup group, NetworkCopy copy) {
        JsonObject json = ControllerJson.parseObject(group.getSummaryJson());
        json.add("stats", stats(group.getId(), copy));
        return json;
    }

    /**
     * Computes the stats as {@code loadServerGroupStats} in {@code internal/web/server_groups.go} does: every
     * server of the group by state, and the players of its counted servers. A group without servers has an
     * empty {@code servers_by_state}.
     */
    private static JsonObject stats(ByteString groupId, NetworkCopy copy) {
        Map<String, Integer> serversByState = new TreeMap<>(ByteOrder.TEXT);
        int totalServers = 0;
        long onlinePlayers = 0;
        for (StateServer server : copy.servers().values()) {
            boolean ofGroup = server.hasServerGroupId() && server.getServerGroupId().equals(groupId);
            if (!ofGroup) {
                continue;
            }
            totalServers++;
            serversByState.merge(server.getState(), 1, Integer::sum);
            if (OnlinePlayers.areCounted(server.getState())) {
                onlinePlayers += OnlinePlayers.reportedBy(server, copy);
            }
        }
        JsonObject stats = new JsonObject();
        stats.addProperty("total_servers", totalServers);
        stats.add("servers_by_state", countsByState(serversByState));
        stats.addProperty("online_players", onlinePlayers);
        return stats;
    }

    /**
     * Keeps the order Go's encoder writes a map in, by key bytes, which the parsed map keeps too.
     */
    private static JsonObject countsByState(Map<String, Integer> serversByState) {
        JsonObject counts = new JsonObject();
        for (Map.Entry<String, Integer> entry : serversByState.entrySet()) {
            counts.addProperty(entry.getKey(), entry.getValue());
        }
        return counts;
    }
}
