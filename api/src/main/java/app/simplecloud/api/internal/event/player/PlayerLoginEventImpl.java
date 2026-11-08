package app.simplecloud.api.internal.event.player;

import app.simplecloud.api.event.player.PlayerLoginEvent;
import app.simplecloud.api.internal.util.ProtoConversionUtil;
import app.simplecloud.api.player.CloudPlayer;
import app.simplecloud.api.player.PlayerApi;
import io.nats.client.Connection;

import java.util.UUID;

class PlayerLoginEventImpl implements PlayerLoginEvent {
    private final build.buf.gen.simplecloud.player.v2.PlayerLoginEvent delegate;
    private final PlayerApi playerApi;
    private final Connection natsConnection;
    private CloudPlayer player;

    PlayerLoginEventImpl(
            build.buf.gen.simplecloud.player.v2.PlayerLoginEvent delegate,
            PlayerApi playerApi,
            Connection natsConnection) {
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
        return ProtoConversionUtil.epochMillisToIso(delegate.getTimestamp());
    }

    @Override
    public String getProxyName() {
        return ProtoConversionUtil.emptyToNull(delegate.getProxyServerId());
    }

    @Override
    public String getSessionId() {
        String sessionId = ProtoConversionUtil.emptyToNull(delegate.getSessionId());
        if (sessionId == null && delegate.hasConfig()) {
            return ProtoConversionUtil.emptyToNull(delegate.getConfig().getSessionId());
        }
        return sessionId;
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
    public CloudPlayer getPlayer() {
        if (player == null && delegate.hasConfig()) {
            player = PlayerEventModelMapper.mapPlayer(
                    playerApi, natsConnection, delegate.getNetworkId(), delegate.getConfig(), true);
        }
        return player;
    }
}
