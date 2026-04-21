package app.simplecloud.api.internal.event.player;

import app.simplecloud.api.event.player.PlayerServerSwitchEvent;

import java.time.Instant;

class PlayerServerSwitchEventImpl implements PlayerServerSwitchEvent {
    private final build.buf.gen.simplecloud.player.v2.PlayerServerSwitchEvent delegate;

    PlayerServerSwitchEventImpl(build.buf.gen.simplecloud.player.v2.PlayerServerSwitchEvent delegate) {
        this.delegate = delegate;
    }

    @Override
    public String getNetworkId() {
        return delegate.getNetworkId();
    }

    @Override
    public String getPlayerId() {
        return delegate.getPlayerId();
    }

    @Override
    public String getPreviousServerId() {
        return delegate.getPreviousServerId();
    }

    @Override
    public String getNewServerId() {
        return delegate.getNewServerId();
    }

    @Override
    public String getTimestamp() {
        return Instant.ofEpochMilli(delegate.getTimestamp()).toString();
    }
}
