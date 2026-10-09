package app.simplecloud.api.internal.event.player;

import app.simplecloud.api.event.player.PlayerClientSettingsUpdatedEvent;
import app.simplecloud.api.player.PlayerClientSettings;

import java.time.Instant;
import java.util.UUID;

class PlayerClientSettingsUpdatedEventImpl implements PlayerClientSettingsUpdatedEvent {
    private final build.buf.gen.simplecloud.player.v2.PlayerClientSettingsUpdatedEvent delegate;

    PlayerClientSettingsUpdatedEventImpl(build.buf.gen.simplecloud.player.v2.PlayerClientSettingsUpdatedEvent delegate) {
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
        return Instant.ofEpochMilli(delegate.getTimestamp()).toString();
    }

    @Override
    public String getSessionId() {
        return PlayerEventModelMapper.emptyToNull(delegate.getSessionId());
    }

    @Override
    public PlayerClientSettings getSettings() {
        return delegate.hasSettings() ? PlayerEventModelMapper.mapClientSettings(delegate.getSettings()) : null;
    }
}
