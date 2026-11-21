package app.simplecloud.api.testing.event;

import app.simplecloud.api.blueprint.Blueprint;
import app.simplecloud.api.event.blueprint.BlueprintCreatedEvent;
import app.simplecloud.api.event.blueprint.BlueprintDeletedEvent;
import app.simplecloud.api.event.blueprint.BlueprintUpdatedEvent;
import app.simplecloud.api.event.group.GroupCreatedEvent;
import app.simplecloud.api.event.group.GroupDeletedEvent;
import app.simplecloud.api.event.group.GroupUpdatedEvent;
import app.simplecloud.api.event.persistentserver.PersistentServerCreatedEvent;
import app.simplecloud.api.event.persistentserver.PersistentServerDeletedEvent;
import app.simplecloud.api.event.persistentserver.PersistentServerStartedEvent;
import app.simplecloud.api.event.persistentserver.PersistentServerStoppedEvent;
import app.simplecloud.api.event.persistentserver.PersistentServerUpdatedEvent;
import app.simplecloud.api.event.player.PlayerClientSettingsUpdatedEvent;
import app.simplecloud.api.event.player.PlayerDisconnectEvent;
import app.simplecloud.api.event.player.PlayerKickEvent;
import app.simplecloud.api.event.player.PlayerLoginEvent;
import app.simplecloud.api.event.player.PlayerProfileChangedEvent;
import app.simplecloud.api.event.player.PlayerServerSwitchEvent;
import app.simplecloud.api.event.server.ServerDeletedEvent;
import app.simplecloud.api.event.server.ServerStartedEvent;
import app.simplecloud.api.event.server.ServerStateChangedEvent;
import app.simplecloud.api.event.server.ServerStoppedEvent;
import app.simplecloud.api.event.server.ServerUpdatedEvent;
import app.simplecloud.api.group.Group;
import app.simplecloud.api.persistentserver.PersistentServer;
import app.simplecloud.api.player.CloudPlayer;
import app.simplecloud.api.player.PlayerClientSettings;
import app.simplecloud.api.player.PlayerProfile;
import app.simplecloud.api.server.Server;
import app.simplecloud.api.server.ServerState;
import app.simplecloud.api.testing.group.TestGroup;
import app.simplecloud.api.testing.internal.event.SimulatedEvents;
import app.simplecloud.api.testing.player.TestPlayer;
import app.simplecloud.api.testing.server.TestServer;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Creates events for {@link app.simplecloud.api.testing.TestCloud#publish(Object)}. Publishing only notifies
 * subscribers, the cloud state stays unchanged. Network id and timestamp are filled in on publish and test
 * objects like {@code TestServer} are replaced by their snapshot.
 *
 * <pre>{@code
 * cloud.publish(TestEvents.serverCrashed(server, 137, "out of memory"));
 * cloud.publish(TestEvents.playerLogin(steve, true, false));
 * }</pre>
 */
public final class TestEvents {

    private TestEvents() {
    }

    public static GroupCreatedEvent groupCreated(Group group) {
        return new SimulatedEvents.GroupCreated(null, null, snapshot(group));
    }

    public static GroupUpdatedEvent groupUpdated(Group group) {
        return new SimulatedEvents.GroupUpdated(null, null, snapshot(group));
    }

    public static GroupDeletedEvent groupDeleted(Group group) {
        return groupDeleted(group.getServerGroupId(), group.getName());
    }

    public static GroupDeletedEvent groupDeleted(String serverGroupId, @Nullable String name) {
        return new SimulatedEvents.GroupDeleted(null, null, serverGroupId, name);
    }

    public static ServerStartedEvent serverStarted(Server server) {
        return new SimulatedEvents.ServerStarted(null, null, snapshot(server));
    }

    public static ServerUpdatedEvent serverUpdated(Server server) {
        return new SimulatedEvents.ServerUpdated(null, null, snapshot(server));
    }

    public static ServerDeletedEvent serverDeleted(Server server) {
        return new SimulatedEvents.ServerDeleted(null, null, snapshot(server));
    }

    public static ServerStoppedEvent serverStopped(Server server) {
        return new SimulatedEvents.ServerStopped(null, null, snapshot(server), false, null, null);
    }

    public static ServerStoppedEvent serverCrashed(Server server, @Nullable Integer exitCode, @Nullable String reason) {
        return new SimulatedEvents.ServerStopped(null, null, snapshot(server), true, exitCode, reason);
    }

    public static ServerStateChangedEvent serverStateChanged(Server server, ServerState oldState, ServerState newState) {
        return new SimulatedEvents.ServerStateChanged(null, null, snapshot(server), oldState, newState);
    }

    public static PersistentServerCreatedEvent persistentServerCreated(PersistentServer server) {
        return new SimulatedEvents.PersistentServerCreated(null, null, server.getPersistentServerId(), server.getName());
    }

    public static PersistentServerUpdatedEvent persistentServerUpdated(PersistentServer server) {
        return new SimulatedEvents.PersistentServerUpdated(null, null, server.getPersistentServerId(), server.getName());
    }

    public static PersistentServerDeletedEvent persistentServerDeleted(PersistentServer server) {
        return new SimulatedEvents.PersistentServerDeleted(null, null, server.getPersistentServerId(), server.getName());
    }

    public static PersistentServerStartedEvent persistentServerStarted(PersistentServer server, @Nullable String serverId) {
        return new SimulatedEvents.PersistentServerStarted(null, null, server.getPersistentServerId(), serverId);
    }

    public static PersistentServerStoppedEvent persistentServerStopped(PersistentServer server, @Nullable String serverId) {
        return new SimulatedEvents.PersistentServerStopped(null, null, server.getPersistentServerId(), serverId);
    }

    public static BlueprintCreatedEvent blueprintCreated(Blueprint blueprint) {
        return new SimulatedEvents.BlueprintCreated(null, null, blueprint);
    }

    public static BlueprintUpdatedEvent blueprintUpdated(Blueprint blueprint) {
        return new SimulatedEvents.BlueprintUpdated(null, null, blueprint);
    }

    public static BlueprintDeletedEvent blueprintDeleted(Blueprint blueprint) {
        return new SimulatedEvents.BlueprintDeleted(null, null, blueprint.getBlueprintId(), blueprint.getName(), blueprint);
    }

    public static PlayerLoginEvent playerLogin(CloudPlayer player) {
        return playerLogin(player, false, false);
    }

    public static PlayerLoginEvent playerLogin(CloudPlayer player, boolean newPlayer, boolean nameChanged) {
        CloudPlayer snapshot = snapshot(player);
        return new SimulatedEvents.PlayerLogin(null, null, snapshot, snapshot.getConnectedProxyName(),
                snapshot.getSessionId(), newPlayer, nameChanged);
    }

    public static PlayerDisconnectEvent playerDisconnect(CloudPlayer player, long sessionDurationSeconds) {
        CloudPlayer snapshot = snapshot(player);
        return new SimulatedEvents.PlayerDisconnect(null, null, snapshot, snapshot.getConnectedProxyName(),
                snapshot.getConnectedServerName(), sessionDurationSeconds);
    }

    public static PlayerServerSwitchEvent playerServerSwitch(CloudPlayer player, @Nullable String previousServerName,
                                                             String newServerName) {
        return new SimulatedEvents.PlayerServerSwitch(null, null, snapshot(player), previousServerName, newServerName);
    }

    public static PlayerKickEvent playerKick(CloudPlayer player, @Nullable Component reason) {
        CloudPlayer snapshot = snapshot(player);
        return new SimulatedEvents.PlayerKick(null, null, snapshot, snapshot.getConnectedServerName(), reason);
    }

    public static PlayerProfileChangedEvent playerProfileChanged(UUID playerId, @Nullable PlayerProfile oldProfile,
                                                                 @Nullable PlayerProfile newProfile) {
        return new SimulatedEvents.PlayerProfileChanged(null, null, playerId, oldProfile, newProfile);
    }

    public static PlayerClientSettingsUpdatedEvent playerClientSettingsUpdated(UUID playerId, @Nullable String sessionId,
                                                                               PlayerClientSettings settings) {
        return new SimulatedEvents.PlayerClientSettingsUpdated(null, null, playerId, sessionId, settings);
    }

    private static CloudPlayer snapshot(CloudPlayer player) {
        return player instanceof TestPlayer testPlayer ? testPlayer.snapshot() : player;
    }

    private static Group snapshot(Group group) {
        return group instanceof TestGroup testGroup ? testGroup.snapshot() : group;
    }

    private static Server snapshot(Server server) {
        return server instanceof TestServer testServer ? testServer.snapshot() : server;
    }
}
