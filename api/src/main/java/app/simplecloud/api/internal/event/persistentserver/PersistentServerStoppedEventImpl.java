package app.simplecloud.api.internal.event.persistentserver;

import app.simplecloud.api.event.persistentserver.PersistentServerStoppedEvent;
import app.simplecloud.api.internal.event.ControllerEventModelMapper;
import app.simplecloud.api.internal.util.ProtoConversionUtil;
import app.simplecloud.api.persistentserver.PersistentServer;
import org.jetbrains.annotations.Nullable;

class PersistentServerStoppedEventImpl implements PersistentServerStoppedEvent {
    private final build.buf.gen.simplecloud.controller.v2.PersistentServerStoppedEvent delegate;
    private PersistentServer persistentServer;

    PersistentServerStoppedEventImpl(build.buf.gen.simplecloud.controller.v2.PersistentServerStoppedEvent delegate) {
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
    public boolean isCrashed() {
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
    public PersistentServer getPersistentServer() {
        if (persistentServer == null) {
            if (!delegate.hasConfig() || !delegate.getConfig().hasBaseConfig()) {
                throw new IllegalStateException("Persistent server data is not available in PersistentServerStoppedEvent");
            }
            persistentServer = ControllerEventModelMapper.mapPersistentServer(
                    delegate.getPersistentServerId(),
                    delegate.getConfig(),
                    delegate.hasRuntimeInfo() ? delegate.getRuntimeInfo() : null,
                    delegate.getTimestamp());
        }
        return persistentServer;
    }

    @Override
    public String getTimestamp() {
        return ProtoConversionUtil.epochSecondsToIso(delegate.getTimestamp());
    }
}
