package app.simplecloud.api.testing.internal.player;

import app.simplecloud.api.player.CloudPlayer;
import app.simplecloud.api.player.PlayerApi;
import app.simplecloud.api.player.PlayerClientSettings;
import app.simplecloud.api.testing.internal.server.ServerData;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

public final class PlayerData {

    public final UUID id;
    public String name;
    public String displayName;
    public @Nullable ServerData proxy;
    public @Nullable ServerData server;
    public long onlineTimeSeconds;
    public @Nullable Instant onlineSince;
    public Instant firstSeen;
    public Instant lastSeen;
    public @Nullable String lastLoginName;
    public @Nullable PlayerClientSettings clientSettings;
    public final Map<String, String> properties = new LinkedHashMap<>();
    public final List<ProfileData> profiles = new ArrayList<>();
    public final List<SessionData> sessions = new ArrayList<>();
    public final Inbox inbox = new Inbox();
    public @Nullable TestPlayerImpl handle;

    public PlayerData(UUID id, String name, Instant now) {
        this.id = id;
        this.name = name;
        this.displayName = name;
        this.firstSeen = now;
        this.lastSeen = now;
    }

    public @Nullable SessionData currentSession() {
        if (sessions.isEmpty()) {
            return null;
        }
        SessionData last = sessions.getLast();
        return last.isActive() ? last : null;
    }

    public boolean online() {
        return currentSession() != null;
    }

    public @Nullable String sessionId() {
        SessionData session = currentSession();
        return session == null ? null : session.id;
    }

    public @Nullable String proxyName() {
        return proxy == null ? null : proxy.name();
    }

    public @Nullable String serverName() {
        return server == null ? null : server.name();
    }

    public @Nullable ProfileData latestProfile() {
        return profiles.isEmpty() ? null : profiles.getLast();
    }

    public long onlineTimeSeconds(Instant now) {
        if (onlineSince == null) {
            return onlineTimeSeconds;
        }
        return onlineTimeSeconds + Duration.between(onlineSince, now).getSeconds();
    }

    public @Nullable PlayerClientSettings clientSettings() {
        return clientSettings;
    }

    public CloudPlayer snapshot(PlayerApi api, Instant now) {
        return new PlayerImpl(
                api,
                inbox,
                id,
                name,
                displayName,
                proxyName(),
                serverName(),
                online(),
                onlineTimeSeconds(now),
                sessionId(),
                firstSeen.toString(),
                lastSeen.toString(),
                Map.copyOf(properties));
    }
}
