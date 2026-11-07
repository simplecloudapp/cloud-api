package app.simplecloud.api.player;

import java.util.Objects;

/**
 * Where the controller should send a player, see {@link PlayerApi#connectTo(java.util.UUID, ConnectTarget)}.
 *
 * <p>There is one variant for each target the controller's connect request accepts. Each variant's name says
 * whether it holds a name or an ID, because both are strings. Names and IDs must not be null.
 */
public sealed interface ConnectTarget {

    /**
     * A server by its cloud name: a persistent server's name, or {@code <group>-<number>} for a group server,
     * matched case-insensitively. It is the name the cloud gives the server, not the name a proxy registered it
     * under. A server ID is looked up as a name, so it answers {@link CloudPlayer.ConnectResult#SERVER_NOT_FOUND};
     * use {@link ServerId}.
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
     * @param serverId the UUID of the server
     */
    record ServerId(String serverId) implements ConnectTarget {
        public ServerId {
            Objects.requireNonNull(serverId, "serverId");
        }
    }

    /**
     * A server of a server group, with the group found by its name, matched case-insensitively.
     *
     * @param groupName the name of the server group
     * @param selectionMode how the server is chosen; null when creating it means
     *                      {@link ServerSelectionMode#RANDOM}, so the accessor never returns null
     */
    record GroupName(String groupName, ServerSelectionMode selectionMode) implements ConnectTarget {
        public GroupName {
            Objects.requireNonNull(groupName, "groupName");
            selectionMode = selectionMode != null ? selectionMode : ServerSelectionMode.RANDOM;
        }
    }

    /**
     * A server of a server group, with the group found by its ID.
     *
     * @param groupId the UUID of the server group
     * @param selectionMode how the server is chosen; null when creating it means
     *                      {@link ServerSelectionMode#RANDOM}, so the accessor never returns null
     */
    record GroupId(String groupId, ServerSelectionMode selectionMode) implements ConnectTarget {
        public GroupId {
            Objects.requireNonNull(groupId, "groupId");
            selectionMode = selectionMode != null ? selectionMode : ServerSelectionMode.RANDOM;
        }
    }

    /**
     * The running server of a persistent server, with the persistent server found by its ID.
     *
     * @param persistentServerId the UUID of the persistent server
     */
    record PersistentServerId(String persistentServerId) implements ConnectTarget {
        public PersistentServerId {
            Objects.requireNonNull(persistentServerId, "persistentServerId");
        }
    }
}
