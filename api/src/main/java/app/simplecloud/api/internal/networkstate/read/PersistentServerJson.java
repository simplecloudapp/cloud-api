package app.simplecloud.api.internal.networkstate.read;

import app.simplecloud.api.internal.networkstate.NetworkCopy;
import build.buf.gen.simplecloud.controller.v2.StatePersistentServer;
import build.buf.gen.simplecloud.controller.v2.StateServer;
import com.google.gson.JsonObject;
import com.google.protobuf.ByteString;

/**
 * Renders a persistent server as the controller's persistent server list does: the item's
 * {@code summary_json} with the {@code player_count} the copy computes, because it changes with every server.
 */
final class PersistentServerJson {

    private PersistentServerJson() {
    }

    static JsonObject render(StatePersistentServer persistentServer, NetworkCopy copy) {
        JsonObject json = ControllerJson.parseObject(persistentServer.getSummaryJson());
        json.addProperty("player_count", playerCount(persistentServer.getId(), copy));
        return json;
    }

    /**
     * Adds up the players of the persistent server's counted servers, as
     * {@code loadPersistentServerPlayerCountsFromMetrics} in {@code internal/web/persistent_servers.go} does.
     * It skips counts of 0 or less; the pulse carries no count below 0, so every count adds as it is.
     */
    private static long playerCount(ByteString persistentServerId, NetworkCopy copy) {
        long playerCount = 0;
        for (StateServer server : copy.servers().values()) {
            boolean ofPersistentServer = server.hasPersistentServerId()
                    && server.getPersistentServerId().equals(persistentServerId);
            if (ofPersistentServer && OnlinePlayers.areCounted(server.getState())) {
                playerCount += OnlinePlayers.reportedBy(server, copy);
            }
        }
        return playerCount;
    }
}
