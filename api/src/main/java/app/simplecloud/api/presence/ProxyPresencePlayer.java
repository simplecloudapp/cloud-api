package app.simplecloud.api.presence;

import build.buf.gen.simplecloud.controller.v2.ProxyPresencePlayerSnapshot;

/**
 * Immutable player snapshot used for proxy presence reconciliation.
 */
public final class ProxyPresencePlayer {

    private final String playerId;
    private final String name;
    private final String displayName;
    private final String connectedServerName;
    private final String connectedProxyName;
    private final long loginTimestampUnixMillis;
    private final String clientLanguage;
    private final int clientVersion;
    private final boolean onlineMode;
    private final String sessionId;

    public ProxyPresencePlayer(
            String playerId,
            String name,
            String displayName,
            String connectedServerName,
            String connectedProxyName,
            long loginTimestampUnixMillis,
            String clientLanguage,
            int clientVersion,
            boolean onlineMode,
            String sessionId
    ) {
        String normalizedDisplayName = normalize(displayName);

        this.playerId = normalize(playerId);
        this.name = normalize(name);
        this.displayName = normalizedDisplayName.isEmpty() ? this.name : normalizedDisplayName;
        this.connectedServerName = normalize(connectedServerName);
        this.connectedProxyName = normalize(connectedProxyName);
        this.loginTimestampUnixMillis = Math.max(0L, loginTimestampUnixMillis);
        this.clientLanguage = normalize(clientLanguage);
        this.clientVersion = clientVersion;
        this.onlineMode = onlineMode;
        this.sessionId = normalize(sessionId);
    }

    public String getPlayerId() {
        return playerId;
    }

    public String getName() {
        return name;
    }

    public String getConnectedServerName() {
        return connectedServerName;
    }

    public String hashRecord() {
        return playerId + '\u001f' + connectedServerName;
    }

    public ProxyPresencePlayerSnapshot toProto() {
        return ProxyPresencePlayerSnapshot.newBuilder()
                .setPlayerId(playerId)
                .setName(name)
                .setDisplayName(displayName)
                .setConnectedServerName(connectedServerName)
                .setConnectedProxyName(connectedProxyName)
                .setLoginTimestampUnixMillis(loginTimestampUnixMillis)
                .setClientLanguage(clientLanguage)
                .setClientVersion(clientVersion)
                .setOnlineMode(onlineMode)
                .setSessionId(sessionId)
                .build();
    }

    private static String normalize(String value) {
        return value == null ? "" : value;
    }
}
