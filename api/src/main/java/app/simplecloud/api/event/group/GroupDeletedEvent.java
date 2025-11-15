package app.simplecloud.api.event.group;

import org.jetbrains.annotations.Nullable;

/**
 * Event fired when a server group is deleted.
 */
public interface GroupDeletedEvent {
    
    /**
     * Returns the network ID this event belongs to.
     *
     * @return the network ID
     */
    String getNetworkId();
    
    /**
     * Returns the ID of the deleted server group.
     *
     * @return the server group ID
     */
    String getServerGroupId();
    
    /**
     * Returns the name of the deleted server group.
     *
     * @return the group name, or null if not available
     */
    @Nullable String getName();
    
    /**
     * Returns the timestamp when this event occurred (ISO 8601 format).
     *
     * @return the timestamp
     */
    String getTimestamp();
}

