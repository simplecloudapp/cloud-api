package app.simplecloud.api.presence;

import build.buf.gen.simplecloud.controller.v2.ProxyPresencePlayerSnapshot;

/**
 * Immutable player snapshot used for proxy presence reconciliation.
 */
public record ProxyPresencePlayer(
        String playerId,
        String name,
        String displayName,
        String connectedServerName,
        String connectedProxyName,
        long loginTimestampUnixMillis,
        String clientLanguage,
        int clientVersion,
        boolean onlineMode,
        String sessionId) {

    public ProxyPresencePlayer {
        playerId = normalize(playerId);
        name = normalize(name);
        displayName = normalize(displayName);
        if (displayName.isEmpty()) {
            displayName = name;
        }
        connectedServerName = normalize(connectedServerName);
        connectedProxyName = normalize(connectedProxyName);
        loginTimestampUnixMillis = Math.max(0L, loginTimestampUnixMillis);
        clientLanguage = normalize(clientLanguage);
        sessionId = normalize(sessionId);
    }

    /**
     * @deprecated use {@link #playerId()}; will be removed in 1.0.0
     */
    @Deprecated(forRemoval = true)
    public String getPlayerId() {
        return playerId;
    }

    /**
     * @deprecated use {@link #name()}; will be removed in 1.0.0
     */
    @Deprecated(forRemoval = true)
    public String getName() {
        return name;
    }

    /**
     * @deprecated use {@link #connectedServerName()}; will be removed in 1.0.0
     */
    @Deprecated(forRemoval = true)
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
