package app.simplecloud.api.internal.event.blueprint;

import app.simplecloud.api.event.Subscription;
import app.simplecloud.api.event.blueprint.BlueprintCreatedEvent;
import app.simplecloud.api.event.blueprint.BlueprintDeletedEvent;
import app.simplecloud.api.event.blueprint.BlueprintEventApi;
import app.simplecloud.api.event.blueprint.BlueprintUpdatedEvent;
import app.simplecloud.api.internal.event.EventSubscriber;
import java.util.function.Consumer;

public class BlueprintEventApiImpl implements BlueprintEventApi {

    private final EventSubscriber events;

    public BlueprintEventApiImpl(EventSubscriber events) {
        this.events = events;
    }

    @Override
    public Subscription onCreated(Consumer<BlueprintCreatedEvent> handler) {
        return events.subscribe(
                "blueprint.created",
                data -> new BlueprintCreatedEventImpl(
                        build.buf.gen.simplecloud.controller.v2.BlueprintCreatedEvent.parseFrom(data)),
                handler);
    }

    @Override
    public Subscription onUpdated(Consumer<BlueprintUpdatedEvent> handler) {
        return events.subscribe(
                "blueprint.updated",
                data -> new BlueprintUpdatedEventImpl(
                        build.buf.gen.simplecloud.controller.v2.BlueprintUpdatedEvent.parseFrom(data)),
                handler);
    }

    @Override
    public Subscription onDeleted(Consumer<BlueprintDeletedEvent> handler) {
        return events.subscribe(
                "blueprint.deleted",
                data -> new BlueprintDeletedEventImpl(
                        build.buf.gen.simplecloud.controller.v2.BlueprintDeletedEvent.parseFrom(data)),
                handler);
    }
}
