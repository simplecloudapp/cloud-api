package app.simplecloud.api.player;

import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

/**
 * One session of a player on the network, from login to logout.
 *
 * @see PlayerApi#getSessions(UUID)
 * @see PlayerApi#getCurrentSession(UUID)
 */
public class PlayerSession {
    private String id;
    private String networkId;
    private UUID playerId;

    @Nullable
    private String addressHash;

    private int clientVersion;
    private boolean onlineMode;
    private String loginTime;

    @Nullable
    private String logoutTime;

    @Nullable
    private Long durationSeconds;

    @Nullable
    private PlayerClientSettings clientSettings;

    /**
     * @deprecated use the constructor with all fields
     */
    @Deprecated
    public PlayerSession() {}

    /**
     * @param id the ID of this session
     * @param networkId the network the session belongs to
     * @param playerId the player's UUID
     * @param addressHash a hash of the address the player connected from, or null if not reported
     * @param clientVersion the client's protocol version, for example {@code 765}, or 0 if unknown
     * @param onlineMode whether the proxy authenticated the player with Mojang
     * @param loginTime the login time as an ISO-8601 timestamp
     * @param logoutTime the logout time as an ISO-8601 timestamp, or null while the session is active
     * @param durationSeconds the session length in seconds, or null if the controller didn't report one
     * @param clientSettings the client settings of this session, or null if not loaded
     */
    public PlayerSession(
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
        this.id = id;
        this.networkId = networkId;
        this.playerId = playerId;
        this.addressHash = addressHash;
        this.clientVersion = clientVersion;
        this.onlineMode = onlineMode;
        this.loginTime = loginTime;
        this.logoutTime = logoutTime;
        this.durationSeconds = durationSeconds;
        this.clientSettings = clientSettings;
    }

    /**
     * @return the ID of this session
     */
    public String id() {
        return id;
    }

    /**
     * @return the network the session belongs to
     */
    public String networkId() {
        return networkId;
    }

    /**
     * @return the player's UUID
     */
    public UUID playerId() {
        return playerId;
    }

    /**
     * Returns a hash of the address the player connected from. Useful to tell whether two sessions came from the
     * same address. It is not a secure hash: for IPv4 most of the address can be read back from it.
     *
     * @return the address hash, or null if not reported
     */
    @Nullable
    public String addressHash() {
        return addressHash;
    }

    /**
     * @return the client's protocol version, for example {@code 765}, or 0 if unknown
     */
    public int clientVersion() {
        return clientVersion;
    }

    /**
     * @return whether the proxy authenticated the player with Mojang
     */
    public boolean onlineMode() {
        return onlineMode;
    }

    /**
     * @return the login time as an ISO-8601 timestamp
     */
    public String loginTime() {
        return loginTime;
    }

    /**
     * @return the logout time as an ISO-8601 timestamp, or null while the session is active
     */
    @Nullable
    public String logoutTime() {
        return logoutTime;
    }

    /**
     * @return the session length in seconds, or null if the controller didn't report one
     */
    @Nullable
    public Long durationSeconds() {
        return durationSeconds;
    }

    /**
     * @return the client settings of this session, or null if not loaded
     */
    @Nullable
    public PlayerClientSettings clientSettings() {
        return clientSettings;
    }

    /**
     * @return true if the player hasn't logged out of this session yet
     */
    public boolean isActive() {
        return logoutTime == null;
    }

    /** @deprecated use {@link #id()} */
    @Deprecated
    public String getId() {
        return id;
    }

    /** @deprecated sessions are read-only */
    @Deprecated
    public void setId(String id) {
        this.id = id;
    }

    /** @deprecated use {@link #networkId()} */
    @Deprecated
    public String getNetworkId() {
        return networkId;
    }

    /** @deprecated sessions are read-only */
    @Deprecated
    public void setNetworkId(String networkId) {
        this.networkId = networkId;
    }

    /** @deprecated use {@link #playerId()} */
    @Deprecated
    public UUID getPlayerId() {
        return playerId;
    }

    /** @deprecated sessions are read-only */
    @Deprecated
    public void setPlayerId(UUID playerId) {
        this.playerId = playerId;
    }

    /** @deprecated use {@link #addressHash()} */
    @Deprecated
    @Nullable
    public String getAddressHash() {
        return addressHash;
    }

    /** @deprecated sessions are read-only */
    @Deprecated
    public void setAddressHash(@Nullable String addressHash) {
        this.addressHash = addressHash;
    }

    /** @deprecated use {@link #clientVersion()} */
    @Deprecated
    public int getClientVersion() {
        return clientVersion;
    }

    /** @deprecated sessions are read-only */
    @Deprecated
    public void setClientVersion(int clientVersion) {
        this.clientVersion = clientVersion;
    }

    /** @deprecated use {@link #onlineMode()} */
    @Deprecated
    public boolean isOnlineMode() {
        return onlineMode;
    }

    /** @deprecated sessions are read-only */
    @Deprecated
    public void setOnlineMode(boolean onlineMode) {
        this.onlineMode = onlineMode;
    }

    /** @deprecated use {@link #loginTime()} */
    @Deprecated
    public String getLoginTime() {
        return loginTime;
    }

    /** @deprecated sessions are read-only */
    @Deprecated
    public void setLoginTime(String loginTime) {
        this.loginTime = loginTime;
    }

    /** @deprecated use {@link #logoutTime()} */
    @Deprecated
    @Nullable
    public String getLogoutTime() {
        return logoutTime;
    }

    /** @deprecated sessions are read-only */
    @Deprecated
    public void setLogoutTime(@Nullable String logoutTime) {
        this.logoutTime = logoutTime;
    }

    /** @deprecated use {@link #durationSeconds()} */
    @Deprecated
    @Nullable
    public Long getDurationSeconds() {
        return durationSeconds;
    }

    /** @deprecated sessions are read-only */
    @Deprecated
    public void setDurationSeconds(@Nullable Long durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    /** @deprecated use {@link #clientSettings()} */
    @Deprecated
    @Nullable
    public PlayerClientSettings getClientSettings() {
        return clientSettings;
    }

    /** @deprecated sessions are read-only */
    @Deprecated
    public void setClientSettings(@Nullable PlayerClientSettings clientSettings) {
        this.clientSettings = clientSettings;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof PlayerSession other
                && clientVersion == other.clientVersion
                && onlineMode == other.onlineMode
                && Objects.equals(id, other.id)
                && Objects.equals(networkId, other.networkId)
                && Objects.equals(playerId, other.playerId)
                && Objects.equals(addressHash, other.addressHash)
                && Objects.equals(loginTime, other.loginTime)
                && Objects.equals(logoutTime, other.logoutTime)
                && Objects.equals(durationSeconds, other.durationSeconds)
                && Objects.equals(clientSettings, other.clientSettings);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                id,
                networkId,
                playerId,
                addressHash,
                clientVersion,
                onlineMode,
                loginTime,
                logoutTime,
                durationSeconds,
                clientSettings);
    }

    @Override
    public String toString() {
        return "PlayerSession[id=" + id + ", networkId=" + networkId + ", playerId=" + playerId + ", addressHash="
                + addressHash + ", clientVersion=" + clientVersion + ", onlineMode=" + onlineMode + ", loginTime="
                + loginTime + ", logoutTime=" + logoutTime + ", durationSeconds=" + durationSeconds
                + ", clientSettings=" + clientSettings + "]";
    }
}
