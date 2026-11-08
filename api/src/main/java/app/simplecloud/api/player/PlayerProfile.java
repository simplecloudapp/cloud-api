package app.simplecloud.api.player;

import org.jetbrains.annotations.Nullable;

import java.util.Objects;
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

    /**
     * @deprecated use {@link #PlayerProfile(String, UUID, String, String, String)}
     */
    @Deprecated
    public PlayerProfile() {}

    /**
     * @param id the ID of this profile entry, not the player's UUID
     * @param playerId the player's UUID
     * @param name the player's name at that time
     * @param texture the Base64-encoded skin texture property, or null if the proxy didn't know it
     * @param createdAt when this profile was recorded as an ISO-8601 timestamp, or null if unavailable
     */
    public PlayerProfile(String id, UUID playerId, String name, @Nullable String texture, @Nullable String createdAt) {
        this.id = id;
        this.playerId = playerId;
        this.name = name;
        this.texture = texture;
        this.createdAt = createdAt;
    }

    /**
     * @return the ID of this profile entry, not the player's UUID
     */
    public String id() {
        return id;
    }

    /**
     * @return the player's UUID
     */
    public UUID playerId() {
        return playerId;
    }

    /**
     * @return the player's name at that time
     */
    public String name() {
        return name;
    }

    /**
     * @return the Base64-encoded skin texture property, or null if the proxy didn't know it
     */
    @Nullable
    public String texture() {
        return texture;
    }

    /**
     * @return when this profile was recorded as an ISO-8601 timestamp, or null if unavailable
     */
    @Nullable
    public String createdAt() {
        return createdAt;
    }

    /** @deprecated use {@link #id()} */
    @Deprecated
    public String getId() {
        return id;
    }

    /** @deprecated profiles are read-only */
    @Deprecated
    public void setId(String id) {
        this.id = id;
    }

    /** @deprecated use {@link #playerId()} */
    @Deprecated
    public UUID getPlayerId() {
        return playerId;
    }

    /** @deprecated profiles are read-only */
    @Deprecated
    public void setPlayerId(UUID playerId) {
        this.playerId = playerId;
    }

    /** @deprecated use {@link #name()} */
    @Deprecated
    public String getName() {
        return name;
    }

    /** @deprecated profiles are read-only */
    @Deprecated
    public void setName(String name) {
        this.name = name;
    }

    /** @deprecated use {@link #texture()} */
    @Deprecated
    @Nullable
    public String getTexture() {
        return texture;
    }

    /** @deprecated profiles are read-only */
    @Deprecated
    public void setTexture(@Nullable String texture) {
        this.texture = texture;
    }

    /** @deprecated use {@link #createdAt()} */
    @Deprecated
    @Nullable
    public String getCreatedAt() {
        return createdAt;
    }

    /** @deprecated profiles are read-only */
    @Deprecated
    public void setCreatedAt(@Nullable String createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof PlayerProfile other
                && Objects.equals(id, other.id)
                && Objects.equals(playerId, other.playerId)
                && Objects.equals(name, other.name)
                && Objects.equals(texture, other.texture)
                && Objects.equals(createdAt, other.createdAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, playerId, name, texture, createdAt);
    }

    @Override
    public String toString() {
        return "PlayerProfile[id=" + id + ", playerId=" + playerId + ", name=" + name + ", texture=" + texture
                + ", createdAt=" + createdAt + "]";
    }
}
