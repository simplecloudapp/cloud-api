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
    SERVERS,

    /**
     * Scale based on player count and thresholds.
     *
     * @deprecated the controller does not support this mode and rejects it; use {@link #SERVERS}
     */
    @Deprecated
    PLAYERS,

    /**
     * Manual scaling only, no automatic scaling.
     *
     * @deprecated the controller does not support this mode and rejects it
     */
    @Deprecated
    MANUAL
}
