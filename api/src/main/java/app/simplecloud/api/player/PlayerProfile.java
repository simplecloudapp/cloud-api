package app.simplecloud.api.player;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class PlayerProfile {
    private String id;
    private UUID playerId;
    private String name;
    @Nullable
    private String texture;
    @Nullable
    private String createdAt;

    public PlayerProfile() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public void setPlayerId(UUID playerId) {
        this.playerId = playerId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Nullable
    public String getTexture() {
        return texture;
    }

    public void setTexture(@Nullable String texture) {
        this.texture = texture;
    }

    @Nullable
    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(@Nullable String createdAt) {
        this.createdAt = createdAt;
    }
}
