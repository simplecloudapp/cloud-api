package app.simplecloud.api.internal.event.player;

import app.simplecloud.api.event.Subscription;
import app.simplecloud.api.event.player.PlayerClientSettingsUpdatedEvent;
import app.simplecloud.api.event.player.PlayerDisconnectEvent;
import app.simplecloud.api.event.player.PlayerEventApi;
import app.simplecloud.api.event.player.PlayerKickEvent;
import app.simplecloud.api.event.player.PlayerLoginEvent;
import app.simplecloud.api.event.player.PlayerProfileChangedEvent;
import app.simplecloud.api.event.player.PlayerServerSwitchEvent;
import app.simplecloud.api.internal.event.EventSubscriber;
import app.simplecloud.api.player.PlayerApi;
import io.nats.client.Connection;
import java.util.function.Consumer;

public class PlayerEventApiImpl implements PlayerEventApi {

    private final EventSubscriber events;
    private final Connection natsConnection;
    private final PlayerApi playerApi;

    public PlayerEventApiImpl(EventSubscriber events, Connection natsConnection, PlayerApi playerApi) {
        this.events = events;
        this.natsConnection = natsConnection;
        this.playerApi = playerApi;
    }

    @Override
    public Subscription onLogin(Consumer<PlayerLoginEvent> handler) {
        return events.subscribe(
                "player.login",
                data -> new PlayerLoginEventImpl(
                        build.buf.gen.simplecloud.player.v2.PlayerLoginEvent.parseFrom(data),
                        playerApi,
                        natsConnection),
                handler);
    }

    @Override
    public Subscription onDisconnect(Consumer<PlayerDisconnectEvent> handler) {
        return events.subscribe(
                "player.disconnect",
                data -> new PlayerDisconnectEventImpl(
                        build.buf.gen.simplecloud.player.v2.PlayerDisconnectEvent.parseFrom(data),
                        playerApi,
                        natsConnection),
                handler);
    }

    @Override
    public Subscription onServerSwitch(Consumer<PlayerServerSwitchEvent> handler) {
        return events.subscribe(
                "player.server-switch",
                data -> new PlayerServerSwitchEventImpl(
                        build.buf.gen.simplecloud.player.v2.PlayerServerSwitchEvent.parseFrom(data),
                        playerApi,
                        natsConnection),
                handler);
    }

    @Override
    public Subscription onKick(Consumer<PlayerKickEvent> handler) {
        return events.subscribe(
                "player.kick",
                data -> new PlayerKickEventImpl(
                        build.buf.gen.simplecloud.player.v2.PlayerKickEvent.parseFrom(data), playerApi, natsConnection),
                handler);
    }

    @Override
    public Subscription onProfileChanged(Consumer<PlayerProfileChangedEvent> handler) {
        return events.subscribe(
                "player.profile-changed",
                data -> new PlayerProfileChangedEventImpl(
                        build.buf.gen.simplecloud.player.v2.PlayerProfileChangedEvent.parseFrom(data)),
                handler);
    }

    @Override
    public Subscription onClientSettingsUpdated(Consumer<PlayerClientSettingsUpdatedEvent> handler) {
        return events.subscribe(
                "player.client-settings-updated",
                data -> new PlayerClientSettingsUpdatedEventImpl(
                        build.buf.gen.simplecloud.player.v2.PlayerClientSettingsUpdatedEvent.parseFrom(data)),
                handler);
    }
}
