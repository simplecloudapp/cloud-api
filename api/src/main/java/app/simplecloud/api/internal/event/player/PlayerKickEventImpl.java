package app.simplecloud.api.internal.event.player;

import app.simplecloud.api.event.player.PlayerKickEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;

class PlayerKickEventImpl implements PlayerKickEvent {
    private final build.buf.gen.simplecloud.player.v2.PlayerKickEvent delegate;

    PlayerKickEventImpl(build.buf.gen.simplecloud.player.v2.PlayerKickEvent delegate) {
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
    public String getServerId() {
        return delegate.getServerId();
    }

    @Override
    @Nullable
    public Component getKickReason() {
        if (!delegate.hasKickReason()) {
            return null;
        }
        String json = delegate.getKickReason().getJson();
        return json.isEmpty() ? null : GsonComponentSerializer.gson().deserialize(json);
    }

    @Override
    public String getTimestamp() {
        return Instant.ofEpochMilli(delegate.getTimestamp()).toString();
    }
}
