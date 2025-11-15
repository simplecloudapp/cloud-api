package app.simplecloud.api.internal.event.persistentserver;

import app.simplecloud.api.event.persistentserver.PersistentServerStartedEvent;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;

class PersistentServerStartedEventImpl implements PersistentServerStartedEvent {
    private final build.buf.gen.simplecloud.controller.v2.PersistentServerStartedEvent delegate;

    PersistentServerStartedEventImpl(build.buf.gen.simplecloud.controller.v2.PersistentServerStartedEvent delegate) {
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
    public String getServerId() {
        String serverId = delegate.getServerId();
        return serverId.isEmpty() ? null : serverId;
    }

    @Override
    public String getTimestamp() {
        return Instant.ofEpochSecond(delegate.getTimestamp()).toString();
    }
}

