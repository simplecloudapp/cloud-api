package app.simplecloud.api.internal.event.persistentserver;

import app.simplecloud.api.event.persistentserver.PersistentServerDeletedEvent;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;

class PersistentServerDeletedEventImpl implements PersistentServerDeletedEvent {
    private final build.buf.gen.simplecloud.controller.v2.PersistentServerDeletedEvent delegate;

    PersistentServerDeletedEventImpl(build.buf.gen.simplecloud.controller.v2.PersistentServerDeletedEvent delegate) {
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
        String name = delegate.getName();
        return name.isEmpty() ? null : name;
    }

    @Override
    public String getTimestamp() {
        return Instant.ofEpochSecond(delegate.getTimestamp()).toString();
    }
}

