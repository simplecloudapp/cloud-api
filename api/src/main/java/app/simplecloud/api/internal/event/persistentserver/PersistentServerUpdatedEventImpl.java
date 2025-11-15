package app.simplecloud.api.internal.event.persistentserver;

import app.simplecloud.api.event.persistentserver.PersistentServerUpdatedEvent;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;

class PersistentServerUpdatedEventImpl implements PersistentServerUpdatedEvent {
    private final build.buf.gen.simplecloud.controller.v2.PersistentServerUpdatedEvent delegate;

    PersistentServerUpdatedEventImpl(build.buf.gen.simplecloud.controller.v2.PersistentServerUpdatedEvent delegate) {
        this.delegate = delegate;
    }

    @Override
    public String getNetworkId() {
        return delegate.getNetworkId();
    }

    @Override
    public String getPersistentServerId() {
        return delegate.getPersistentServerId();
    }

    @Override
    @Nullable
    public String getName() {
        if (delegate.hasConfig() && delegate.getConfig().hasBaseConfig()) {
            String name = delegate.getConfig().getBaseConfig().getName();
            return name.isEmpty() ? null : name;
        }
        return null;
    }

    @Override
    public String getTimestamp() {
        return Instant.ofEpochSecond(delegate.getTimestamp()).toString();
    }
}

