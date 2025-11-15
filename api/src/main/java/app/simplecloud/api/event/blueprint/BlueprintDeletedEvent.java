package app.simplecloud.api.event.blueprint;

import org.jetbrains.annotations.Nullable;

/**
 * Event fired when a blueprint is deleted.
 */
public interface BlueprintDeletedEvent {
    
    /**
     * Returns the network ID this event belongs to.
     *
     * @return the network ID
     */
    String getNetworkId();
    
    /**
     * Returns the ID of the deleted blueprint.
     *
     * @return the blueprint ID
     */
    String getBlueprintId();
    
    /**
     * Returns the name of the deleted blueprint.
     *
     * @return the blueprint name, or null if not available
     */
    @Nullable String getName();
    
    /**
     * Returns the timestamp when this event occurred (ISO 8601 format).
     *
     * @return the timestamp
     */
    String getTimestamp();
}

