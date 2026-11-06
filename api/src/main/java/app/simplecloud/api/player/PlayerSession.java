package app.simplecloud.api.player;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * One stay of a player on the network, from login to logout.
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

    public PlayerSession() {}

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getNetworkId() {
        return networkId;
    }

    public void setNetworkId(String networkId) {
        this.networkId = networkId;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public void setPlayerId(UUID playerId) {
        this.playerId = playerId;
    }

    /**
     * Returns a hash of the address the player connected from.
     *
     * <p>Useful to tell whether two sessions came from the same address. It is not a secure hash:
     * for IPv4 most of the address can be read back from it.
     *
     * @return the address hash, or null if not reported
     */
    @Nullable
    public String getAddressHash() {
        return addressHash;
    }

    public void setAddressHash(@Nullable String addressHash) {
        this.addressHash = addressHash;
    }

    /**
     * @return the client's protocol version, for example {@code 765}, or 0 if unknown
     */
    public int getClientVersion() {
        return clientVersion;
    }

    public void setClientVersion(int clientVersion) {
        this.clientVersion = clientVersion;
    }

    /**
     * @return whether the proxy authenticated the player with Mojang
     */
    public boolean isOnlineMode() {
        return onlineMode;
    }

    public void setOnlineMode(boolean onlineMode) {
        this.onlineMode = onlineMode;
    }

    /**
     * @return the login time as an ISO-8601 timestamp
     */
    public String getLoginTime() {
        return loginTime;
    }

    public void setLoginTime(String loginTime) {
        this.loginTime = loginTime;
    }

    /**
     * @return the logout time as an ISO-8601 timestamp, or null while the session is active
     */
    @Nullable
    public String getLogoutTime() {
        return logoutTime;
    }

    public void setLogoutTime(@Nullable String logoutTime) {
        this.logoutTime = logoutTime;
    }

    /**
     * @return the session length in seconds, or null if the controller didn't report one
     */
    @Nullable
    public Long getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(@Nullable Long durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    /**
     * @return true if the player hasn't logged out of this session yet
     */
    public boolean isActive() {
        return logoutTime == null;
    }

    /**
     * Returns the client settings of this session.
     *
     * @return the client settings, or null if not loaded
     */
    @Nullable
    public PlayerClientSettings getClientSettings() {
        return clientSettings;
    }

    public void setClientSettings(@Nullable PlayerClientSettings clientSettings) {
        this.clientSettings = clientSettings;
    }
}
