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
     *
     * @deprecated not supported by the controller; will be removed in 1.0.0
     */
    @Deprecated(forRemoval = true)
    RANDOM,

    /**
     * Deploy servers to hosts based on priority (hosts with lower priority numbers are preferred).
     *
     * @deprecated not supported by the controller; will be removed in 1.0.0
     */
    @Deprecated(forRemoval = true)
    PRIORITY,

    /**
     * Deploy servers to hosts in a round-robin fashion, distributing load evenly.
     *
     * @deprecated not supported by the controller; will be removed in 1.0.0
     */
    @Deprecated(forRemoval = true)
    ROUND_ROBIN
}
