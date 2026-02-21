package app.simplecloud.api.internal.event.server;

import app.simplecloud.api.event.server.ServerStoppedEvent;
import app.simplecloud.api.server.Server;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;

class ServerStoppedEventImpl implements ServerStoppedEvent {
    private final build.buf.gen.simplecloud.controller.v2.ServerStoppedEvent delegate;
    private Server server;

    ServerStoppedEventImpl(build.buf.gen.simplecloud.controller.v2.ServerStoppedEvent delegate) {
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
                throw new IllegalStateException("Server data is not available in ServerStoppedEvent");
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
    public boolean getCrashed() {
        return delegate.getCrashed();
    }

    @Override
    @Nullable
    public Integer getExitCode() {
        int exitCode = delegate.getExitCode();
        return exitCode != 0 ? exitCode : null;
    }

    @Override
    @Nullable
    public String getReason() {
        String reason = delegate.getReason();
        return reason.isEmpty() ? null : reason;
    }

    @Override
    public String getTimestamp() {
        return Instant.ofEpochSecond(delegate.getTimestamp()).toString();
    }
}
