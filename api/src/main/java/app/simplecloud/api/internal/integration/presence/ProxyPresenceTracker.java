package app.simplecloud.api.internal.integration.presence;

import app.simplecloud.api.presence.ProxyPresencePlayer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks stable per-player metadata needed for proxy presence reconciliation.
 */
public final class ProxyPresenceTracker {

    private final String connectedProxyName;
    private final Map<String, TrackedPlayer> players = new ConcurrentHashMap<>();

    public ProxyPresenceTracker(String connectedProxyName) {
        this.connectedProxyName = connectedProxyName == null ? "" : connectedProxyName;
    }

    public void trackLogin(String playerId) {
        players.put(normalize(playerId), new TrackedPlayer(System.currentTimeMillis(), ""));
    }

    public void updateSessionId(String playerId, String sessionId) {
        players.computeIfPresent(
                normalize(playerId), (id, player) -> new TrackedPlayer(player.loginTimestampUnixMillis(), sessionId));
    }

    public String getSessionId(String playerId) {
        TrackedPlayer player = players.get(normalize(playerId));
        if (player == null || player.sessionId().isEmpty()) {
            return null;
        }
        return player.sessionId();
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
            boolean onlineMode) {
        String normalizedPlayerId = normalize(playerId);
        TrackedPlayer player = players.get(normalizedPlayerId);

        return new ProxyPresencePlayer(
                normalizedPlayerId,
                name,
                displayName,
                connectedServerName,
                connectedProxyName,
                player != null ? player.loginTimestampUnixMillis() : 0L,
                clientLanguage,
                clientVersion,
                onlineMode,
                player != null ? player.sessionId() : "");
    }

    private static String normalize(String value) {
        return value == null ? "" : value;
    }

    private record TrackedPlayer(long loginTimestampUnixMillis, String sessionId) {

        private TrackedPlayer {
            sessionId = normalize(sessionId);
        }
    }
}
