package app.simplecloud.api.internal.event.group;

import app.simplecloud.api.event.group.GroupUpdatedEvent;
import app.simplecloud.api.group.Group;
import build.buf.gen.simplecloud.controller.v2.ServerGroupUpdatedEvent;

import java.time.Instant;

class GroupUpdatedEventImpl implements GroupUpdatedEvent {
    private final ServerGroupUpdatedEvent delegate;

    GroupUpdatedEventImpl(ServerGroupUpdatedEvent delegate) {
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
    public Group getGroup() {
        throw new IllegalStateException("Group data is not available in GroupUpdatedEvent. Use CloudApi.group().getGroup(serverGroupId) to fetch the group.");
    }

    @Override
    public String getTimestamp() {
        return Instant.ofEpochSecond(delegate.getTimestamp()).toString();
    }
}

