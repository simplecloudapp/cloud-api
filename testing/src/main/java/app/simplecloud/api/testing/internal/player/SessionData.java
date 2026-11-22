package app.simplecloud.api.testing.internal.player;

import app.simplecloud.api.player.PlayerClientSettings;
import app.simplecloud.api.player.PlayerSession;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

public final class SessionData {

    public static final int CLIENT_VERSION = 774;

    public final String id;
    public final Instant loginTime;
    public @Nullable Instant logoutTime;

    public SessionData(String id, Instant loginTime) {
        this.id = id;
        this.loginTime = loginTime;
    }

    public boolean isActive() {
        return logoutTime == null;
    }

    public long durationSeconds(Instant now) {
        return Duration.between(loginTime, logoutTime == null ? now : logoutTime)
                .getSeconds();
    }

    public PlayerSession toSession(String networkId, UUID playerId, @Nullable PlayerClientSettings settings) {
        PlayerSession session = new PlayerSession();
        session.setId(id);
        session.setNetworkId(networkId);
        session.setPlayerId(playerId);
        session.setClientVersion(CLIENT_VERSION);
        session.setOnlineMode(true);
        session.setLoginTime(loginTime.toString());
        if (logoutTime != null) {
            session.setLogoutTime(logoutTime.toString());
            session.setDurationSeconds(durationSeconds(logoutTime));
        }
        session.setClientSettings(settings);
        return session;
    }
}
