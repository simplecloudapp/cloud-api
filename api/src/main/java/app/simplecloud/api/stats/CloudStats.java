package app.simplecloud.api.stats;

import app.simplecloud.api.server.ServerState;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Statistics of the whole network.
 *
 * @param health the overall health
 * @param servers the server counts
 * @param groups the server group counts
 * @param persistentServers the persistent server counts
 * @param serverhosts the serverhost counts
 * @param players the player counts
 * @param resources the resource usage
 */
public record CloudStats(
        Health health,
        Servers servers,
        Count groups,
        Count persistentServers,
        Serverhosts serverhosts,
        Players players,
        Resources resources) {

    /**
     * @param status the health status as reported by the controller, or null if not available
     * @param healthyServerPercent the share of servers that are healthy, from 0 to 100
     */
    public record Health(@Nullable String status, double healthyServerPercent) {}

    /**
     * @param total the number of servers
     * @param active the number of active servers
     * @param byState the number of servers per state
     */
    public record Servers(int total, int active, Map<ServerState, Integer> byState) {
        public Servers {
            byState = Map.copyOf(byState);
        }
    }

    /**
     * @param total the number of configurations
     * @param active the number of active configurations
     */
    public record Count(int total, int active) {}

    /**
     * @param total the number of serverhosts
     * @param active the number of active serverhosts
     * @param totalMemoryCapacityMb the memory of all serverhosts in MB
     */
    public record Serverhosts(int total, int active, int totalMemoryCapacityMb) {}

    /**
     * @param online the number of online players
     * @param capacity the player capacity of all servers
     * @param utilizationPercent the share of the capacity in use, from 0 to 100
     */
    public record Players(int online, int capacity, double utilizationPercent) {}

    /**
     * @param memoryAllocatedMb the memory allocated to servers in MB
     * @param memoryUsedMb the memory used by servers in MB
     * @param averageCpuPercent the average CPU usage of all servers
     */
    public record Resources(int memoryAllocatedMb, double memoryUsedMb, double averageCpuPercent) {}
}
