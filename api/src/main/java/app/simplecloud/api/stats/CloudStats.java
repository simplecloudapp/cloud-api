package app.simplecloud.api.stats;

/**
 * Network-wide statistics.
 *
 * @param servers           server counts
 * @param players           player usage
 * @param serverGroups      server group counts
 * @param persistentServers persistent server counts
 * @param serverhosts       serverhost counts and capacity
 * @param resources         allocated and used resources
 * @param health            fleet health
 */
public record CloudStats(
        ServerStats servers,
        PlayerStats players,
        CountStats serverGroups,
        CountStats persistentServers,
        ServerhostStats serverhosts,
        ResourceStats resources,
        HealthStats health) {

    /**
     * @param total  the total number of servers
     * @param active the number of active servers
     * @param byState the number of servers per state
     */
    public record ServerStats(int total, int active, ServersByState byState) {}

    public record ServersByState(
            int queued, int preparing, int starting, int available, int ingame, int stopping, int stopped) {}

    /**
     * @param online             the number of online players
     * @param capacity           the summed max players of all servers
     * @param utilizationPercent online players relative to capacity, in percent
     */
    public record PlayerStats(int online, int capacity, double utilizationPercent) {}

    public record CountStats(int total, int active) {}

    /**
     * @param total                 the total number of serverhosts
     * @param active                the number of active serverhosts
     * @param totalMemoryCapacityMb the summed memory capacity of all serverhosts in MB
     */
    public record ServerhostStats(int total, int active, int totalMemoryCapacityMb) {}

    /**
     * @param memoryAllocatedMb the memory allocated to servers in MB
     * @param memoryUsedMb      the memory used by servers in MB
     * @param averageCpuPercent the average CPU usage of servers in percent
     */
    public record ResourceStats(int memoryAllocatedMb, double memoryUsedMb, double averageCpuPercent) {}

    /**
     * @param status               the controller's health status
     * @param healthyServerPercent the share of healthy servers in percent
     */
    public record HealthStats(String status, double healthyServerPercent) {}
}
