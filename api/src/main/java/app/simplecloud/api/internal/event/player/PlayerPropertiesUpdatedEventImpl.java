package app.simplecloud.api.internal.event.player;

import app.simplecloud.api.event.player.PlayerPropertiesUpdatedEvent;
import app.simplecloud.api.player.CloudPlayer;
import app.simplecloud.api.player.PlayerApi;
import io.nats.client.Connection;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

class PlayerPropertiesUpdatedEventImpl implements PlayerPropertiesUpdatedEvent {
    private final build.buf.gen.simplecloud.player.v2.PlayerPropertiesUpdatedEvent delegate;
    private final PlayerApi playerApi;
    private final Connection natsConnection;
    private CloudPlayer player;

    PlayerPropertiesUpdatedEventImpl(
            build.buf.gen.simplecloud.player.v2.PlayerPropertiesUpdatedEvent delegate,
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
        return Instant.ofEpochMilli(delegate.getTimestamp()).toString();
    }

    @Override
    public Map<String, String> getOldProperties() {
        return delegate.getOldPropertiesMap();
    }

    @Override
    public Map<String, String> getProperties() {
        return delegate.getPropertiesMap();
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
