package app.simplecloud.api.player;

import app.simplecloud.api.group.Group;
import app.simplecloud.api.internal.player.ConnectTargetFields;
import app.simplecloud.api.internal.player.ConnectTargetFields.Kind;
import app.simplecloud.api.persistentserver.PersistentServer;
import app.simplecloud.api.server.Server;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Where {@link PlayerApi#connectTo(java.util.UUID, ConnectTarget)} sends a player.
 *
 * <p>Names and IDs are both strings, so each factory method says in its name which one it takes. The
 * methods that take a {@link Server}, {@link Group} or {@link PersistentServer} send its ID.
 *
 * <p>A target from {@link #registeredServerName(String)} goes straight to the player's proxy. Every other
 * target goes through the controller, which picks a game server and sends the player's proxy its ID.
 *
 * <p>The factory methods throw a {@link NullPointerException} for a null argument. Later releases may add
 * kinds of targets.
 */
public final class ConnectTarget {

    static {
        ConnectTargetFields.registerReader(target -> target.fields);
    }

    private final ConnectTargetFields fields;

    private ConnectTarget(Kind kind, String nameOrId, @Nullable ServerSelectionMode selectionMode) {
        Objects.requireNonNull(nameOrId, kind.parameterName());
        this.fields = new ConnectTargetFields(kind, nameOrId, selectionMode);
    }

    /**
     * Targets a server.
     *
     * @param server the server
     * @return the target
     */
    public static ConnectTarget server(Server server) {
        Objects.requireNonNull(server, "server");
        return serverId(server.getServerId());
    }

    /**
     * Targets a server by its ID.
     *
     * @param serverId the UUID of the server
     * @return the target
     */
    public static ConnectTarget serverId(String serverId) {
        return new ConnectTarget(Kind.SERVER_ID, serverId, null);
    }

    /**
     * Targets a server by its cloud name: {@code <group>-<number>} for a group server, or a persistent
     * server's name, matched case-insensitively. This is the name the cloud gives the server, which can
     * differ from the name a proxy registered it under. A server ID passed here is looked up as a name, so
     * it answers {@link ConnectStatus#TARGET_NOT_FOUND}.
     *
     * @param serverName the cloud name of the server
     * @return the target
     */
    public static ConnectTarget serverName(String serverName) {
        return new ConnectTarget(Kind.SERVER_NAME, serverName, null);
    }

    /**
     * Targets a server of a group, chosen by the controller's default selection mode, which is
     * {@link ServerSelectionMode#RANDOM} today.
     *
     * @param group the group
     * @return the target
     */
    public static ConnectTarget group(Group group) {
        return group(group, null);
    }

    /**
     * Targets a server of a group.
     *
     * @param group the group
     * @param selectionMode how the server is chosen, or null for the controller's default
     * @return the target
     */
    public static ConnectTarget group(Group group, @Nullable ServerSelectionMode selectionMode) {
        Objects.requireNonNull(group, "group");
        return groupId(group.getServerGroupId(), selectionMode);
    }

    /**
     * Targets a server of a group, found by the group's ID, chosen by the controller's default selection
     * mode.
     *
     * @param groupId the UUID of the group
     * @return the target
     */
    public static ConnectTarget groupId(String groupId) {
        return groupId(groupId, null);
    }

    /**
     * Targets a server of a group, found by the group's ID.
     *
     * @param groupId the UUID of the group
     * @param selectionMode how the server is chosen, or null for the controller's default
     * @return the target
     */
    public static ConnectTarget groupId(String groupId, @Nullable ServerSelectionMode selectionMode) {
        return new ConnectTarget(Kind.GROUP_ID, groupId, selectionMode);
    }

    /**
     * Targets a server of a group, found by the group's name, matched case-insensitively, chosen by the
     * controller's default selection mode.
     *
     * @param groupName the name of the group
     * @return the target
     */
    public static ConnectTarget groupName(String groupName) {
        return groupName(groupName, null);
    }

    /**
     * Targets a server of a group, found by the group's name, matched case-insensitively.
     *
     * @param groupName the name of the group
     * @param selectionMode how the server is chosen, or null for the controller's default
     * @return the target
     */
    public static ConnectTarget groupName(String groupName, @Nullable ServerSelectionMode selectionMode) {
        return new ConnectTarget(Kind.GROUP_NAME, groupName, selectionMode);
    }

    /**
     * Targets the running server of a persistent server.
     *
     * @param persistentServer the persistent server
     * @return the target
     */
    public static ConnectTarget persistentServer(PersistentServer persistentServer) {
        Objects.requireNonNull(persistentServer, "persistentServer");
        return persistentServerId(persistentServer.getPersistentServerId());
    }

    /**
     * Targets the running server of a persistent server, found by the persistent server's ID. To target a
     * persistent server by its name, use {@link #serverName(String)}.
     *
     * @param persistentServerId the UUID of the persistent server
     * @return the target
     */
    public static ConnectTarget persistentServerId(String persistentServerId) {
        return new ConnectTarget(Kind.PERSISTENT_SERVER_ID, persistentServerId, null);
    }

    /**
     * Targets a server by the name under which the player's proxy registered it. The request goes straight
     * to the player's proxy, without the controller, so it also reaches servers the cloud doesn't manage.
     *
     * @param registeredServerName the name of the server on the player's proxy
     * @return the target
     */
    public static ConnectTarget registeredServerName(String registeredServerName) {
        return new ConnectTarget(Kind.REGISTERED_SERVER_NAME, registeredServerName, null);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ConnectTarget target)) {
            return false;
        }
        return fields.equals(target.fields);
    }

    @Override
    public int hashCode() {
        return fields.hashCode();
    }

    /**
     * Describes the target for logs. The format is not part of the API.
     *
     * @return the description
     */
    @Override
    public String toString() {
        ServerSelectionMode selectionMode = fields.selectionMode();
        String mode = selectionMode != null ? ", selectionMode=" + selectionMode : "";
        return "ConnectTarget[" + fields.kind().parameterName() + "=" + fields.nameOrId() + mode + "]";
    }
}
