package app.simplecloud.api.event.persistentserver;

import org.jetbrains.annotations.Nullable;

/**
 * Event fired when a persistent server is updated.
 */
public interface PersistentServerUpdatedEvent {

    /**
     * Returns the network ID this event belongs to.
     *
     * @return the network ID
     */
    String getNetworkId();

    /**
     * Returns the ID of the updated persistent server.
     *
     * @return the persistent server ID
     */
    String getPersistentServerId();

    /**
     * Returns the name of the updated persistent server.
     *
     * @return the persistent server name, or null if not available
     */
    @Nullable String getName();

    /**
     * Returns the timestamp when this event occurred (ISO 8601 format).
     *
     * @return the timestamp
     */
    String getTimestamp();
}

