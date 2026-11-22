package app.simplecloud.api.internal.event.server;

import app.simplecloud.api.event.server.ServerStateChangedEvent;
import app.simplecloud.api.internal.util.ProtoConversionUtil;
import app.simplecloud.api.server.Server;
import app.simplecloud.api.server.ServerState;
import java.time.Instant;

class ServerStateChangedEventImpl implements ServerStateChangedEvent {
    private final build.buf.gen.simplecloud.controller.v2.ServerStateChangedEvent delegate;
    private Server server;

    ServerStateChangedEventImpl(build.buf.gen.simplecloud.controller.v2.ServerStateChangedEvent delegate) {
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
    public ServerState getOldState() {
        return ProtoConversionUtil.convertServerState(delegate.getOldState());
    }

    @Override
    public ServerState getNewState() {
        return ProtoConversionUtil.convertServerState(delegate.getNewState());
    }

    @Override
    public Server getServer() {
        if (server == null) {
            if (!delegate.hasConfig() && !delegate.hasGroupConfig() && !delegate.hasPersistentServerConfig()) {
                throw new IllegalStateException("Server data is not available in ServerStateChangedEvent");
            }
            server = ServerEventModelMapper.mapServer(
                    delegate.getNetworkId(),
                    delegate.getServerId(),
                    delegate.getServerGroupId(),
                    delegate.getPersistentServerId(),
                    delegate.hasConfig() ? delegate.getConfig() : null,
                    delegate.hasRuntimeInfo() ? delegate.getRuntimeInfo() : null,
                    delegate.getNewState(),
                    delegate.hasGroupConfig() ? delegate.getGroupConfig() : null,
                    delegate.hasPersistentServerConfig() ? delegate.getPersistentServerConfig() : null,
                    delegate.getTimestamp());
        }
        return server;
    }

    @Override
    public String getTimestamp() {
        return Instant.ofEpochSecond(delegate.getTimestamp()).toString();
    }
}
