package app.simplecloud.api.event.group;

import app.simplecloud.api.group.Group;

/**
 * Event fired when a server group is created.
 */
public interface GroupCreatedEvent {

    /**
     * Returns the network ID this event belongs to.
     *
     * @return the network ID
     */
    String getNetworkId();

    /**
     * Returns the ID of the created server group.
     *
     * @return the server group ID
     */
    String getServerGroupId();

    /**
     * Returns the created server group.
     *
     * @return the group
     * @throws IllegalStateException if group data is not available
     */
    Group getGroup();

    /**
     * Returns the timestamp when this event occurred (ISO 8601 format).
     *
     * @return the timestamp
     */
    String getTimestamp();
}
