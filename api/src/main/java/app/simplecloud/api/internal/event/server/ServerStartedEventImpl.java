package app.simplecloud.api.internal.event.server;

import app.simplecloud.api.event.server.ServerStartedEvent;
import app.simplecloud.api.internal.event.ControllerEventModelMapper;
import app.simplecloud.api.internal.util.ProtoConversionUtil;
import app.simplecloud.api.server.Server;

class ServerStartedEventImpl implements ServerStartedEvent {
    private final build.buf.gen.simplecloud.controller.v2.ServerStartedEvent delegate;
    private Server server;

    ServerStartedEventImpl(build.buf.gen.simplecloud.controller.v2.ServerStartedEvent delegate) {
        this.delegate = delegate;
    }

    @Override
    public String getNetworkId() {
        return delegate.getNetworkId();
    }

    @Override
    public String getServerId() {
        return delegate.getServerId();
    }

    @Override
    public String getServerGroupId() {
        return delegate.getServerGroupId();
    }

    @Override
    public String getPersistentServerId() {
        return delegate.getPersistentServerId();
    }

    @Override
    public Server getServer() {
        if (server == null) {
            if (!delegate.hasConfig() && !delegate.hasGroupConfig() && !delegate.hasPersistentServerConfig()) {
                throw new IllegalStateException("Server data is not available in ServerStartedEvent");
            }
            server = ControllerEventModelMapper.mapServer(
                    delegate.getNetworkId(),
                    delegate.getServerId(),
                    delegate.getServerGroupId(),
                    delegate.getPersistentServerId(),
                    delegate.hasConfig() ? delegate.getConfig() : null,
                    delegate.hasRuntimeInfo() ? delegate.getRuntimeInfo() : null,
                    delegate.getState(),
                    delegate.hasGroupConfig() ? delegate.getGroupConfig() : null,
                    delegate.hasPersistentServerConfig() ? delegate.getPersistentServerConfig() : null,
                    delegate.getTimestamp());
        }
        return server;
    }

    @Override
    public String getTimestamp() {
        return ProtoConversionUtil.epochSecondsToIso(delegate.getTimestamp());
    }
}
