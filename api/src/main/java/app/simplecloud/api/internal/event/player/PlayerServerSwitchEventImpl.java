package app.simplecloud.api.internal.event.player;

import app.simplecloud.api.event.player.PlayerServerSwitchEvent;
import app.simplecloud.api.internal.util.ProtoConversionUtil;
import app.simplecloud.api.player.CloudPlayer;
import app.simplecloud.api.player.PlayerApi;
import io.nats.client.Connection;

import java.util.UUID;

class PlayerServerSwitchEventImpl implements PlayerServerSwitchEvent {
    private final build.buf.gen.simplecloud.player.v2.PlayerServerSwitchEvent delegate;
    private final PlayerApi playerApi;
    private final Connection natsConnection;
    private CloudPlayer player;

    PlayerServerSwitchEventImpl(
            build.buf.gen.simplecloud.player.v2.PlayerServerSwitchEvent delegate,
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
    public String getPreviousServerName() {
        return ProtoConversionUtil.emptyToNull(delegate.getPreviousServerId());
    }

    @Override
    public String getNewServerName() {
        return delegate.getNewServerId();
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
