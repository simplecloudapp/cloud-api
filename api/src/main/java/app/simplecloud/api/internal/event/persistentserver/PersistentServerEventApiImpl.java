package app.simplecloud.api.internal.event.persistentserver;

import app.simplecloud.api.event.Subscription;
import app.simplecloud.api.event.persistentserver.PersistentServerCreatedEvent;
import app.simplecloud.api.event.persistentserver.PersistentServerDeletedEvent;
import app.simplecloud.api.event.persistentserver.PersistentServerEventApi;
import app.simplecloud.api.event.persistentserver.PersistentServerStartedEvent;
import app.simplecloud.api.event.persistentserver.PersistentServerStoppedEvent;
import app.simplecloud.api.event.persistentserver.PersistentServerUpdatedEvent;
import app.simplecloud.api.internal.event.EventSubscriber;

import java.util.function.Consumer;

public class PersistentServerEventApiImpl implements PersistentServerEventApi {

    private final EventSubscriber events;

    public PersistentServerEventApiImpl(EventSubscriber events) {
        this.events = events;
    }

    @Override
    public Subscription onCreated(Consumer<PersistentServerCreatedEvent> handler) {
        return events.subscribe("persistent-server.created", data -> new PersistentServerCreatedEventImpl(
                build.buf.gen.simplecloud.controller.v2.PersistentServerCreatedEvent.parseFrom(data)), handler);
    }

    @Override
    public Subscription onStarted(Consumer<PersistentServerStartedEvent> handler) {
        return events.subscribe("persistent-server.started", data -> new PersistentServerStartedEventImpl(
                build.buf.gen.simplecloud.controller.v2.PersistentServerStartedEvent.parseFrom(data)), handler);
    }

    @Override
    public Subscription onStopped(Consumer<PersistentServerStoppedEvent> handler) {
        return events.subscribe("persistent-server.stopped", data -> new PersistentServerStoppedEventImpl(
                build.buf.gen.simplecloud.controller.v2.PersistentServerStoppedEvent.parseFrom(data)), handler);
    }

    @Override
    public Subscription onUpdated(Consumer<PersistentServerUpdatedEvent> handler) {
        return events.subscribe("persistent-server.updated", data -> new PersistentServerUpdatedEventImpl(
                build.buf.gen.simplecloud.controller.v2.PersistentServerUpdatedEvent.parseFrom(data)), handler);
    }

    @Override
    public Subscription onDeleted(Consumer<PersistentServerDeletedEvent> handler) {
        return events.subscribe("persistent-server.deleted", data -> new PersistentServerDeletedEventImpl(
                build.buf.gen.simplecloud.controller.v2.PersistentServerDeletedEvent.parseFrom(data)), handler);
    }
}
