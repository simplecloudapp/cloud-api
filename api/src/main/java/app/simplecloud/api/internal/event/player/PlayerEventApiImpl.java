package app.simplecloud.api.internal.event.player;

import app.simplecloud.api.event.Subscription;
import app.simplecloud.api.event.player.PlayerEventApi;
import app.simplecloud.api.internal.event.SubscriptionImpl;
import build.buf.gen.simplecloud.player.v2.PlayerDisconnectEvent;
import build.buf.gen.simplecloud.player.v2.PlayerKickEvent;
import build.buf.gen.simplecloud.player.v2.PlayerLoginEvent;
import build.buf.gen.simplecloud.player.v2.PlayerServerSwitchEvent;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.Message;

import java.util.function.Consumer;

public class PlayerEventApiImpl implements PlayerEventApi {

    private final Connection natsConnection;
    private final String networkId;
    private final Dispatcher dispatcher;

    public PlayerEventApiImpl(Connection natsConnection, String networkId) {
        this.natsConnection = natsConnection;
        this.networkId = networkId;
        this.dispatcher = natsConnection.createDispatcher(null);
    }

    @Override
    public Subscription onLogin(Consumer<app.simplecloud.api.event.player.PlayerLoginEvent> handler) {
        String subject = networkId + ".event.player.login";
        io.nats.client.Subscription natsSub = dispatcher.subscribe(subject, (Message msg) -> {
            try {
                PlayerLoginEvent protoEvent = PlayerLoginEvent.parseFrom(msg.getData());
                app.simplecloud.api.event.player.PlayerLoginEvent event = new PlayerLoginEventImpl(protoEvent);
                handler.accept(event);
            } catch (Exception e) {
                System.err.println("Error parsing PlayerLoginEvent: " + e.getMessage());
                e.printStackTrace();
            }
        });
        return new SubscriptionImpl(natsSub);
    }

    @Override
    public Subscription onDisconnect(Consumer<app.simplecloud.api.event.player.PlayerDisconnectEvent> handler) {
        String subject = networkId + ".event.player.disconnect";
        io.nats.client.Subscription natsSub = dispatcher.subscribe(subject, (Message msg) -> {
            try {
                PlayerDisconnectEvent protoEvent = PlayerDisconnectEvent.parseFrom(msg.getData());
                app.simplecloud.api.event.player.PlayerDisconnectEvent event = new PlayerDisconnectEventImpl(protoEvent);
                handler.accept(event);
            } catch (Exception e) {
                System.err.println("Error parsing PlayerDisconnectEvent: " + e.getMessage());
                e.printStackTrace();
            }
        });
        return new SubscriptionImpl(natsSub);
    }

    @Override
    public Subscription onServerSwitch(Consumer<app.simplecloud.api.event.player.PlayerServerSwitchEvent> handler) {
        String subject = networkId + ".event.player.server-switch";
        io.nats.client.Subscription natsSub = dispatcher.subscribe(subject, (Message msg) -> {
            try {
                PlayerServerSwitchEvent protoEvent = PlayerServerSwitchEvent.parseFrom(msg.getData());
                app.simplecloud.api.event.player.PlayerServerSwitchEvent event = new PlayerServerSwitchEventImpl(protoEvent);
                handler.accept(event);
            } catch (Exception e) {
                System.err.println("Error parsing PlayerServerSwitchEvent: " + e.getMessage());
                e.printStackTrace();
            }
        });
        return new SubscriptionImpl(natsSub);
    }

    @Override
    public Subscription onKick(Consumer<app.simplecloud.api.event.player.PlayerKickEvent> handler) {
        String subject = networkId + ".event.player.kick";
        io.nats.client.Subscription natsSub = dispatcher.subscribe(subject, (Message msg) -> {
            try {
                PlayerKickEvent protoEvent = PlayerKickEvent.parseFrom(msg.getData());
                app.simplecloud.api.event.player.PlayerKickEvent event = new PlayerKickEventImpl(protoEvent);
                handler.accept(event);
            } catch (Exception e) {
                System.err.println("Error parsing PlayerKickEvent: " + e.getMessage());
                e.printStackTrace();
            }
        });
        return new SubscriptionImpl(natsSub);
    }

}
