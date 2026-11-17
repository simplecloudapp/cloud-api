package app.simplecloud.api.internal.networkstate.read;

import app.simplecloud.api.internal.networkstate.NetworkCopy;
import build.buf.gen.simplecloud.controller.v2.StateServer;
import build.buf.gen.simplecloud.controller.v2.StateServerMetrics;

/**
 * The player counts the controller shows for a server and adds up for a group's stats and a persistent
 * server's player count.
 */
final class OnlinePlayers {

    private OnlinePlayers() {
    }

    /**
     * Returns whether the controller counts the players of a server in this state
     * ({@code GetRunningServersByGroups}, {@code ListActiveServersForPersistentServerPlayerCount}).
     */
    static boolean areCounted(String state) {
        return state.equals("AVAILABLE") || state.equals("INGAME");
    }

    /**
     * Returns the server's player count from the copy's metrics, or 0 for a server whose metrics are not
     * fresh, which the pulse leaves out as the controller leaves out stale metrics.
     */
    static long reportedBy(StateServer server, NetworkCopy copy) {
        StateServerMetrics metrics = copy.metrics().get(server.getId());
        if (metrics == null) {
            return 0;
        }
        return Integer.toUnsignedLong(metrics.getPlayerCount());
    }
}
