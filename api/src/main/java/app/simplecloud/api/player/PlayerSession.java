package app.simplecloud.api.player;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

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

    public PlayerSession() {
    }

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

    @Nullable
    public String getAddressHash() {
        return addressHash;
    }

    public void setAddressHash(@Nullable String addressHash) {
        this.addressHash = addressHash;
    }

    public int getClientVersion() {
        return clientVersion;
    }

    public void setClientVersion(int clientVersion) {
        this.clientVersion = clientVersion;
    }

    public boolean isOnlineMode() {
        return onlineMode;
    }

    public void setOnlineMode(boolean onlineMode) {
        this.onlineMode = onlineMode;
    }

    public String getLoginTime() {
        return loginTime;
    }

    public void setLoginTime(String loginTime) {
        this.loginTime = loginTime;
    }

    @Nullable
    public String getLogoutTime() {
        return logoutTime;
    }

    public void setLogoutTime(@Nullable String logoutTime) {
        this.logoutTime = logoutTime;
    }

    @Nullable
    public Long getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(@Nullable Long durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public boolean isActive() {
        return logoutTime == null;
    }

    @Nullable
    public PlayerClientSettings getClientSettings() {
        return clientSettings;
    }

    public void setClientSettings(@Nullable PlayerClientSettings clientSettings) {
        this.clientSettings = clientSettings;
    }
}
