package app.simplecloud.api.testing.internal.player;

import app.simplecloud.api.player.PlayerApi;
import java.util.Map;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

public final class PlayerImpl extends AbstractPlayer {

    private final UUID uniqueId;
    private final String name;
    private final String displayName;
    private final @Nullable String proxyName;
    private final @Nullable String serverName;
    private final boolean online;
    private final long onlineTimeSeconds;
    private final @Nullable String sessionId;
    private final String firstSeen;
    private final String lastSeen;
    private final Map<String, String> properties;

    public PlayerImpl(
            PlayerApi api,
            Inbox inbox,
            UUID uniqueId,
            String name,
            String displayName,
            @Nullable String proxyName,
            @Nullable String serverName,
            boolean online,
            long onlineTimeSeconds,
            @Nullable String sessionId,
            String firstSeen,
            String lastSeen,
            Map<String, String> properties) {
        super(api, inbox);
        this.uniqueId = uniqueId;
        this.name = name;
        this.displayName = displayName;
        this.proxyName = proxyName;
        this.serverName = serverName;
        this.online = online;
        this.onlineTimeSeconds = onlineTimeSeconds;
        this.sessionId = sessionId;
        this.firstSeen = firstSeen;
        this.lastSeen = lastSeen;
        this.properties = properties;
    }

    @Override
    public UUID getUniqueId() {
        return uniqueId;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String getConnectedProxyName() {
        return proxyName;
    }

    @Override
    public String getConnectedServerName() {
        return serverName;
    }

    @Override
    public boolean isOnline() {
        return online;
    }

    @Override
    public long getOnlineTimeSeconds() {
        return onlineTimeSeconds;
    }

    @Override
    public String getSessionId() {
        return sessionId;
    }

    @Override
    public String getFirstSeen() {
        return firstSeen;
    }

    @Override
    public String getLastSeen() {
        return lastSeen;
    }

    @Override
    public Map<String, String> getProperties() {
        return properties;
    }

    @Override
    public String toString() {
        return "CloudPlayer[" + name + ", " + uniqueId + "]";
    }
}
