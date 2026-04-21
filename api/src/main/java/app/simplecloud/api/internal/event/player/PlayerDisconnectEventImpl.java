package app.simplecloud.api.internal.event.player;

import app.simplecloud.api.event.player.PlayerDisconnectEvent;

import java.time.Instant;

class PlayerDisconnectEventImpl implements PlayerDisconnectEvent {
    private final build.buf.gen.simplecloud.player.v2.PlayerDisconnectEvent delegate;

    PlayerDisconnectEventImpl(build.buf.gen.simplecloud.player.v2.PlayerDisconnectEvent delegate) {
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
    public String getServerId() {
        return delegate.getServerId();
    }

    @Override
    public long getSessionDurationSeconds() {
        return delegate.getSessionDurationSeconds();
    }

    @Override
    public String getTimestamp() {
        return Instant.ofEpochMilli(delegate.getTimestamp()).toString();
    }
}
