package app.simplecloud.api.internal.event.group;

import app.simplecloud.api.event.Subscription;
import app.simplecloud.api.event.group.GroupCreatedEvent;
import app.simplecloud.api.event.group.GroupDeletedEvent;
import app.simplecloud.api.event.group.GroupEventApi;
import app.simplecloud.api.event.group.GroupUpdatedEvent;
import app.simplecloud.api.internal.event.EventSubscriber;
import build.buf.gen.simplecloud.controller.v2.ServerGroupCreatedEvent;
import build.buf.gen.simplecloud.controller.v2.ServerGroupDeletedEvent;
import build.buf.gen.simplecloud.controller.v2.ServerGroupUpdatedEvent;
import java.util.function.Consumer;

public class GroupEventApiImpl implements GroupEventApi {

    private final EventSubscriber events;

    public GroupEventApiImpl(EventSubscriber events) {
        this.events = events;
    }

    @Override
    public Subscription onCreated(Consumer<GroupCreatedEvent> handler) {
        return events.subscribe(
                "group.created", data -> new GroupCreatedEventImpl(ServerGroupCreatedEvent.parseFrom(data)), handler);
    }

    @Override
    public Subscription onUpdated(Consumer<GroupUpdatedEvent> handler) {
        return events.subscribe(
                "group.updated", data -> new GroupUpdatedEventImpl(ServerGroupUpdatedEvent.parseFrom(data)), handler);
    }

    @Override
    public Subscription onDeleted(Consumer<GroupDeletedEvent> handler) {
        return events.subscribe(
                "group.deleted", data -> new GroupDeletedEventImpl(ServerGroupDeletedEvent.parseFrom(data)), handler);
    }
}
