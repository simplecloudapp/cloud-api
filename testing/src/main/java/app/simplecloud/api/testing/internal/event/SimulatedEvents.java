package app.simplecloud.api.testing.internal.event;

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
import app.simplecloud.api.player.CloudPlayer;
import app.simplecloud.api.player.PlayerClientSettings;
import app.simplecloud.api.player.PlayerProfile;
import app.simplecloud.api.server.Server;
import app.simplecloud.api.server.ServerState;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.Nullable;

public final class SimulatedEvents {

    private SimulatedEvents() {}

    public interface Stampable {

        Object stamp(String networkId, String timestamp);
    }

    private static String orElse(@Nullable String value, String fallback) {
        return value == null ? fallback : value;
    }

    public record GroupCreated(
            @Nullable String networkId, @Nullable String timestamp, Group group)
            implements GroupCreatedEvent, Stampable {

        @Override
        public String getNetworkId() {
            return networkId;
        }

        @Override
        public String getServerGroupId() {
            return group.getServerGroupId();
        }

        @Override
        public Group getGroup() {
            return group;
        }

        @Override
        public String getTimestamp() {
            return timestamp;
        }

        @Override
        public GroupCreated stamp(String networkId, String timestamp) {
            return new GroupCreated(orElse(this.networkId, networkId), orElse(this.timestamp, timestamp), group);
        }
    }

    public record GroupUpdated(
            @Nullable String networkId, @Nullable String timestamp, Group group)
            implements GroupUpdatedEvent, Stampable {

        @Override
        public String getNetworkId() {
            return networkId;
        }

        @Override
        public String getServerGroupId() {
            return group.getServerGroupId();
        }

        @Override
        public Group getGroup() {
            return group;
        }

        @Override
        public String getTimestamp() {
            return timestamp;
        }

        @Override
        public GroupUpdated stamp(String networkId, String timestamp) {
            return new GroupUpdated(orElse(this.networkId, networkId), orElse(this.timestamp, timestamp), group);
        }
    }

    public record GroupDeleted(
            @Nullable String networkId,
            @Nullable String timestamp,
            String serverGroupId,
            @Nullable String name) implements GroupDeletedEvent, Stampable {

        @Override
        public String getNetworkId() {
            return networkId;
        }

        @Override
        public String getServerGroupId() {
            return serverGroupId;
        }

        @Override
        public @Nullable String getName() {
            return name;
        }

        @Override
        public String getTimestamp() {
            return timestamp;
        }

        @Override
        public GroupDeleted stamp(String networkId, String timestamp) {
            return new GroupDeleted(
                    orElse(this.networkId, networkId), orElse(this.timestamp, timestamp), serverGroupId, name);
        }
    }

    public record ServerStarted(
            @Nullable String networkId, @Nullable String timestamp, Server server)
            implements ServerStartedEvent, Stampable {

        @Override
        public String getNetworkId() {
            return networkId;
        }

        @Override
        public String getServerId() {
            return server.getServerId();
        }

        @Override
        public String getServerGroupId() {
            return server.getServerGroupId();
        }

        @Override
        public Server getServer() {
            return server;
        }

        @Override
        public String getTimestamp() {
            return timestamp;
        }

        @Override
        public ServerStarted stamp(String networkId, String timestamp) {
            return new ServerStarted(orElse(this.networkId, networkId), orElse(this.timestamp, timestamp), server);
        }
    }

    public record ServerUpdated(
            @Nullable String networkId, @Nullable String timestamp, Server server)
            implements ServerUpdatedEvent, Stampable {

        @Override
        public String getNetworkId() {
            return networkId;
        }

        @Override
        public String getServerId() {
            return server.getServerId();
        }

        @Override
        public String getServerGroupId() {
            return server.getServerGroupId();
        }

        @Override
        public Server getServer() {
            return server;
        }

        @Override
        public String getTimestamp() {
            return timestamp;
        }

        @Override
        public ServerUpdated stamp(String networkId, String timestamp) {
            return new ServerUpdated(orElse(this.networkId, networkId), orElse(this.timestamp, timestamp), server);
        }
    }

    public record ServerDeleted(
            @Nullable String networkId, @Nullable String timestamp, Server server)
            implements ServerDeletedEvent, Stampable {

        @Override
        public String getNetworkId() {
            return networkId;
        }

        @Override
        public String getServerId() {
            return server.getServerId();
        }

        @Override
        public String getServerGroupId() {
            return server.getServerGroupId();
        }

        @Override
        public Server getServer() {
            return server;
        }

        @Override
        public String getTimestamp() {
            return timestamp;
        }

        @Override
        public ServerDeleted stamp(String networkId, String timestamp) {
            return new ServerDeleted(orElse(this.networkId, networkId), orElse(this.timestamp, timestamp), server);
        }
    }

    public record ServerStopped(
            @Nullable String networkId,
            @Nullable String timestamp,
            Server server,
            boolean crashed,
            @Nullable Integer exitCode,
            @Nullable String reason)
            implements ServerStoppedEvent, Stampable {

        @Override
        public String getNetworkId() {
            return networkId;
        }

        @Override
        public String getServerId() {
            return server.getServerId();
        }

        @Override
        public String getServerGroupId() {
            return server.getServerGroupId();
        }

        @Override
        public Server getServer() {
            return server;
        }

        @Override
        public boolean isCrashed() {
            return crashed;
        }

        @Override
        public @Nullable Integer getExitCode() {
            return exitCode;
        }

        @Override
        public @Nullable String getReason() {
            return reason;
        }

        @Override
        public String getTimestamp() {
            return timestamp;
        }

        @Override
        public ServerStopped stamp(String networkId, String timestamp) {
            return new ServerStopped(
                    orElse(this.networkId, networkId),
                    orElse(this.timestamp, timestamp),
                    server,
                    crashed,
                    exitCode,
                    reason);
        }
    }

    public record ServerStateChanged(
            @Nullable String networkId,
            @Nullable String timestamp,
            Server server,
            ServerState oldState,
            ServerState newState)
            implements ServerStateChangedEvent, Stampable {

        @Override
        public String getNetworkId() {
            return networkId;
        }

        @Override
        public String getServerId() {
            return server.getServerId();
        }

        @Override
        public ServerState getOldState() {
            return oldState;
        }

        @Override
        public ServerState getNewState() {
            return newState;
        }

        @Override
        public Server getServer() {
            return server;
        }

        @Override
        public String getTimestamp() {
            return timestamp;
        }

        @Override
        public ServerStateChanged stamp(String networkId, String timestamp) {
            return new ServerStateChanged(
                    orElse(this.networkId, networkId), orElse(this.timestamp, timestamp), server, oldState, newState);
        }
    }

    public record PlayerLogin(
            @Nullable String networkId,
            @Nullable String timestamp,
            CloudPlayer player,
            @Nullable String proxyName,
            @Nullable String sessionId,
            boolean newPlayer,
            boolean nameChanged)
            implements PlayerLoginEvent, Stampable {

        @Override
        public String getNetworkId() {
            return networkId;
        }

        @Override
        public UUID getPlayerId() {
            return player.getUniqueId();
        }

        @Override
        public @Nullable String getProxyName() {
            return proxyName;
        }

        @Override
        public @Nullable String getSessionId() {
            return sessionId;
        }

        @Override
        public boolean isNewPlayer() {
            return newPlayer;
        }

        @Override
        public boolean isNameChanged() {
            return nameChanged;
        }

        @Override
        public CloudPlayer getPlayer() {
            return player;
        }

        @Override
        public String getTimestamp() {
            return timestamp;
        }

        @Override
        public PlayerLogin stamp(String networkId, String timestamp) {
            return new PlayerLogin(
                    orElse(this.networkId, networkId),
                    orElse(this.timestamp, timestamp),
                    player,
                    proxyName,
                    sessionId,
                    newPlayer,
                    nameChanged);
        }
    }

    public record PlayerDisconnect(
            @Nullable String networkId,
            @Nullable String timestamp,
            CloudPlayer player,
            @Nullable String proxyName,
            @Nullable String serverName,
            long sessionDurationSeconds)
            implements PlayerDisconnectEvent, Stampable {

        @Override
        public String getNetworkId() {
            return networkId;
        }

        @Override
        public UUID getPlayerId() {
            return player.getUniqueId();
        }

        @Override
        public @Nullable String getProxyName() {
            return proxyName;
        }

        @Override
        public @Nullable String getServerName() {
            return serverName;
        }

        @Override
        public long getSessionDurationSeconds() {
            return sessionDurationSeconds;
        }

        @Override
        public CloudPlayer getPlayer() {
            return player;
        }

        @Override
        public String getTimestamp() {
            return timestamp;
        }

        @Override
        public PlayerDisconnect stamp(String networkId, String timestamp) {
            return new PlayerDisconnect(
                    orElse(this.networkId, networkId),
                    orElse(this.timestamp, timestamp),
                    player,
                    proxyName,
                    serverName,
                    sessionDurationSeconds);
        }
    }

    public record PlayerServerSwitch(
            @Nullable String networkId,
            @Nullable String timestamp,
            CloudPlayer player,
            @Nullable String previousServerName,
            String newServerName)
            implements PlayerServerSwitchEvent, Stampable {

        @Override
        public String getNetworkId() {
            return networkId;
        }

        @Override
        public UUID getPlayerId() {
            return player.getUniqueId();
        }

        @Override
        public @Nullable String getPreviousServerName() {
            return previousServerName;
        }

        @Override
        public String getNewServerName() {
            return newServerName;
        }

        @Override
        public CloudPlayer getPlayer() {
            return player;
        }

        @Override
        public String getTimestamp() {
            return timestamp;
        }

        @Override
        public PlayerServerSwitch stamp(String networkId, String timestamp) {
            return new PlayerServerSwitch(
                    orElse(this.networkId, networkId),
                    orElse(this.timestamp, timestamp),
                    player,
                    previousServerName,
                    newServerName);
        }
    }

    public record PlayerKick(
            @Nullable String networkId,
            @Nullable String timestamp,
            CloudPlayer player,
            @Nullable String serverName,
            @Nullable Component reason)
            implements PlayerKickEvent, Stampable {

        @Override
        public String getNetworkId() {
            return networkId;
        }

        @Override
        public UUID getPlayerId() {
            return player.getUniqueId();
        }

        @Override
        public @Nullable String getServerName() {
            return serverName;
        }

        @Override
        public @Nullable Component getReason() {
            return reason;
        }

        @Override
        public CloudPlayer getPlayer() {
            return player;
        }

        @Override
        public String getTimestamp() {
            return timestamp;
        }

        @Override
        public PlayerKick stamp(String networkId, String timestamp) {
            return new PlayerKick(
                    orElse(this.networkId, networkId), orElse(this.timestamp, timestamp), player, serverName, reason);
        }
    }

    public record PlayerProfileChanged(
            @Nullable String networkId,
            @Nullable String timestamp,
            UUID playerId,
            @Nullable PlayerProfile oldProfile,
            @Nullable PlayerProfile newProfile)
            implements PlayerProfileChangedEvent, Stampable {

        @Override
        public String getNetworkId() {
            return networkId;
        }

        @Override
        public UUID getPlayerId() {
            return playerId;
        }

        @Override
        public @Nullable PlayerProfile getOldProfile() {
            return oldProfile;
        }

        @Override
        public @Nullable PlayerProfile getNewProfile() {
            return newProfile;
        }

        @Override
        public String getTimestamp() {
            return timestamp;
        }

        @Override
        public PlayerProfileChanged stamp(String networkId, String timestamp) {
            return new PlayerProfileChanged(
                    orElse(this.networkId, networkId),
                    orElse(this.timestamp, timestamp),
                    playerId,
                    oldProfile,
                    newProfile);
        }
    }

    public record PlayerClientSettingsUpdated(
            @Nullable String networkId,
            @Nullable String timestamp,
            UUID playerId,
            @Nullable String sessionId,
            @Nullable PlayerClientSettings settings)
            implements PlayerClientSettingsUpdatedEvent, Stampable {

        @Override
        public String getNetworkId() {
            return networkId;
        }

        @Override
        public UUID getPlayerId() {
            return playerId;
        }

        @Override
        public @Nullable String getSessionId() {
            return sessionId;
        }

        @Override
        public @Nullable PlayerClientSettings getSettings() {
            return settings;
        }

        @Override
        public String getTimestamp() {
            return timestamp;
        }

        @Override
        public PlayerClientSettingsUpdated stamp(String networkId, String timestamp) {
            return new PlayerClientSettingsUpdated(
                    orElse(this.networkId, networkId),
                    orElse(this.timestamp, timestamp),
                    playerId,
                    sessionId,
                    settings);
        }
    }

    public record PersistentServerCreated(
            @Nullable String networkId,
            @Nullable String timestamp,
            String persistentServerId,
            @Nullable String name) implements PersistentServerCreatedEvent, Stampable {

        @Override
        public String getNetworkId() {
            return networkId;
        }

        @Override
        public String getPersistentServerId() {
            return persistentServerId;
        }

        @Override
        public @Nullable String getName() {
            return name;
        }

        @Override
        public String getTimestamp() {
            return timestamp;
        }

        @Override
        public PersistentServerCreated stamp(String networkId, String timestamp) {
            return new PersistentServerCreated(
                    orElse(this.networkId, networkId), orElse(this.timestamp, timestamp), persistentServerId, name);
        }
    }

    public record PersistentServerUpdated(
            @Nullable String networkId,
            @Nullable String timestamp,
            String persistentServerId,
            @Nullable String name) implements PersistentServerUpdatedEvent, Stampable {

        @Override
        public String getNetworkId() {
            return networkId;
        }

        @Override
        public String getPersistentServerId() {
            return persistentServerId;
        }

        @Override
        public @Nullable String getName() {
            return name;
        }

        @Override
        public String getTimestamp() {
            return timestamp;
        }

        @Override
        public PersistentServerUpdated stamp(String networkId, String timestamp) {
            return new PersistentServerUpdated(
                    orElse(this.networkId, networkId), orElse(this.timestamp, timestamp), persistentServerId, name);
        }
    }

    public record PersistentServerDeleted(
            @Nullable String networkId,
            @Nullable String timestamp,
            String persistentServerId,
            @Nullable String name) implements PersistentServerDeletedEvent, Stampable {

        @Override
        public String getNetworkId() {
            return networkId;
        }

        @Override
        public String getPersistentServerId() {
            return persistentServerId;
        }

        @Override
        public @Nullable String getName() {
            return name;
        }

        @Override
        public String getTimestamp() {
            return timestamp;
        }

        @Override
        public PersistentServerDeleted stamp(String networkId, String timestamp) {
            return new PersistentServerDeleted(
                    orElse(this.networkId, networkId), orElse(this.timestamp, timestamp), persistentServerId, name);
        }
    }

    public record PersistentServerStarted(
            @Nullable String networkId,
            @Nullable String timestamp,
            String persistentServerId,
            @Nullable String serverId)
            implements PersistentServerStartedEvent, Stampable {

        @Override
        public String getNetworkId() {
            return networkId;
        }

        @Override
        public String getPersistentServerId() {
            return persistentServerId;
        }

        @Override
        public @Nullable String getServerId() {
            return serverId;
        }

        @Override
        public String getTimestamp() {
            return timestamp;
        }

        @Override
        public PersistentServerStarted stamp(String networkId, String timestamp) {
            return new PersistentServerStarted(
                    orElse(this.networkId, networkId), orElse(this.timestamp, timestamp), persistentServerId, serverId);
        }
    }

    public record PersistentServerStopped(
            @Nullable String networkId,
            @Nullable String timestamp,
            String persistentServerId,
            @Nullable String serverId)
            implements PersistentServerStoppedEvent, Stampable {

        @Override
        public String getNetworkId() {
            return networkId;
        }

        @Override
        public String getPersistentServerId() {
            return persistentServerId;
        }

        @Override
        public @Nullable String getServerId() {
            return serverId;
        }

        @Override
        public String getTimestamp() {
            return timestamp;
        }

        @Override
        public PersistentServerStopped stamp(String networkId, String timestamp) {
            return new PersistentServerStopped(
                    orElse(this.networkId, networkId), orElse(this.timestamp, timestamp), persistentServerId, serverId);
        }
    }

    public record BlueprintCreated(
            @Nullable String networkId, @Nullable String timestamp, Blueprint blueprint)
            implements BlueprintCreatedEvent, Stampable {

        @Override
        public String getNetworkId() {
            return networkId;
        }

        @Override
        public String getBlueprintId() {
            return blueprint.getBlueprintId();
        }

        @Override
        public Blueprint getBlueprint() {
            return blueprint;
        }

        @Override
        public String getTimestamp() {
            return timestamp;
        }

        @Override
        public BlueprintCreated stamp(String networkId, String timestamp) {
            return new BlueprintCreated(
                    orElse(this.networkId, networkId), orElse(this.timestamp, timestamp), blueprint);
        }
    }

    public record BlueprintUpdated(
            @Nullable String networkId, @Nullable String timestamp, Blueprint blueprint)
            implements BlueprintUpdatedEvent, Stampable {

        @Override
        public String getNetworkId() {
            return networkId;
        }

        @Override
        public String getBlueprintId() {
            return blueprint.getBlueprintId();
        }

        @Override
        public Blueprint getBlueprint() {
            return blueprint;
        }

        @Override
        public String getTimestamp() {
            return timestamp;
        }

        @Override
        public BlueprintUpdated stamp(String networkId, String timestamp) {
            return new BlueprintUpdated(
                    orElse(this.networkId, networkId), orElse(this.timestamp, timestamp), blueprint);
        }
    }

    public record BlueprintDeleted(
            @Nullable String networkId,
            @Nullable String timestamp,
            String blueprintId,
            @Nullable String name,
            @Nullable Blueprint blueprint)
            implements BlueprintDeletedEvent, Stampable {

        @Override
        public String getNetworkId() {
            return networkId;
        }

        @Override
        public String getBlueprintId() {
            return blueprintId;
        }

        @Override
        public @Nullable String getName() {
            return name;
        }

        @Override
        public @Nullable Blueprint getBlueprint() {
            return blueprint;
        }

        @Override
        public String getTimestamp() {
            return timestamp;
        }

        @Override
        public BlueprintDeleted stamp(String networkId, String timestamp) {
            return new BlueprintDeleted(
                    orElse(this.networkId, networkId), orElse(this.timestamp, timestamp), blueprintId, name, blueprint);
        }
    }
}
