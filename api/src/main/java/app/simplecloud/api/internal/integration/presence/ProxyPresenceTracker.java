package app.simplecloud.api.internal.integration.presence;

import app.simplecloud.api.presence.ProxyPresencePlayer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks stable per-player metadata needed for proxy presence reconciliation.
 */
public final class ProxyPresenceTracker {

    private final String connectedProxyName;
    private final Map<String, TrackedPlayerMetadata> players = new ConcurrentHashMap<>();

    public ProxyPresenceTracker(String connectedProxyName) {
        this.connectedProxyName = connectedProxyName == null ? "" : connectedProxyName;
    }

    public void trackLogin(String playerId) {
        players.put(normalize(playerId), new TrackedPlayerMetadata(System.currentTimeMillis()));
    }

    public void updateSessionId(String playerId, String sessionId) {
        TrackedPlayerMetadata metadata = players.get(normalize(playerId));
        if (metadata != null) {
            metadata.setSessionId(sessionId);
        }
    }

    public void remove(String playerId) {
        players.remove(normalize(playerId));
    }

    public ProxyPresencePlayer createSnapshot(
            String playerId,
            String name,
            String displayName,
            String connectedServerName,
            String clientLanguage,
            int clientVersion,
            boolean onlineMode
    ) {
        String normalizedPlayerId = normalize(playerId);
        TrackedPlayerMetadata metadata = players.get(normalizedPlayerId);

        return new ProxyPresencePlayer(
                normalizedPlayerId,
                name,
                displayName,
                connectedServerName,
                connectedProxyName,
                metadata != null ? metadata.getLoginTimestampUnixMillis() : 0L,
                clientLanguage,
                clientVersion,
                onlineMode,
                metadata != null ? metadata.getSessionId() : ""
        );
    }

    private static String normalize(String value) {
        return value == null ? "" : value;
    }

    private static final class TrackedPlayerMetadata {

        private final long loginTimestampUnixMillis;
        private volatile String sessionId = "";

        private TrackedPlayerMetadata(long loginTimestampUnixMillis) {
            this.loginTimestampUnixMillis = loginTimestampUnixMillis;
        }

        private long getLoginTimestampUnixMillis() {
            return loginTimestampUnixMillis;
        }

        private String getSessionId() {
            return sessionId;
        }

        private void setSessionId(String sessionId) {
            this.sessionId = sessionId == null ? "" : sessionId;
        }
    }
}
