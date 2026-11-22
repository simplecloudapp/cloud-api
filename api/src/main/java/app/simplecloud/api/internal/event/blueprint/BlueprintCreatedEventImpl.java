package app.simplecloud.api.internal.event.blueprint;

import app.simplecloud.api.blueprint.Blueprint;
import app.simplecloud.api.event.blueprint.BlueprintCreatedEvent;
import app.simplecloud.api.internal.blueprint.BlueprintImpl;
import java.time.Instant;

class BlueprintCreatedEventImpl implements BlueprintCreatedEvent {
    private final build.buf.gen.simplecloud.controller.v2.BlueprintCreatedEvent delegate;

    BlueprintCreatedEventImpl(build.buf.gen.simplecloud.controller.v2.BlueprintCreatedEvent delegate) {
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
        if (!delegate.hasConfig()) {
            throw new IllegalStateException(
                    "Blueprint data is not available in BlueprintCreatedEvent. Use CloudApi to fetch the blueprint.");
        }
        String timestamp = getTimestamp();
        return BlueprintImpl.from(delegate.getConfig(), timestamp, timestamp);
    }

    @Override
    public String getTimestamp() {
        return Instant.ofEpochSecond(delegate.getTimestamp()).toString();
    }
}
