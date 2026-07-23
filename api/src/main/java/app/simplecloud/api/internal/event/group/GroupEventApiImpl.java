package app.simplecloud.api.internal.event.group;

import app.simplecloud.api.event.Subscription;
import app.simplecloud.api.event.group.GroupCreatedEvent;
import app.simplecloud.api.event.group.GroupDeletedEvent;
import app.simplecloud.api.event.group.GroupEventApi;
import app.simplecloud.api.event.group.GroupUpdatedEvent;
import app.simplecloud.api.internal.event.SubscriptionImpl;
import build.buf.gen.simplecloud.controller.v2.ServerGroupCreatedEvent;
import build.buf.gen.simplecloud.controller.v2.ServerGroupDeletedEvent;
import build.buf.gen.simplecloud.controller.v2.ServerGroupUpdatedEvent;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.Message;

import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public class GroupEventApiImpl implements GroupEventApi {

    private static final Logger LOGGER = Logger.getLogger(GroupEventApiImpl.class.getName());

    private final Connection natsConnection;
    private final String networkId;
    private final Dispatcher dispatcher;

    public GroupEventApiImpl(Connection natsConnection, String networkId) {
        this.natsConnection = natsConnection;
        this.networkId = networkId;
        this.dispatcher = natsConnection.createDispatcher(null);
    }

    @Override
    public Subscription onCreated(Consumer<GroupCreatedEvent> handler) {
        String subject = networkId + ".event.group.created";
        io.nats.client.Subscription natsSub = dispatcher.subscribe(subject, (Message msg) -> {
            try {
                ServerGroupCreatedEvent protoEvent = ServerGroupCreatedEvent.parseFrom(msg.getData());
                GroupCreatedEvent event = new GroupCreatedEventImpl(protoEvent);
                handler.accept(event);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error handling ServerGroupCreatedEvent", e);
            }
        });
        return new SubscriptionImpl(natsSub);
    }

    @Override
    public Subscription onUpdated(Consumer<GroupUpdatedEvent> handler) {
        String subject = networkId + ".event.group.updated";
        io.nats.client.Subscription natsSub = dispatcher.subscribe(subject, (Message msg) -> {
            try {
                ServerGroupUpdatedEvent protoEvent = ServerGroupUpdatedEvent.parseFrom(msg.getData());
                GroupUpdatedEvent event = new GroupUpdatedEventImpl(protoEvent);
                handler.accept(event);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error handling ServerGroupUpdatedEvent", e);
            }
        });
        return new SubscriptionImpl(natsSub);
    }

    @Override
    public Subscription onDeleted(Consumer<GroupDeletedEvent> handler) {
        String subject = networkId + ".event.group.deleted";
        io.nats.client.Subscription natsSub = dispatcher.subscribe(subject, (Message msg) -> {
            try {
                ServerGroupDeletedEvent protoEvent = ServerGroupDeletedEvent.parseFrom(msg.getData());
                GroupDeletedEvent event = new GroupDeletedEventImpl(protoEvent);
                handler.accept(event);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error handling ServerGroupDeletedEvent", e);
            }
        });
        return new SubscriptionImpl(natsSub);
    }

}
