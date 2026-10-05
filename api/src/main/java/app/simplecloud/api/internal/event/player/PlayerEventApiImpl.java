package app.simplecloud.api.internal.event.player;

import app.simplecloud.api.event.Subscription;
import app.simplecloud.api.event.player.PlayerClientSettingsUpdatedEvent;
import app.simplecloud.api.event.player.PlayerDisconnectEvent;
import app.simplecloud.api.event.player.PlayerEventApi;
import app.simplecloud.api.event.player.PlayerKickEvent;
import app.simplecloud.api.event.player.PlayerLoginEvent;
import app.simplecloud.api.event.player.PlayerProfileChangedEvent;
import app.simplecloud.api.event.player.PlayerServerSwitchEvent;
import app.simplecloud.api.internal.event.SubscriptionImpl;
import app.simplecloud.api.player.PlayerApi;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.Message;

import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PlayerEventApiImpl implements PlayerEventApi {

    private static final Logger LOGGER = Logger.getLogger(PlayerEventApiImpl.class.getName());

    private final Connection natsConnection;
    private final String networkId;
    private final PlayerApi playerApi;
    private final Dispatcher dispatcher;

    public PlayerEventApiImpl(Connection natsConnection, String networkId, PlayerApi playerApi) {
        this.natsConnection = natsConnection;
        this.networkId = networkId;
        this.playerApi = playerApi;
        this.dispatcher = natsConnection.createDispatcher(null);
    }

    @Override
    public Subscription onLogin(Consumer<PlayerLoginEvent> handler) {
        return subscribe("login", "PlayerLoginEvent", handler, data -> new PlayerLoginEventImpl(
                build.buf.gen.simplecloud.player.v2.PlayerLoginEvent.parseFrom(data), playerApi, natsConnection));
    }

    @Override
    public Subscription onDisconnect(Consumer<PlayerDisconnectEvent> handler) {
        return subscribe("disconnect", "PlayerDisconnectEvent", handler, data -> new PlayerDisconnectEventImpl(
                build.buf.gen.simplecloud.player.v2.PlayerDisconnectEvent.parseFrom(data), playerApi, natsConnection));
    }

    @Override
    public Subscription onServerSwitch(Consumer<PlayerServerSwitchEvent> handler) {
        return subscribe("server-switch", "PlayerServerSwitchEvent", handler, data -> new PlayerServerSwitchEventImpl(
                build.buf.gen.simplecloud.player.v2.PlayerServerSwitchEvent.parseFrom(data), playerApi, natsConnection));
    }

    @Override
    public Subscription onKick(Consumer<PlayerKickEvent> handler) {
        return subscribe("kick", "PlayerKickEvent", handler, data -> new PlayerKickEventImpl(
                build.buf.gen.simplecloud.player.v2.PlayerKickEvent.parseFrom(data), playerApi, natsConnection));
    }

    @Override
    public Subscription onProfileChanged(Consumer<PlayerProfileChangedEvent> handler) {
        return subscribe("profile-changed", "PlayerProfileChangedEvent", handler, data -> new PlayerProfileChangedEventImpl(
                build.buf.gen.simplecloud.player.v2.PlayerProfileChangedEvent.parseFrom(data)));
    }

    @Override
    public Subscription onClientSettingsUpdated(Consumer<PlayerClientSettingsUpdatedEvent> handler) {
        return subscribe("client-settings-updated", "PlayerClientSettingsUpdatedEvent", handler, data -> new PlayerClientSettingsUpdatedEventImpl(
                build.buf.gen.simplecloud.player.v2.PlayerClientSettingsUpdatedEvent.parseFrom(data)));
    }

    private <E> Subscription subscribe(String action, String eventName, Consumer<E> handler, EventParser<E> parser) {
        String subject = networkId + ".event.player." + action;
        io.nats.client.Subscription natsSub = dispatcher.subscribe(subject, (Message msg) -> {
            try {
                handler.accept(parser.parse(msg.getData()));
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error handling " + eventName, e);
            }
        });
        return new SubscriptionImpl(natsSub);
    }

    @FunctionalInterface
    private interface EventParser<E> {
        E parse(byte[] data) throws Exception;
    }

}
