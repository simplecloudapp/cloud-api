package app.simplecloud.api.internal.event.server;

import app.simplecloud.api.event.Subscription;
import app.simplecloud.api.event.server.ServerDeletedEvent;
import app.simplecloud.api.event.server.ServerEventApi;
import app.simplecloud.api.event.server.ServerStartedEvent;
import app.simplecloud.api.event.server.ServerStateChangedEvent;
import app.simplecloud.api.event.server.ServerStoppedEvent;
import app.simplecloud.api.event.server.ServerUpdatedEvent;
import app.simplecloud.api.internal.event.EventSubscriber;
import java.util.function.Consumer;

public class ServerEventApiImpl implements ServerEventApi {

    private final EventSubscriber events;

    public ServerEventApiImpl(EventSubscriber events) {
        this.events = events;
    }

    @Override
    public Subscription onStarted(Consumer<ServerStartedEvent> handler) {
        return events.subscribe(
                "server.started",
                data -> new ServerStartedEventImpl(
                        build.buf.gen.simplecloud.controller.v2.ServerStartedEvent.parseFrom(data)),
                handler);
    }

    @Override
    public Subscription onStopped(Consumer<ServerStoppedEvent> handler) {
        return events.subscribe(
                "server.stopped",
                data -> new ServerStoppedEventImpl(
                        build.buf.gen.simplecloud.controller.v2.ServerStoppedEvent.parseFrom(data)),
                handler);
    }

    @Override
    public Subscription onStateChanged(Consumer<ServerStateChangedEvent> handler) {
        return events.subscribe(
                "server.state-changed",
                data -> new ServerStateChangedEventImpl(
                        build.buf.gen.simplecloud.controller.v2.ServerStateChangedEvent.parseFrom(data)),
                handler);
    }

    @Override
    public Subscription onDeleted(Consumer<ServerDeletedEvent> handler) {
        return events.subscribe(
                "server.deleted",
                data -> new ServerDeletedEventImpl(
                        build.buf.gen.simplecloud.controller.v2.ServerDeletedEvent.parseFrom(data)),
                handler);
    }

    @Override
    public Subscription onUpdated(Consumer<ServerUpdatedEvent> handler) {
        return events.subscribe(
                "server.updated",
                data -> new ServerUpdatedEventImpl(
                        build.buf.gen.simplecloud.controller.v2.ServerUpdatedEvent.parseFrom(data)),
                handler);
    }
}
