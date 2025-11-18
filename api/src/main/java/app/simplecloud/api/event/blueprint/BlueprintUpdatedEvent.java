package app.simplecloud.api.event.blueprint;

import app.simplecloud.api.blueprint.Blueprint;

/**
 * Event fired when a blueprint is updated.
 */
public interface BlueprintUpdatedEvent {

    /**
     * Returns the network ID this event belongs to.
     *
     * @return the network ID
     */
    String getNetworkId();

    /**
     * Returns the ID of the updated blueprint.
     *
     * @return the blueprint ID
     */
    String getBlueprintId();

    /**
     * Returns the updated blueprint.
     *
     * @return the blueprint
     * @throws IllegalStateException if blueprint data is not available
     */
    Blueprint getBlueprint();

    /**
     * Returns the timestamp when this event occurred (ISO 8601 format).
     *
     * @return the timestamp
     */
    String getTimestamp();
}

