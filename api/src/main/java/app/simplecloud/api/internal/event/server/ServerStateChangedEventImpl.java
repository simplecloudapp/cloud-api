package app.simplecloud.api.internal.event.server;

import app.simplecloud.api.event.server.ServerStateChangedEvent;
import app.simplecloud.api.internal.event.ControllerEventModelMapper;
import app.simplecloud.api.internal.util.ProtoConversionUtil;
import app.simplecloud.api.server.Server;
import app.simplecloud.api.server.ServerState;

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
        return state != null ? state : ServerState.UNKNOWN_STATE;
    }

    @Override
    public ServerState getNewState() {
        ServerState state = ProtoConversionUtil.convertServerState(delegate.getNewState());
        return state != null ? state : ServerState.UNKNOWN_STATE;
    }

    @Override
    public Server getServer() {
        if (server == null) {
            if (!delegate.hasConfig() && !delegate.hasGroupConfig() && !delegate.hasPersistentServerConfig()) {
                throw new IllegalStateException("Server data is not available in ServerStateChangedEvent");
            }
            server = ControllerEventModelMapper.mapServer(
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
        return ProtoConversionUtil.epochSecondsToIso(delegate.getTimestamp());
    }
}
