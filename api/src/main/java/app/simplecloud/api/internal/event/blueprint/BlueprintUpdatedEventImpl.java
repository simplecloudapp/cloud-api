package app.simplecloud.api.internal.event.blueprint;

import app.simplecloud.api.blueprint.Blueprint;
import app.simplecloud.api.event.blueprint.BlueprintUpdatedEvent;

import java.time.Instant;

class BlueprintUpdatedEventImpl implements BlueprintUpdatedEvent {
    private final build.buf.gen.simplecloud.controller.v2.BlueprintUpdatedEvent delegate;

    BlueprintUpdatedEventImpl(build.buf.gen.simplecloud.controller.v2.BlueprintUpdatedEvent delegate) {
        this.delegate = delegate;
    }

    @Override
    public String getNetworkId() {
        return delegate.getNetworkId();
    }

    @Override
    public String getBlueprintId() {
        return delegate.getBlueprintId();
    }

    @Override
    public Blueprint getBlueprint() {
        throw new IllegalStateException("Blueprint data is not available in BlueprintUpdatedEvent. Use CloudApi to fetch the blueprint.");
    }

    @Override
    public String getTimestamp() {
        return Instant.ofEpochSecond(delegate.getTimestamp()).toString();
    }
}

