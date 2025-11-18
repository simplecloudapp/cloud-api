package app.simplecloud.api.internal.event.persistentserver;

import app.simplecloud.api.event.Subscription;
import app.simplecloud.api.event.persistentserver.PersistentServerEventApi;
import app.simplecloud.api.internal.event.SubscriptionImpl;
import build.buf.gen.simplecloud.controller.v2.*;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.Message;

import java.util.function.Consumer;

public class PersistentServerEventApiImpl implements PersistentServerEventApi {

    private final Connection natsConnection;
    private final String networkId;
    private final Dispatcher dispatcher;

    public PersistentServerEventApiImpl(Connection natsConnection, String networkId) {
        this.natsConnection = natsConnection;
        this.networkId = networkId;
        this.dispatcher = natsConnection.createDispatcher(null);
    }

    @Override
    public Subscription onCreated(Consumer<app.simplecloud.api.event.persistentserver.PersistentServerCreatedEvent> handler) {
        String subject = networkId + ".event.persistent-server.created";
        io.nats.client.Subscription natsSub = dispatcher.subscribe(subject, (Message msg) -> {
            try {
                PersistentServerCreatedEvent protoEvent = PersistentServerCreatedEvent.parseFrom(msg.getData());
                app.simplecloud.api.event.persistentserver.PersistentServerCreatedEvent event = new PersistentServerCreatedEventImpl(protoEvent);
                handler.accept(event);
            } catch (Exception e) {
                System.err.println("Error parsing PersistentServerCreatedEvent: " + e.getMessage());
                e.printStackTrace();
            }
        });
        return new SubscriptionImpl(natsSub);
    }

    @Override
    public Subscription onStarted(Consumer<app.simplecloud.api.event.persistentserver.PersistentServerStartedEvent> handler) {
        String subject = networkId + ".event.persistent-server.started";
        io.nats.client.Subscription natsSub = dispatcher.subscribe(subject, (Message msg) -> {
            try {
                PersistentServerStartedEvent protoEvent = PersistentServerStartedEvent.parseFrom(msg.getData());
                app.simplecloud.api.event.persistentserver.PersistentServerStartedEvent event = new PersistentServerStartedEventImpl(protoEvent);
                handler.accept(event);
            } catch (Exception e) {
                System.err.println("Error parsing PersistentServerStartedEvent: " + e.getMessage());
                e.printStackTrace();
            }
        });
        return new SubscriptionImpl(natsSub);
    }

    @Override
    public Subscription onStopped(Consumer<app.simplecloud.api.event.persistentserver.PersistentServerStoppedEvent> handler) {
        String subject = networkId + ".event.persistent-server.stopped";
        io.nats.client.Subscription natsSub = dispatcher.subscribe(subject, (Message msg) -> {
            try {
                PersistentServerStoppedEvent protoEvent = PersistentServerStoppedEvent.parseFrom(msg.getData());
                app.simplecloud.api.event.persistentserver.PersistentServerStoppedEvent event = new PersistentServerStoppedEventImpl(protoEvent);
                handler.accept(event);
            } catch (Exception e) {
                System.err.println("Error parsing PersistentServerStoppedEvent: " + e.getMessage());
                e.printStackTrace();
            }
        });
        return new SubscriptionImpl(natsSub);
    }

    @Override
    public Subscription onUpdated(Consumer<app.simplecloud.api.event.persistentserver.PersistentServerUpdatedEvent> handler) {
        String subject = networkId + ".event.persistent-server.updated";
        io.nats.client.Subscription natsSub = dispatcher.subscribe(subject, (Message msg) -> {
            try {
                PersistentServerUpdatedEvent protoEvent = PersistentServerUpdatedEvent.parseFrom(msg.getData());
                app.simplecloud.api.event.persistentserver.PersistentServerUpdatedEvent event = new PersistentServerUpdatedEventImpl(protoEvent);
                handler.accept(event);
            } catch (Exception e) {
                System.err.println("Error parsing PersistentServerUpdatedEvent: " + e.getMessage());
                e.printStackTrace();
            }
        });
        return new SubscriptionImpl(natsSub);
    }

    @Override
    public Subscription onDeleted(Consumer<app.simplecloud.api.event.persistentserver.PersistentServerDeletedEvent> handler) {
        String subject = networkId + ".event.persistent-server.deleted";
        io.nats.client.Subscription natsSub = dispatcher.subscribe(subject, (Message msg) -> {
            try {
                PersistentServerDeletedEvent protoEvent = PersistentServerDeletedEvent.parseFrom(msg.getData());
                app.simplecloud.api.event.persistentserver.PersistentServerDeletedEvent event = new PersistentServerDeletedEventImpl(protoEvent);
                handler.accept(event);
            } catch (Exception e) {
                System.err.println("Error parsing PersistentServerDeletedEvent: " + e.getMessage());
                e.printStackTrace();
            }
        });
        return new SubscriptionImpl(natsSub);
    }

}

