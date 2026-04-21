package app.simplecloud.api.internal.event.player;

import app.simplecloud.api.event.player.PlayerLoginEvent;

import java.time.Instant;

class PlayerLoginEventImpl implements PlayerLoginEvent {
    private final build.buf.gen.simplecloud.player.v2.PlayerLoginEvent delegate;

    PlayerLoginEventImpl(build.buf.gen.simplecloud.player.v2.PlayerLoginEvent delegate) {
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
    public String getProxyServerId() {
        return delegate.getProxyServerId();
    }

    @Override
    public String getSessionId() {
        return delegate.getSessionId();
    }

    @Override
    public boolean isNewPlayer() {
        return delegate.getIsNewPlayer();
    }

    @Override
    public boolean isNameChanged() {
        return delegate.getNameChanged();
    }

    @Override
    public String getTimestamp() {
        return Instant.ofEpochMilli(delegate.getTimestamp()).toString();
    }
}
