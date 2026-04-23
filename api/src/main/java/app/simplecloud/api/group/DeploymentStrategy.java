package app.simplecloud.api.group;

/**
 * Represents the strategy for deploying servers to hosts.
 */
public enum DeploymentStrategy {
    /**
     * Prefer only the explicitly configured hosts.
     */
    WHITELIST,

    /**
     * Exclude the explicitly configured hosts and allow all others.
     */
    BLACKLIST,

    /**
     * Deploy servers to hosts randomly.
     */
    @Deprecated
    RANDOM,

    /**
     * Deploy servers to hosts based on priority (hosts with lower priority numbers are preferred).
     */
    @Deprecated
    PRIORITY,

    /**
     * Deploy servers to hosts in a round-robin fashion, distributing load evenly.
     */
    @Deprecated
    ROUND_ROBIN
}
