package app.simplecloud.api.group;

import app.simplecloud.api.server.ServerState;

import java.util.Map;

/**
 * Server and player counts of a server group, computed by the controller when the group was loaded.
 *
 * @param totalServers the number of servers of the group
 * @param serversByState the number of servers per state; states this API version doesn't know are left out
 * @param onlinePlayers the number of players on the group's servers
 */
public record GroupStats(int totalServers, Map<ServerState, Integer> serversByState, int onlinePlayers) {
    public GroupStats {
        serversByState = Map.copyOf(serversByState);
    }
}
