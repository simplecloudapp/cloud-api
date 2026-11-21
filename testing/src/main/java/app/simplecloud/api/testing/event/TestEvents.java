package app.simplecloud.api.testing.event;

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
import app.simplecloud.api.testing.group.TestGroup;
import app.simplecloud.api.testing.internal.event.SimulatedEvents;
import app.simplecloud.api.testing.server.TestServer;
import org.jetbrains.annotations.Nullable;

public final class TestEvents {

    private TestEvents() {
    }

    public static GroupCreatedEvent groupCreated(Group group) {
        return new SimulatedEvents.GroupCreated(null, null, frozen(group));
    }

    public static GroupUpdatedEvent groupUpdated(Group group) {
        return new SimulatedEvents.GroupUpdated(null, null, frozen(group));
    }

    public static GroupDeletedEvent groupDeleted(Group group) {
        return groupDeleted(group.getServerGroupId(), group.getName());
    }

    public static GroupDeletedEvent groupDeleted(String serverGroupId, @Nullable String name) {
        return new SimulatedEvents.GroupDeleted(null, null, serverGroupId, name);
    }

    public static ServerStartedEvent serverStarted(Server server) {
        return new SimulatedEvents.ServerStarted(null, null, frozen(server));
    }

    public static ServerUpdatedEvent serverUpdated(Server server) {
        return new SimulatedEvents.ServerUpdated(null, null, frozen(server));
    }

    public static ServerDeletedEvent serverDeleted(Server server) {
        return new SimulatedEvents.ServerDeleted(null, null, frozen(server));
    }

    public static ServerStoppedEvent serverStopped(Server server) {
        return new SimulatedEvents.ServerStopped(null, null, frozen(server), false, null, null);
    }

    public static ServerStoppedEvent serverCrashed(Server server, @Nullable Integer exitCode, @Nullable String reason) {
        return new SimulatedEvents.ServerStopped(null, null, frozen(server), true, exitCode, reason);
    }

    public static ServerStateChangedEvent serverStateChanged(Server server, ServerState oldState, ServerState newState) {
        return new SimulatedEvents.ServerStateChanged(null, null, frozen(server), oldState, newState);
    }

    private static Group frozen(Group group) {
        return group instanceof TestGroup testGroup ? testGroup.snapshot() : group;
    }

    private static Server frozen(Server server) {
        return server instanceof TestServer testServer ? testServer.snapshot() : server;
    }
}
