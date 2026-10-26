package app.simplecloud.api.internal.integration.player;

import app.simplecloud.api.internal.CloudApiImpl;
import app.simplecloud.api.player.PlayerClientSettings;
import build.buf.gen.simplecloud.player.v2.*;
import io.nats.client.Connection;
import io.nats.client.Message;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeoutException;

/**
 * Reports player sessions on this proxy to the controller: logins, disconnects, server switches and client settings.
 */
public class PlayerSessionReporter {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final Connection natsConnection;
    private final String networkId;
    private final Executor executor;

    public PlayerSessionReporter(CloudApiImpl cloudApi) {
        this(cloudApi.getNatsConnection(), cloudApi.getNetworkId(), cloudApi.getExecutor());
    }

    PlayerSessionReporter(Connection natsConnection, String networkId, Executor executor) {
        this.natsConnection = natsConnection;
        this.networkId = networkId;
        this.executor = executor;
    }

    /**
     * Notifies the controller that a player logged in.
     */
    public CompletableFuture<LoginResult> login(
            String uniqueId,
            String name,
            String displayName,
            String connectedProxyName,
            String addressHash,
            String clientLanguage,
            int clientVersion,
            boolean onlineMode,
            String texture
    ) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                PlayerLoginRequest.Builder builder = PlayerLoginRequest.newBuilder()
                        .setUniqueId(uniqueId)
                        .setName(name)
                        .setDisplayName(displayName)
                        .setConnectedProxyName(connectedProxyName)
                        .setAddressHash(addressHash)
                        .setClientLanguage(clientLanguage)
                        .setClientVersion(clientVersion)
                        .setOnlineMode(onlineMode);

                if (texture != null) builder.setTexture(texture);

                String subject = networkId + ".player.login";
                Message response = natsConnection.request(subject, builder.build().toByteArray(), REQUEST_TIMEOUT);

                if (response == null) {
                    return LoginResult.failure("Request timeout");
                }

                PlayerLoginResponse protoResponse = PlayerLoginResponse.parseFrom(response.getData());
                if (protoResponse.getSuccess()) {
                    return LoginResult.success(protoResponse.getSessionId());
                } else {
                    return LoginResult.failure(protoResponse.getErrorMessage());
                }
            } catch (Exception e) {
                return LoginResult.failure(e.getMessage());
            }
        }, executor);
    }

    /**
     * Notifies the controller that a player disconnected.
     * Completes exceptionally if the controller rejects the request or no valid reply is received.
     */
    public CompletableFuture<Void> disconnect(String playerId) {
        return CompletableFuture.runAsync(() -> {
            try {
                PlayerDisconnectRequest request = PlayerDisconnectRequest.newBuilder()
                        .setPlayerId(playerId)
                        .build();

                String subject = networkId + ".player.disconnect";
                Message response = natsConnection.request(subject, request.toByteArray(), REQUEST_TIMEOUT);
                if (response == null) {
                    throw new TimeoutException("No controller reply within " + REQUEST_TIMEOUT.toSeconds() + " seconds");
                }
                if (!PlayerDisconnectResponse.parseFrom(response.getData()).getSuccess()) {
                    throw new IllegalStateException("Controller rejected the disconnect request");
                }
            } catch (Exception e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                throw new CompletionException("Failed to disconnect player " + playerId + ": " + e.getMessage(), e);
            }
        }, executor);
    }

    /**
     * Notifies the controller that a player switched servers.
     * Completes exceptionally if the controller rejects the request or no valid reply is received.
     */
    public CompletableFuture<Void> serverSwitch(String playerId, String newServerName) {
        return CompletableFuture.runAsync(() -> {
            try {
                PlayerServerSwitchRequest request = PlayerServerSwitchRequest.newBuilder()
                        .setPlayerId(playerId)
                        .setNewServerName(newServerName)
                        .build();

                String subject = networkId + ".player.switch";
                Message response = natsConnection.request(subject, request.toByteArray(), REQUEST_TIMEOUT);
                if (response == null) {
                    throw new TimeoutException("No controller reply within " + REQUEST_TIMEOUT.toSeconds() + " seconds");
                }
                if (!PlayerServerSwitchResponse.parseFrom(response.getData()).getSuccess()) {
                    throw new IllegalStateException("Controller rejected the server-switch request");
                }
            } catch (Exception e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                throw new CompletionException("Failed to switch player " + playerId + " to server "
                        + newServerName + ": " + e.getMessage(), e);
            }
        }, executor);
    }

    /**
     * Notifies the controller that a player's client settings changed.
     */
    public CompletableFuture<Void> updateClientSettings(String sessionId, PlayerClientSettings settings) {
        return CompletableFuture.runAsync(() -> {
            try {
                build.buf.gen.simplecloud.player.v2.PlayerClientSettings.Builder settingsBuilder =
                        build.buf.gen.simplecloud.player.v2.PlayerClientSettings.newBuilder()
                                .setSessionId(sessionId)
                                .setChatMode(toProtoChatMode(settings.getChatMode()))
                                .setMainHand(toProtoMainHand(settings.getMainHand()))
                                .setViewDistance(settings.getViewDistance())
                                .setChatColors(settings.hasChatColors())
                                .setClientListingAllowed(settings.isClientListingAllowed())
                                .addAllSkinParts(settings.getSkinParts());

                if (settings.getLocale() != null) settingsBuilder.setLocale(settings.getLocale());

                UpdateClientSettingsRequest request = UpdateClientSettingsRequest.newBuilder()
                        .setSessionId(sessionId)
                        .setSettings(settingsBuilder)
                        .build();

                String subject = networkId + ".player.settings";
                natsConnection.request(subject, request.toByteArray(), REQUEST_TIMEOUT);
            } catch (Exception ignored) {
            }
        }, executor);
    }

    private static ChatMode toProtoChatMode(PlayerClientSettings.ChatMode chatMode) {
        return switch (chatMode) {
            case ENABLED -> ChatMode.CHAT_MODE_ENABLED;
            case COMMANDS_ONLY -> ChatMode.CHAT_MODE_COMMANDS_ONLY;
            case HIDDEN -> ChatMode.CHAT_MODE_HIDDEN;
            default -> ChatMode.CHAT_MODE_UNSPECIFIED;
        };
    }

    private static MainHand toProtoMainHand(PlayerClientSettings.MainHand mainHand) {
        return switch (mainHand) {
            case LEFT -> MainHand.MAIN_HAND_LEFT;
            case RIGHT -> MainHand.MAIN_HAND_RIGHT;
            default -> MainHand.MAIN_HAND_UNSPECIFIED;
        };
    }
}
