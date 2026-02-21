package app.simplecloud.api.internal.event.server;

import app.simplecloud.api.event.server.ServerStateChangedEvent;
import app.simplecloud.api.internal.ProtoConversionUtil;
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
        ServerState state = ProtoConversionUtil.convertServerState(delegate.getOldState());
        if (state == null) {
            throw new IllegalStateException("Old state is not available in ServerStateChangedEvent");
        }
        return state;
    }

    @Override
    public ServerState getNewState() {
        ServerState state = ProtoConversionUtil.convertServerState(delegate.getNewState());
        if (state == null) {
            throw new IllegalStateException("New state is not available in ServerStateChangedEvent");
        }
        return state;
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
