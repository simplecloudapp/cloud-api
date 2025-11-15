package app.simplecloud.api.group;

/**
 * Represents the scaling mode for server groups.
 */
public enum ScalingMode {
    /** Scale based on available slots (free server capacity). */
    SLOTS,
    
    /** Scale based on player count and thresholds. */
    PLAYERS,
    
    /** Manual scaling only, no automatic scaling. */
    MANUAL
}

