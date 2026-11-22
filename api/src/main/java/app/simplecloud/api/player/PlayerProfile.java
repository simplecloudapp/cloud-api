package app.simplecloud.api.player;

import java.util.UUID;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

public record PlayerProfile(
        String id,
        UUID playerId,
        String name,
        @Nullable String texture,
        @Nullable String createdAt) {

    /**
     * @deprecated use {@link #id()}; will be removed in 1.0.0
     */
    @Deprecated(forRemoval = true)
    @ApiStatus.ScheduledForRemoval(inVersion = "1.0.0")
    public String getId() {
        return id;
    }

    /**
     * @deprecated use {@link #playerId()}; will be removed in 1.0.0
     */
    @Deprecated(forRemoval = true)
    @ApiStatus.ScheduledForRemoval(inVersion = "1.0.0")
    public UUID getPlayerId() {
        return playerId;
    }

    /**
     * @deprecated use {@link #name()}; will be removed in 1.0.0
     */
    @Deprecated(forRemoval = true)
    @ApiStatus.ScheduledForRemoval(inVersion = "1.0.0")
    public String getName() {
        return name;
    }

    /**
     * @deprecated use {@link #texture()}; will be removed in 1.0.0
     */
    @Deprecated(forRemoval = true)
    @ApiStatus.ScheduledForRemoval(inVersion = "1.0.0")
    @Nullable public String getTexture() {
        return texture;
    }

    /**
     * @deprecated use {@link #createdAt()}; will be removed in 1.0.0
     */
    @Deprecated(forRemoval = true)
    @ApiStatus.ScheduledForRemoval(inVersion = "1.0.0")
    @Nullable public String getCreatedAt() {
        return createdAt;
    }
}
