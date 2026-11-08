package app.simplecloud.api.internal.event.blueprint;

import app.simplecloud.api.blueprint.Blueprint;
import app.simplecloud.api.event.blueprint.BlueprintCreatedEvent;
import app.simplecloud.api.internal.event.ControllerEventModelMapper;
import app.simplecloud.api.internal.util.ProtoConversionUtil;

class BlueprintCreatedEventImpl implements BlueprintCreatedEvent {
    private final build.buf.gen.simplecloud.controller.v2.BlueprintCreatedEvent delegate;
    private Blueprint blueprint;

    BlueprintCreatedEventImpl(build.buf.gen.simplecloud.controller.v2.BlueprintCreatedEvent delegate) {
        this.delegate = delegate;
    }

    @Override
    public String getNetworkId() {
        return delegate.getNetworkId();
    }

    @Override
    public String getBlueprintId() {
        return delegate.getBlueprintId();
    }

    @Override
    public Blueprint getBlueprint() {
        if (blueprint == null) {
            if (!delegate.hasConfig()) {
                throw new IllegalStateException("Blueprint data is not available in BlueprintCreatedEvent");
            }
            blueprint = ControllerEventModelMapper.mapBlueprint(delegate.getConfig(), delegate.getTimestamp());
        }
        return blueprint;
    }

    @Override
    public String getTimestamp() {
        return ProtoConversionUtil.epochSecondsToIso(delegate.getTimestamp());
    }
}
