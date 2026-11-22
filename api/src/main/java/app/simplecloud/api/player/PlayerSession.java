package app.simplecloud.api.player;

import java.util.UUID;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

public record PlayerSession(
        String id,
        String networkId,
        UUID playerId,
        @Nullable String addressHash,
        int clientVersion,
        boolean onlineMode,
        String loginTime,
        @Nullable String logoutTime,
        @Nullable Long durationSeconds,
        @Nullable PlayerClientSettings clientSettings) {

    public boolean isActive() {
        return logoutTime == null;
    }

    /**
     * @deprecated use {@link #id()}; will be removed in 1.0.0
     */
    @Deprecated(forRemoval = true)
    @ApiStatus.ScheduledForRemoval(inVersion = "1.0.0")
    public String getId() {
        return id;
    }

    /**
     * @deprecated use {@link #networkId()}; will be removed in 1.0.0
     */
    @Deprecated(forRemoval = true)
    @ApiStatus.ScheduledForRemoval(inVersion = "1.0.0")
    public String getNetworkId() {
        return networkId;
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
     * @deprecated use {@link #addressHash()}; will be removed in 1.0.0
     */
    @Deprecated(forRemoval = true)
    @ApiStatus.ScheduledForRemoval(inVersion = "1.0.0")
    @Nullable public String getAddressHash() {
        return addressHash;
    }

    /**
     * @deprecated use {@link #clientVersion()}; will be removed in 1.0.0
     */
    @Deprecated(forRemoval = true)
    @ApiStatus.ScheduledForRemoval(inVersion = "1.0.0")
    public int getClientVersion() {
        return clientVersion;
    }

    /**
     * @deprecated use {@link #onlineMode()}; will be removed in 1.0.0
     */
    @Deprecated(forRemoval = true)
    @ApiStatus.ScheduledForRemoval(inVersion = "1.0.0")
    public boolean isOnlineMode() {
        return onlineMode;
    }

    /**
     * @deprecated use {@link #loginTime()}; will be removed in 1.0.0
     */
    @Deprecated(forRemoval = true)
    @ApiStatus.ScheduledForRemoval(inVersion = "1.0.0")
    public String getLoginTime() {
        return loginTime;
    }

    /**
     * @deprecated use {@link #logoutTime()}; will be removed in 1.0.0
     */
    @Deprecated(forRemoval = true)
    @ApiStatus.ScheduledForRemoval(inVersion = "1.0.0")
    @Nullable public String getLogoutTime() {
        return logoutTime;
    }

    /**
     * @deprecated use {@link #durationSeconds()}; will be removed in 1.0.0
     */
    @Deprecated(forRemoval = true)
    @ApiStatus.ScheduledForRemoval(inVersion = "1.0.0")
    @Nullable public Long getDurationSeconds() {
        return durationSeconds;
    }

    /**
     * @deprecated use {@link #clientSettings()}; will be removed in 1.0.0
     */
    @Deprecated(forRemoval = true)
    @ApiStatus.ScheduledForRemoval(inVersion = "1.0.0")
    @Nullable public PlayerClientSettings getClientSettings() {
        return clientSettings;
    }
}
