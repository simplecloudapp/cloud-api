package app.simplecloud.api.testing.internal.player;

import app.simplecloud.api.player.PlayerProfile;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

public record ProfileData(String id, String name, @Nullable String texture, Instant createdAt) {

    public PlayerProfile toProfile(UUID playerId) {
        PlayerProfile profile = new PlayerProfile();
        profile.setId(id);
        profile.setPlayerId(playerId);
        profile.setName(name);
        profile.setTexture(texture);
        profile.setCreatedAt(createdAt.toString());
        return profile;
    }
}
