package app.simplecloud.api.player;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * One recorded entry of a player's name and skin history.
 *
 * @see PlayerApi#getProfiles(UUID)
 */
public class PlayerProfile {
    private String id;
    private UUID playerId;
    private String name;

    @Nullable
    private String texture;

    @Nullable
    private String createdAt;

    public PlayerProfile() {}

    /**
     * @return the ID of this profile entry, not the player's UUID
     */
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

    /**
     * @return the Base64-encoded skin texture property, or null if the proxy didn't know it
     */
    @Nullable
    public String getTexture() {
        return texture;
    }

    public void setTexture(@Nullable String texture) {
        this.texture = texture;
    }

    /**
     * @return when this profile was recorded as an ISO-8601 timestamp, or null if unavailable
     */
    @Nullable
    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(@Nullable String createdAt) {
        this.createdAt = createdAt;
    }
}
