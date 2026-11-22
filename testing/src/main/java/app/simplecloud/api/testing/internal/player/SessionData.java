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
        return new PlayerSession(
                id,
                networkId,
                playerId,
                null,
                CLIENT_VERSION,
                true,
                loginTime.toString(),
                logoutTime != null ? logoutTime.toString() : null,
                logoutTime != null ? durationSeconds(logoutTime) : null,
                settings);
    }
}
