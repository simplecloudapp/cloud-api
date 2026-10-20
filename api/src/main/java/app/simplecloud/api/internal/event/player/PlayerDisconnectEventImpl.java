package app.simplecloud.api.internal.event.player;

import app.simplecloud.api.event.player.PlayerDisconnectEvent;
import app.simplecloud.api.player.CloudPlayer;
import app.simplecloud.api.player.PlayerApi;
import io.nats.client.Connection;

import java.time.Instant;
import java.util.UUID;

class PlayerDisconnectEventImpl implements PlayerDisconnectEvent {
    private final build.buf.gen.simplecloud.player.v2.PlayerDisconnectEvent delegate;
    private final PlayerApi playerApi;
    private final Connection natsConnection;
    private CloudPlayer player;

    PlayerDisconnectEventImpl(build.buf.gen.simplecloud.player.v2.PlayerDisconnectEvent delegate, PlayerApi playerApi, Connection natsConnection) {
        this.delegate = delegate;
        this.playerApi = playerApi;
        this.natsConnection = natsConnection;
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
    public String getProxyName() {
        return PlayerEventModelMapper.emptyToNull(delegate.getProxyServerId());
    }

    @Override
    public String getServerName() {
        return PlayerEventModelMapper.emptyToNull(delegate.getServerId());
    }

    @Override
    public long getSessionDurationSeconds() {
        return delegate.getSessionDurationSeconds();
    }

    @Override
    public CloudPlayer getPlayer() {
        if (player == null && delegate.hasConfig()) {
            player = PlayerEventModelMapper.mapPlayer(playerApi, natsConnection, delegate.getNetworkId(), delegate.getConfig(), false);
        }
        return player;
    }
}
