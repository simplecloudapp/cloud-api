package app.simplecloud.api.internal.event.server;

import app.simplecloud.api.event.server.ServerDeletedEvent;
import app.simplecloud.api.server.Server;

import java.time.Instant;

class ServerDeletedEventImpl implements ServerDeletedEvent {
    private final build.buf.gen.simplecloud.controller.v2.ServerDeletedEvent delegate;
    private Server server;

    ServerDeletedEventImpl(build.buf.gen.simplecloud.controller.v2.ServerDeletedEvent delegate) {
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
    public Server getServer() {
        if (server == null) {
            if (!delegate.hasConfig() && !delegate.hasGroupConfig() && !delegate.hasPersistentServerConfig()) {
                throw new IllegalStateException("Server data is not available in ServerDeletedEvent");
            }
            server = ServerEventModelMapper.mapServer(
                    delegate.getNetworkId(),
                    delegate.getServerId(),
                    delegate.getServerGroupId(),
                    delegate.getPersistentServerId(),
                    delegate.hasConfig() ? delegate.getConfig() : null,
                    delegate.hasRuntimeInfo() ? delegate.getRuntimeInfo() : null,
                    null,
                    delegate.hasGroupConfig() ? delegate.getGroupConfig() : null,
                    delegate.hasPersistentServerConfig() ? delegate.getPersistentServerConfig() : null,
                    delegate.getTimestamp()
            );
        }
        return server;
    }

    @Override
    public String getTimestamp() {
        return Instant.ofEpochSecond(delegate.getTimestamp()).toString();
    }
}
