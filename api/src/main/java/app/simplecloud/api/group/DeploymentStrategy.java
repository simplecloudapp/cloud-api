package app.simplecloud.api.group;

/**
 * Represents the strategy for deploying servers to hosts.
 */
public enum DeploymentStrategy {
    /**
     * Deploy servers to hosts randomly.
     */
    RANDOM,

    /**
     * Deploy servers to hosts based on priority (hosts with lower priority numbers are preferred).
     */
    PRIORITY,

    /**
     * Deploy servers to hosts in a round-robin fashion, distributing load evenly.
     */
    ROUND_ROBIN
}

