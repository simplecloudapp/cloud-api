package app.simplecloud.api.internal.player;

import app.simplecloud.api.internal.integration.adventure.RemoteAudience;
import app.simplecloud.api.player.CloudPlayer;
import build.buf.gen.simplecloud.adventure.v1.AdventureComponent;
import build.buf.gen.simplecloud.player.v2.ConnectPlayerRequest;
import build.buf.gen.simplecloud.player.v2.ConnectPlayerResponse;
import build.buf.gen.simplecloud.player.v2.KickPlayerRequest;
import build.buf.gen.simplecloud.player.v2.KickPlayerResponse;
import io.nats.client.Connection;
import io.nats.client.Message;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.audience.ForwardingAudience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Implementation of CloudPlayer that sends adventure actions via NATS.
 */
public class CloudPlayerImpl implements CloudPlayer, ForwardingAudience.Single {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final Connection natsConnection;
    private final String networkId;
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
        this.natsConnection = natsConnection;
        this.networkId = networkId;
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

    // ===== CloudPlayer methods =====

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
        return CompletableFuture.runAsync(() -> {
            try {
                KickPlayerRequest.Builder builder = KickPlayerRequest.newBuilder()
                        .setPlayerId(uniqueId.toString());

                if (reason != null) {
                    builder.setReason(AdventureComponent.newBuilder()
                            .setJson(GsonComponentSerializer.gson().serialize(reason))
                            .build());
                }

                String subject = networkId + ".player." + uniqueId + ".kick";
                Message response = natsConnection.request(subject, builder.build().toByteArray(), REQUEST_TIMEOUT);

                if (response != null) {
                    KickPlayerResponse.parseFrom(response.getData());
                }
            } catch (Exception ignored) {
            }
        });
    }

    @Override
    public CompletableFuture<ConnectResult> connect(String serverName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ConnectPlayerRequest request = ConnectPlayerRequest.newBuilder()
                        .setPlayerId(uniqueId.toString())
                        .setServerName(serverName)
                        .build();

                String subject = networkId + ".player." + uniqueId + ".connect";
                Message response = natsConnection.request(subject, request.toByteArray(), REQUEST_TIMEOUT);

                if (response == null) {
                    return ConnectResult.CONNECTION_FAILED;
                }

                ConnectPlayerResponse protoResponse = ConnectPlayerResponse.parseFrom(response.getData());
                switch (protoResponse.getResult()) {
                    case CONNECT_RESULT_SUCCESS:
                        return ConnectResult.SUCCESS;
                    case CONNECT_RESULT_SERVER_NOT_FOUND:
                        return ConnectResult.SERVER_NOT_FOUND;
                    case CONNECT_RESULT_ALREADY_CONNECTED:
                        return ConnectResult.ALREADY_CONNECTED;
                    default:
                        return ConnectResult.CONNECTION_FAILED;
                }
            } catch (Exception e) {
                return ConnectResult.CONNECTION_FAILED;
            }
        });
    }
}
