package app.simplecloud.api.internal.event.persistentserver;

import app.simplecloud.api.event.persistentserver.PersistentServerCreatedEvent;
import java.time.Instant;
import org.jetbrains.annotations.Nullable;

class PersistentServerCreatedEventImpl implements PersistentServerCreatedEvent {
    private final build.buf.gen.simplecloud.controller.v2.PersistentServerCreatedEvent delegate;

    PersistentServerCreatedEventImpl(build.buf.gen.simplecloud.controller.v2.PersistentServerCreatedEvent delegate) {
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
    @Nullable public String getName() {
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
