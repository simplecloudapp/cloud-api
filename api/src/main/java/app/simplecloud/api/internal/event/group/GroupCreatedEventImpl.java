package app.simplecloud.api.internal.event.group;

import app.simplecloud.api.event.group.GroupCreatedEvent;
import app.simplecloud.api.group.Group;
import app.simplecloud.api.internal.event.ControllerEventModelMapper;
import app.simplecloud.api.internal.util.ProtoConversionUtil;
import build.buf.gen.simplecloud.controller.v2.ServerGroupCreatedEvent;

class GroupCreatedEventImpl implements GroupCreatedEvent {
    private final ServerGroupCreatedEvent delegate;
    private Group group;

    GroupCreatedEventImpl(ServerGroupCreatedEvent delegate) {
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
        if (group == null) {
            if (!delegate.hasConfig() || !delegate.getConfig().hasBaseConfig()) {
                throw new IllegalStateException("Group data is not available in GroupCreatedEvent");
            }
            group = ControllerEventModelMapper.mapGroup(
                    delegate.getServerGroupId(), delegate.getConfig(), delegate.getTimestamp());
        }
        return group;
    }

    @Override
    public String getTimestamp() {
        return ProtoConversionUtil.epochSecondsToIso(delegate.getTimestamp());
    }
}
