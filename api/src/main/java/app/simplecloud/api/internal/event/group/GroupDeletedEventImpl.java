package app.simplecloud.api.internal.event.group;

import app.simplecloud.api.event.group.GroupDeletedEvent;
import build.buf.gen.simplecloud.controller.v2.ServerGroupDeletedEvent;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;

class GroupDeletedEventImpl implements GroupDeletedEvent {
    private final ServerGroupDeletedEvent delegate;

    GroupDeletedEventImpl(ServerGroupDeletedEvent delegate) {
        this.delegate = delegate;
    }

    @Override
    public String getNetworkId() {
        return delegate.getNetworkId();
    }

    @Override
    public String getServerGroupId() {
        return delegate.getServerGroupId();
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

