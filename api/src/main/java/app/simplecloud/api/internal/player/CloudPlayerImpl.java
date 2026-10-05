package app.simplecloud.api.internal.player;

import app.simplecloud.api.internal.integration.adventure.RemoteAudience;
import app.simplecloud.api.player.CloudPlayer;
import app.simplecloud.api.player.PlayerApi;
import app.simplecloud.api.player.ServerSelectionMode;
import io.nats.client.Connection;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.audience.ForwardingAudience;
import net.kyori.adventure.text.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class CloudPlayerImpl implements CloudPlayer, ForwardingAudience.Single {

    private final PlayerApi playerApi;
    private final Audience audience;

    private final UUID uniqueId;
    private final String name;
    private final String displayName;
    private final String connectedProxyName;
    private final String connectedServerName;
    private final boolean online;
    private final long onlineTimeSeconds;
    private final String sessionId;
    private final String firstSeen;
    private final String lastSeen;
    private final Map<String, String> properties;

    public CloudPlayerImpl(
            PlayerApi playerApi,
            Connection natsConnection,
            String networkId,
            UUID uniqueId,
            String name,
            String displayName,
            String connectedProxyName,
            String connectedServerName,
            boolean online,
            long onlineTimeSeconds,
            String sessionId,
            String firstSeen,
            String lastSeen,
            Map<String, String> properties
    ) {
        this.playerApi = playerApi;
        this.audience = RemoteAudience.builder(natsConnection, networkId).forPlayer(uniqueId);
        this.uniqueId = uniqueId;
        this.name = name;
        this.displayName = displayName;
        this.connectedProxyName = connectedProxyName;
        this.connectedServerName = connectedServerName;
        this.online = online;
        this.onlineTimeSeconds = onlineTimeSeconds;
        this.sessionId = sessionId;
        this.firstSeen = firstSeen;
        this.lastSeen = lastSeen;
        if (properties == null || properties.isEmpty()) {
            this.properties = Collections.emptyMap();
        } else {
            this.properties = Collections.unmodifiableMap(new HashMap<>(properties));
        }
    }

    @Override
    public Audience audience() {
        return audience;
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
        return connectedProxyName;
    }

    @Override
    public String getConnectedServerName() {
        return connectedServerName;
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
    public CompletableFuture<Void> kick(Component reason) {
        return playerApi.kick(uniqueId, reason).thenApply(ignored -> null);
    }

    @Override
    public CompletableFuture<ConnectResult> connect(String serverName) {
        return playerApi.connect(uniqueId, serverName);
    }

    @Override
    public CompletableFuture<ConnectResult> connectToGroup(String groupName, ServerSelectionMode selectionMode) {
        return playerApi.connectToGroup(uniqueId, groupName, selectionMode);
    }

    @Override
    public CompletableFuture<ConnectResult> connectToPersistentServer(String persistentServerId) {
        return playerApi.connectToPersistentServer(uniqueId, persistentServerId);
    }
}
