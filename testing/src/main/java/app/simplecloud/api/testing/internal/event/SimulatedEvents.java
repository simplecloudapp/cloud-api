package app.simplecloud.api.testing.internal.event;

import app.simplecloud.api.event.group.GroupCreatedEvent;
import app.simplecloud.api.event.group.GroupDeletedEvent;
import app.simplecloud.api.event.group.GroupUpdatedEvent;
import app.simplecloud.api.event.server.ServerDeletedEvent;
import app.simplecloud.api.event.server.ServerStartedEvent;
import app.simplecloud.api.event.server.ServerStateChangedEvent;
import app.simplecloud.api.event.server.ServerStoppedEvent;
import app.simplecloud.api.event.server.ServerUpdatedEvent;
import app.simplecloud.api.group.Group;
import app.simplecloud.api.server.Server;
import app.simplecloud.api.server.ServerState;
import org.jetbrains.annotations.Nullable;

public final class SimulatedEvents {

    private SimulatedEvents() {
    }

    public interface Stampable {

        Object stamp(String networkId, String timestamp);
    }

    private static String orElse(@Nullable String value, String fallback) {
        return value == null ? fallback : value;
    }

    public record GroupCreated(@Nullable String networkId, @Nullable String timestamp, Group group)
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

    public record GroupUpdated(@Nullable String networkId, @Nullable String timestamp, Group group)
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

    public record GroupDeleted(@Nullable String networkId, @Nullable String timestamp, String serverGroupId,
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
            return new GroupDeleted(orElse(this.networkId, networkId), orElse(this.timestamp, timestamp), serverGroupId, name);
        }
    }

    public record ServerStarted(@Nullable String networkId, @Nullable String timestamp, Server server)
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

    public record ServerUpdated(@Nullable String networkId, @Nullable String timestamp, Server server)
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

    public record ServerDeleted(@Nullable String networkId, @Nullable String timestamp, Server server)
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

    public record ServerStopped(@Nullable String networkId, @Nullable String timestamp, Server server, boolean crashed,
                                @Nullable Integer exitCode, @Nullable String reason)
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
        public boolean getCrashed() {
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
            return new ServerStopped(orElse(this.networkId, networkId), orElse(this.timestamp, timestamp), server,
                    crashed, exitCode, reason);
        }
    }

    public record ServerStateChanged(@Nullable String networkId, @Nullable String timestamp, Server server,
                                     ServerState oldState, ServerState newState)
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
            return new ServerStateChanged(orElse(this.networkId, networkId), orElse(this.timestamp, timestamp), server,
                    oldState, newState);
        }
    }
}
