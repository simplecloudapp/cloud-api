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
    BLACKLIST
}
