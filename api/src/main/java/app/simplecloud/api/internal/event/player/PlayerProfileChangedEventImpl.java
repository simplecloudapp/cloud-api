package app.simplecloud.api.internal.event.player;

import app.simplecloud.api.event.player.PlayerProfileChangedEvent;
import app.simplecloud.api.player.PlayerProfile;

import java.time.Instant;
import java.util.UUID;

class PlayerProfileChangedEventImpl implements PlayerProfileChangedEvent {
    private final build.buf.gen.simplecloud.player.v2.PlayerProfileChangedEvent delegate;

    PlayerProfileChangedEventImpl(build.buf.gen.simplecloud.player.v2.PlayerProfileChangedEvent delegate) {
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
    public PlayerProfile getOldProfile() {
        return delegate.hasOldProfile() ? PlayerEventModelMapper.mapProfile(delegate.getOldProfile()) : null;
    }

    @Override
    public PlayerProfile getNewProfile() {
        return delegate.hasNewProfile() ? PlayerEventModelMapper.mapProfile(delegate.getNewProfile()) : null;
    }
}
