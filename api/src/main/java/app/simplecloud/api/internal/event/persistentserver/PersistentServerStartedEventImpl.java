package app.simplecloud.api.internal.event.persistentserver;

import app.simplecloud.api.event.persistentserver.PersistentServerStartedEvent;
import app.simplecloud.api.internal.event.ControllerEventModelMapper;
import app.simplecloud.api.internal.util.ProtoConversionUtil;
import app.simplecloud.api.persistentserver.PersistentServer;
import org.jetbrains.annotations.Nullable;

class PersistentServerStartedEventImpl implements PersistentServerStartedEvent {
    private final build.buf.gen.simplecloud.controller.v2.PersistentServerStartedEvent delegate;
    private PersistentServer persistentServer;

    PersistentServerStartedEventImpl(build.buf.gen.simplecloud.controller.v2.PersistentServerStartedEvent delegate) {
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
    public PersistentServer getPersistentServer() {
        if (persistentServer == null) {
            if (!delegate.hasConfig() || !delegate.getConfig().hasBaseConfig()) {
                throw new IllegalStateException("Persistent server data is not available in PersistentServerStartedEvent");
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
