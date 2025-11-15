package app.simplecloud.api.internal.event.blueprint;

import app.simplecloud.api.event.blueprint.BlueprintDeletedEvent;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;

class BlueprintDeletedEventImpl implements BlueprintDeletedEvent {
    private final build.buf.gen.simplecloud.controller.v2.BlueprintDeletedEvent delegate;

    BlueprintDeletedEventImpl(build.buf.gen.simplecloud.controller.v2.BlueprintDeletedEvent delegate) {
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
    @Nullable
    public String getName() {
        String name = delegate.getName();
        return name.isEmpty() ? null : name;
    }

    @Override
    public String getTimestamp() {
        return Instant.ofEpochSecond(delegate.getTimestamp()).toString();
    }
}

