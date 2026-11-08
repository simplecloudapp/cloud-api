package app.simplecloud.api.player;

import java.util.Objects;

/**
 * Where the controller should send a player, see {@link PlayerApi#connectTo(java.util.UUID, ConnectTarget)}.
 *
 * <p>The controller resolves every target to one server and tells the proxy to send the player there.
 */
public sealed interface ConnectTarget {

    /**
     * A server by its cloud name: a persistent server's name, or {@code <group>-<numerical id>} for a group
     * server. The name is matched case-insensitively. It is the name the cloud gives the server, not the
     * name a proxy registered it under. A server UUID is not accepted here, use {@link ServerId} instead.
     *
     * @param serverName the cloud name of the server
     */
    record ServerName(String serverName) implements ConnectTarget {
        public ServerName {
            Objects.requireNonNull(serverName, "serverName");
        }
    }

    /**
     * A server by its ID.
     *
     * @param serverId the server UUID
     */
    record ServerId(String serverId) implements ConnectTarget {
        public ServerId {
            Objects.requireNonNull(serverId, "serverId");
        }
    }

    /**
     * A server of a server group, looked up by the group's name (case-insensitive).
     *
     * @param groupName the name of the server group
     * @param selectionMode how the server is chosen; null when creating it means {@link ServerSelectionMode#RANDOM},
     *     so the accessor never returns null
     */
    record GroupName(String groupName, ServerSelectionMode selectionMode) implements ConnectTarget {
        public GroupName {
            Objects.requireNonNull(groupName, "groupName");
            selectionMode = selectionMode != null ? selectionMode : ServerSelectionMode.RANDOM;
        }
    }

    /**
     * A server of a server group, looked up by the group's ID.
     *
     * @param groupId the server group UUID
     * @param selectionMode how the server is chosen; null when creating it means {@link ServerSelectionMode#RANDOM},
     *     so the accessor never returns null
     */
    record GroupId(String groupId, ServerSelectionMode selectionMode) implements ConnectTarget {
        public GroupId {
            Objects.requireNonNull(groupId, "groupId");
            selectionMode = selectionMode != null ? selectionMode : ServerSelectionMode.RANDOM;
        }
    }

    /**
     * The running instance of a persistent server.
     *
     * @param persistentServerId the persistent server UUID
     */
    record PersistentServerId(String persistentServerId) implements ConnectTarget {
        public PersistentServerId {
            Objects.requireNonNull(persistentServerId, "persistentServerId");
        }
    }
}
