package app.simplecloud.api.internal.event.blueprint;

import app.simplecloud.api.event.blueprint.BlueprintEventApi;
import app.simplecloud.api.event.Subscription;
import app.simplecloud.api.internal.event.SubscriptionImpl;
import build.buf.gen.simplecloud.controller.v2.BlueprintCreatedEvent;
import build.buf.gen.simplecloud.controller.v2.BlueprintDeletedEvent;
import build.buf.gen.simplecloud.controller.v2.BlueprintUpdatedEvent;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.Message;

import java.util.function.Consumer;

public class BlueprintEventApiImpl implements BlueprintEventApi {

    private final Connection natsConnection;
    private final String networkId;
    private final Dispatcher dispatcher;

    public BlueprintEventApiImpl(Connection natsConnection, String networkId) {
        this.natsConnection = natsConnection;
        this.networkId = networkId;
        this.dispatcher = natsConnection.createDispatcher(null);
    }

    @Override
    public Subscription onCreated(Consumer<app.simplecloud.api.event.blueprint.BlueprintCreatedEvent> handler) {
        String subject = networkId + ".event.blueprint.created";
        io.nats.client.Subscription natsSub = dispatcher.subscribe(subject, (Message msg) -> {
            try {
                BlueprintCreatedEvent protoEvent = BlueprintCreatedEvent.parseFrom(msg.getData());
                app.simplecloud.api.event.blueprint.BlueprintCreatedEvent event = new BlueprintCreatedEventImpl(protoEvent);
                handler.accept(event);
            } catch (Exception e) {
                System.err.println("Error parsing BlueprintCreatedEvent: " + e.getMessage());
                e.printStackTrace();
            }
        });
        return new SubscriptionImpl(natsSub);
    }

    @Override
    public Subscription onUpdated(Consumer<app.simplecloud.api.event.blueprint.BlueprintUpdatedEvent> handler) {
        String subject = networkId + ".event.blueprint.updated";
        io.nats.client.Subscription natsSub = dispatcher.subscribe(subject, (Message msg) -> {
            try {
                BlueprintUpdatedEvent protoEvent = BlueprintUpdatedEvent.parseFrom(msg.getData());
                app.simplecloud.api.event.blueprint.BlueprintUpdatedEvent event = new BlueprintUpdatedEventImpl(protoEvent);
                handler.accept(event);
            } catch (Exception e) {
                System.err.println("Error parsing BlueprintUpdatedEvent: " + e.getMessage());
                e.printStackTrace();
            }
        });
        return new SubscriptionImpl(natsSub);
    }

    @Override
    public Subscription onDeleted(Consumer<app.simplecloud.api.event.blueprint.BlueprintDeletedEvent> handler) {
        String subject = networkId + ".event.blueprint.deleted";
        io.nats.client.Subscription natsSub = dispatcher.subscribe(subject, (Message msg) -> {
            try {
                BlueprintDeletedEvent protoEvent = BlueprintDeletedEvent.parseFrom(msg.getData());
                app.simplecloud.api.event.blueprint.BlueprintDeletedEvent event = new BlueprintDeletedEventImpl(protoEvent);
                handler.accept(event);
            } catch (Exception e) {
                System.err.println("Error parsing BlueprintDeletedEvent: " + e.getMessage());
                e.printStackTrace();
            }
        });
        return new SubscriptionImpl(natsSub);
    }

}

