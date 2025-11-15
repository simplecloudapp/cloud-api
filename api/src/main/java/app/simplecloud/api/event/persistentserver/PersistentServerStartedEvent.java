package app.simplecloud.api.event.persistentserver;

import org.jetbrains.annotations.Nullable;

/**
 * Event fired when a persistent server starts.
 */
public interface PersistentServerStartedEvent {
    
    /**
     * Returns the network ID this event belongs to.
     *
     * @return the network ID
     */
    String getNetworkId();
    
    /**
     * Returns the ID of the started persistent server.
     *
     * @return the persistent server ID
     */
    String getPersistentServerId();
    
    /**
     * Returns the ID of the associated server instance.
     *
     * @return the server ID, or null if not available
     */
    @Nullable String getServerId();
    
    /**
     * Returns the timestamp when this event occurred (ISO 8601 format).
     *
     * @return the timestamp
     */
    String getTimestamp();
}

