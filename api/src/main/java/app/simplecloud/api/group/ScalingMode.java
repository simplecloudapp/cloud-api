package app.simplecloud.api.group;

/**
 * Represents the scaling mode for server groups.
 */
public enum ScalingMode {
    /**
     * Scale based on available slots (free server capacity).
     */
    SLOTS,

    /**
     * Scale based on the number of server instances.
     */
    SERVERS
}

