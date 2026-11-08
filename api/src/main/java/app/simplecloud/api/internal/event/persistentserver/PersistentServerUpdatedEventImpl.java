package app.simplecloud.api.internal.event.persistentserver;

import app.simplecloud.api.event.persistentserver.PersistentServerUpdatedEvent;
import app.simplecloud.api.internal.event.ControllerEventModelMapper;
import app.simplecloud.api.internal.util.ProtoConversionUtil;
import app.simplecloud.api.persistentserver.PersistentServer;
import org.jetbrains.annotations.Nullable;

class PersistentServerUpdatedEventImpl implements PersistentServerUpdatedEvent {
    private final build.buf.gen.simplecloud.controller.v2.PersistentServerUpdatedEvent delegate;
    private PersistentServer persistentServer;

    PersistentServerUpdatedEventImpl(build.buf.gen.simplecloud.controller.v2.PersistentServerUpdatedEvent delegate) {
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
    public String getName() {
        if (delegate.hasConfig() && delegate.getConfig().hasBaseConfig()) {
            String name = delegate.getConfig().getBaseConfig().getName();
            return name.isEmpty() ? null : name;
        }
        return null;
    }

    @Override
    public PersistentServer getPersistentServer() {
        if (persistentServer == null) {
            if (!delegate.hasConfig() || !delegate.getConfig().hasBaseConfig()) {
                throw new IllegalStateException("Persistent server data is not available in PersistentServerUpdatedEvent");
            }
            persistentServer = ControllerEventModelMapper.mapPersistentServer(
                    delegate.getPersistentServerId(),
                    delegate.getConfig(),
                    null,
                    delegate.getTimestamp());
        }
        return persistentServer;
    }

    @Override
    public String getTimestamp() {
        return ProtoConversionUtil.epochSecondsToIso(delegate.getTimestamp());
    }
}
