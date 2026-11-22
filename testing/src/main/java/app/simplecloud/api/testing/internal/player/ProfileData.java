package app.simplecloud.api.testing.internal.player;

import app.simplecloud.api.player.PlayerProfile;
import java.time.Instant;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

public record ProfileData(String id, String name, @Nullable String texture, Instant createdAt) {

    public PlayerProfile toProfile(UUID playerId) {
        return new PlayerProfile(id, playerId, name, texture, createdAt.toString());
    }
}
