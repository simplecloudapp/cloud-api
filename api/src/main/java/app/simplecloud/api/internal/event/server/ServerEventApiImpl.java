package app.simplecloud.api.internal.event.server;

import app.simplecloud.api.event.Subscription;
import app.simplecloud.api.event.server.ServerEventApi;
import app.simplecloud.api.internal.event.SubscriptionImpl;
import build.buf.gen.simplecloud.controller.v2.ServerDeletedEvent;
import build.buf.gen.simplecloud.controller.v2.ServerStartedEvent;
import build.buf.gen.simplecloud.controller.v2.ServerStateChangedEvent;
import build.buf.gen.simplecloud.controller.v2.ServerStoppedEvent;
import build.buf.gen.simplecloud.controller.v2.ServerUpdatedEvent;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.Message;

import java.util.function.Consumer;

public class ServerEventApiImpl implements ServerEventApi {

    private final Connection natsConnection;
    private final String networkId;
    private final Dispatcher dispatcher;

    public ServerEventApiImpl(Connection natsConnection, String networkId) {
        this.natsConnection = natsConnection;
        this.networkId = networkId;
        this.dispatcher = natsConnection.createDispatcher(null);
    }

    @Override
    public Subscription onStarted(Consumer<app.simplecloud.api.event.server.ServerStartedEvent> handler) {
        String subject = networkId + ".event.server.started";
        io.nats.client.Subscription natsSub = dispatcher.subscribe(subject, (Message msg) -> {
            try {
                ServerStartedEvent protoEvent = ServerStartedEvent.parseFrom(msg.getData());
                app.simplecloud.api.event.server.ServerStartedEvent event = new ServerStartedEventImpl(protoEvent);
                handler.accept(event);
            } catch (Exception e) {
                System.err.println("Error parsing ServerStartedEvent: " + e.getMessage());
                e.printStackTrace();
            }
        });
        return new SubscriptionImpl(natsSub);
    }

    @Override
    public Subscription onStopped(Consumer<app.simplecloud.api.event.server.ServerStoppedEvent> handler) {
        String subject = networkId + ".event.server.stopped";
        io.nats.client.Subscription natsSub = dispatcher.subscribe(subject, (Message msg) -> {
            try {
                ServerStoppedEvent protoEvent = ServerStoppedEvent.parseFrom(msg.getData());
                app.simplecloud.api.event.server.ServerStoppedEvent event = new ServerStoppedEventImpl(protoEvent);
                handler.accept(event);
            } catch (Exception e) {
                System.err.println("Error parsing ServerStoppedEvent: " + e.getMessage());
                e.printStackTrace();
            }
        });
        return new SubscriptionImpl(natsSub);
    }

    @Override
    public Subscription onStateChanged(Consumer<app.simplecloud.api.event.server.ServerStateChangedEvent> handler) {
        String subject = networkId + ".event.server.state-changed";
        io.nats.client.Subscription natsSub = dispatcher.subscribe(subject, (Message msg) -> {
            try {
                ServerStateChangedEvent protoEvent = ServerStateChangedEvent.parseFrom(msg.getData());
                app.simplecloud.api.event.server.ServerStateChangedEvent event = new ServerStateChangedEventImpl(protoEvent);
                handler.accept(event);
            } catch (Exception e) {
                System.err.println("Error parsing ServerStateChangedEvent: " + e.getMessage());
                e.printStackTrace();
            }
        });
        return new SubscriptionImpl(natsSub);
    }

    @Override
    public Subscription onDeleted(Consumer<app.simplecloud.api.event.server.ServerDeletedEvent> handler) {
        String subject = networkId + ".event.server.deleted";
        io.nats.client.Subscription natsSub = dispatcher.subscribe(subject, (Message msg) -> {
            try {
                ServerDeletedEvent protoEvent = ServerDeletedEvent.parseFrom(msg.getData());
                app.simplecloud.api.event.server.ServerDeletedEvent event = new ServerDeletedEventImpl(protoEvent);
                handler.accept(event);
            } catch (Exception e) {
                System.err.println("Error parsing ServerDeletedEvent: " + e.getMessage());
                e.printStackTrace();
            }
        });
        return new SubscriptionImpl(natsSub);
    }

    @Override
    public Subscription onUpdated(Consumer<app.simplecloud.api.event.server.ServerUpdatedEvent> handler) {
        String subject = networkId + ".event.server.updated";
        io.nats.client.Subscription natsSub = dispatcher.subscribe(subject, (Message msg) -> {
            try {
                ServerUpdatedEvent protoEvent = ServerUpdatedEvent.parseFrom(msg.getData());
                app.simplecloud.api.event.server.ServerUpdatedEvent event = new ServerUpdatedEventImpl(protoEvent);
                handler.accept(event);
            } catch (Exception e) {
                System.err.println("Error parsing ServerUpdatedEvent: " + e.getMessage());
                e.printStackTrace();
            }
        });
        return new SubscriptionImpl(natsSub);
    }

}

