package app.simplecloud.api.testing.internal.event;

import app.simplecloud.api.event.EventApi;
import app.simplecloud.api.event.Subscription;
import app.simplecloud.api.event.blueprint.BlueprintCreatedEvent;
import app.simplecloud.api.event.blueprint.BlueprintDeletedEvent;
import app.simplecloud.api.event.blueprint.BlueprintEventApi;
import app.simplecloud.api.event.blueprint.BlueprintUpdatedEvent;
import app.simplecloud.api.event.group.GroupCreatedEvent;
import app.simplecloud.api.event.group.GroupDeletedEvent;
import app.simplecloud.api.event.group.GroupEventApi;
import app.simplecloud.api.event.group.GroupUpdatedEvent;
import app.simplecloud.api.event.persistentserver.PersistentServerCreatedEvent;
import app.simplecloud.api.event.persistentserver.PersistentServerDeletedEvent;
import app.simplecloud.api.event.persistentserver.PersistentServerEventApi;
import app.simplecloud.api.event.persistentserver.PersistentServerStartedEvent;
import app.simplecloud.api.event.persistentserver.PersistentServerStoppedEvent;
import app.simplecloud.api.event.persistentserver.PersistentServerUpdatedEvent;
import app.simplecloud.api.event.player.PlayerClientSettingsUpdatedEvent;
import app.simplecloud.api.event.player.PlayerDisconnectEvent;
import app.simplecloud.api.event.player.PlayerEventApi;
import app.simplecloud.api.event.player.PlayerKickEvent;
import app.simplecloud.api.event.player.PlayerLoginEvent;
import app.simplecloud.api.event.player.PlayerProfileChangedEvent;
import app.simplecloud.api.event.player.PlayerServerSwitchEvent;
import app.simplecloud.api.event.server.ServerDeletedEvent;
import app.simplecloud.api.event.server.ServerEventApi;
import app.simplecloud.api.event.server.ServerStartedEvent;
import app.simplecloud.api.event.server.ServerStateChangedEvent;
import app.simplecloud.api.event.server.ServerStoppedEvent;
import app.simplecloud.api.event.server.ServerUpdatedEvent;

import java.util.function.Consumer;

public final class TestEventApi implements EventApi {

    private final GroupEventApi group;
    private final ServerEventApi server;
    private final PersistentServerEventApi persistentServer;
    private final BlueprintEventApi blueprint;
    private final PlayerEventApi player;

    public TestEventApi(EventBus bus) {
        this.group = new GroupEventApi() {
            @Override
            public Subscription onCreated(Consumer<GroupCreatedEvent> handler) {
                return bus.subscribe(GroupCreatedEvent.class, handler);
            }

            @Override
            public Subscription onUpdated(Consumer<GroupUpdatedEvent> handler) {
                return bus.subscribe(GroupUpdatedEvent.class, handler);
            }

            @Override
            public Subscription onDeleted(Consumer<GroupDeletedEvent> handler) {
                return bus.subscribe(GroupDeletedEvent.class, handler);
            }
        };
        this.server = new ServerEventApi() {
            @Override
            public Subscription onStarted(Consumer<ServerStartedEvent> handler) {
                return bus.subscribe(ServerStartedEvent.class, handler);
            }

            @Override
            public Subscription onStopped(Consumer<ServerStoppedEvent> handler) {
                return bus.subscribe(ServerStoppedEvent.class, handler);
            }

            @Override
            public Subscription onStateChanged(Consumer<ServerStateChangedEvent> handler) {
                return bus.subscribe(ServerStateChangedEvent.class, handler);
            }

            @Override
            public Subscription onDeleted(Consumer<ServerDeletedEvent> handler) {
                return bus.subscribe(ServerDeletedEvent.class, handler);
            }

            @Override
            public Subscription onUpdated(Consumer<ServerUpdatedEvent> handler) {
                return bus.subscribe(ServerUpdatedEvent.class, handler);
            }
        };
        this.persistentServer = new PersistentServerEventApi() {
            @Override
            public Subscription onCreated(Consumer<PersistentServerCreatedEvent> handler) {
                return bus.subscribe(PersistentServerCreatedEvent.class, handler);
            }

            @Override
            public Subscription onStarted(Consumer<PersistentServerStartedEvent> handler) {
                return bus.subscribe(PersistentServerStartedEvent.class, handler);
            }

            @Override
            public Subscription onStopped(Consumer<PersistentServerStoppedEvent> handler) {
                return bus.subscribe(PersistentServerStoppedEvent.class, handler);
            }

            @Override
            public Subscription onUpdated(Consumer<PersistentServerUpdatedEvent> handler) {
                return bus.subscribe(PersistentServerUpdatedEvent.class, handler);
            }

            @Override
            public Subscription onDeleted(Consumer<PersistentServerDeletedEvent> handler) {
                return bus.subscribe(PersistentServerDeletedEvent.class, handler);
            }
        };
        this.blueprint = new BlueprintEventApi() {
            @Override
            public Subscription onCreated(Consumer<BlueprintCreatedEvent> handler) {
                return bus.subscribe(BlueprintCreatedEvent.class, handler);
            }

            @Override
            public Subscription onUpdated(Consumer<BlueprintUpdatedEvent> handler) {
                return bus.subscribe(BlueprintUpdatedEvent.class, handler);
            }

            @Override
            public Subscription onDeleted(Consumer<BlueprintDeletedEvent> handler) {
                return bus.subscribe(BlueprintDeletedEvent.class, handler);
            }
        };
        this.player = new PlayerEventApi() {
            @Override
            public Subscription onLogin(Consumer<PlayerLoginEvent> handler) {
                return bus.subscribe(PlayerLoginEvent.class, handler);
            }

            @Override
            public Subscription onDisconnect(Consumer<PlayerDisconnectEvent> handler) {
                return bus.subscribe(PlayerDisconnectEvent.class, handler);
            }

            @Override
            public Subscription onServerSwitch(Consumer<PlayerServerSwitchEvent> handler) {
                return bus.subscribe(PlayerServerSwitchEvent.class, handler);
            }

            @Override
            public Subscription onKick(Consumer<PlayerKickEvent> handler) {
                return bus.subscribe(PlayerKickEvent.class, handler);
            }

            @Override
            public Subscription onProfileChanged(Consumer<PlayerProfileChangedEvent> handler) {
                return bus.subscribe(PlayerProfileChangedEvent.class, handler);
            }

            @Override
            public Subscription onClientSettingsUpdated(Consumer<PlayerClientSettingsUpdatedEvent> handler) {
                return bus.subscribe(PlayerClientSettingsUpdatedEvent.class, handler);
            }
        };
    }

    @Override
    public GroupEventApi group() {
        return group;
    }

    @Override
    public ServerEventApi server() {
        return server;
    }

    @Override
    public PersistentServerEventApi persistentServer() {
        return persistentServer;
    }

    @Override
    public BlueprintEventApi blueprint() {
        return blueprint;
    }

    @Override
    public PlayerEventApi player() {
        return player;
    }
}
