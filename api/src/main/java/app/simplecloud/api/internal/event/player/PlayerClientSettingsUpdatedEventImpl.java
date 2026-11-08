package app.simplecloud.api.internal.event.player;

import app.simplecloud.api.event.player.PlayerClientSettingsUpdatedEvent;
import app.simplecloud.api.internal.util.ProtoConversionUtil;
import app.simplecloud.api.player.PlayerClientSettings;

import java.util.UUID;

class PlayerClientSettingsUpdatedEventImpl implements PlayerClientSettingsUpdatedEvent {
    private final build.buf.gen.simplecloud.player.v2.PlayerClientSettingsUpdatedEvent delegate;

    PlayerClientSettingsUpdatedEventImpl(
            build.buf.gen.simplecloud.player.v2.PlayerClientSettingsUpdatedEvent delegate) {
        this.delegate = delegate;
    }

    @Override
    public String getNetworkId() {
        return delegate.getNetworkId();
    }

    @Override
    public UUID getPlayerId() {
        return UUID.fromString(delegate.getPlayerId());
    }

    @Override
    public String getTimestamp() {
        return ProtoConversionUtil.epochMillisToIso(delegate.getTimestamp());
    }

    @Override
    public String getSessionId() {
        return ProtoConversionUtil.emptyToNull(delegate.getSessionId());
    }

    @Override
    public PlayerClientSettings getSettings() {
        return delegate.hasSettings() ? PlayerEventModelMapper.mapClientSettings(delegate.getSettings()) : null;
    }
}
