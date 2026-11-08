package app.simplecloud.api.internal.event.player;

import app.simplecloud.api.event.player.PlayerKickEvent;
import app.simplecloud.api.internal.util.ProtoConversionUtil;
import app.simplecloud.api.player.CloudPlayer;
import app.simplecloud.api.player.PlayerApi;
import io.nats.client.Connection;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;

import java.util.UUID;

class PlayerKickEventImpl implements PlayerKickEvent {
    private final build.buf.gen.simplecloud.player.v2.PlayerKickEvent delegate;
    private final PlayerApi playerApi;
    private final Connection natsConnection;
    private CloudPlayer player;

    PlayerKickEventImpl(
            build.buf.gen.simplecloud.player.v2.PlayerKickEvent delegate,
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
    public String getServerName() {
        return ProtoConversionUtil.emptyToNull(delegate.getServerName());
    }

    @Override
    public Component getReason() {
        if (!delegate.hasKickReason() || delegate.getKickReason().getJson().isEmpty()) {
            return null;
        }
        return GsonComponentSerializer.gson()
                .deserialize(delegate.getKickReason().getJson());
    }

    @Override
    public CloudPlayer getPlayer() {
        if (player == null && delegate.hasConfig()) {
            player = PlayerEventModelMapper.mapPlayer(
                    playerApi, natsConnection, delegate.getNetworkId(), delegate.getConfig(), false);
        }
        return player;
    }
}
