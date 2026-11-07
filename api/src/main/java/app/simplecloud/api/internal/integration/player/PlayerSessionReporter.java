package app.simplecloud.api.internal.integration.player;

import app.simplecloud.api.internal.CloudApiImpl;
import app.simplecloud.api.player.PlayerClientSettings;
import build.buf.gen.simplecloud.player.v2.ChatMode;
import build.buf.gen.simplecloud.player.v2.MainHand;
import build.buf.gen.simplecloud.player.v2.PlayerDisconnectRequest;
import build.buf.gen.simplecloud.player.v2.PlayerDisconnectResponse;
import build.buf.gen.simplecloud.player.v2.PlayerLoginRequest;
import build.buf.gen.simplecloud.player.v2.PlayerLoginResponse;
import build.buf.gen.simplecloud.player.v2.PlayerServerSwitchRequest;
import build.buf.gen.simplecloud.player.v2.PlayerServerSwitchResponse;
import build.buf.gen.simplecloud.player.v2.UpdateClientSettingsRequest;
import io.nats.client.Connection;
import io.nats.client.Message;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Reports player sessions on this proxy to the controller: logins, disconnects, server switches and client settings.
 */
public class PlayerSessionReporter {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final Connection natsConnection;
    private final String networkId;
    private final Executor executor;
    private final Map<UUID, CompletableFuture<?>> lastReports = new ConcurrentHashMap<>();

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
    public CompletableFuture<LoginResult> reportLogin(PlayerLogin login) {
        return inPlayerOrder(login.playerId(), () -> {
            try {
                PlayerLoginRequest.Builder builder = PlayerLoginRequest.newBuilder()
                        .setUniqueId(login.playerId().toString())
                        .setName(login.name())
                        .setDisplayName(login.displayName())
                        .setConnectedProxyName(login.connectedProxyName())
                        .setAddressHash(login.addressHash())
                        .setClientLanguage(login.clientLanguage())
                        .setClientVersion(login.clientVersion())
                        .setOnlineMode(login.onlineMode());

                if (login.texture() != null) builder.setTexture(login.texture());

                String subject = networkId + ".player.login";
                Message response =
                        natsConnection.request(subject, builder.build().toByteArray(), REQUEST_TIMEOUT);

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
        });
    }

    /**
     * Notifies the controller that a player disconnected.
     * Completes exceptionally if the controller rejects the request or no valid reply is received.
     */
    public CompletableFuture<Void> reportDisconnect(UUID playerId) {
        return inPlayerOrder(playerId, () -> {
            try {
                PlayerDisconnectRequest request = PlayerDisconnectRequest.newBuilder()
                        .setPlayerId(playerId.toString())
                        .build();

                String subject = networkId + ".player.disconnect";
                Message response = natsConnection.request(subject, request.toByteArray(), REQUEST_TIMEOUT);
                if (response == null) {
                    throw new TimeoutException(
                            "No controller reply within " + REQUEST_TIMEOUT.toSeconds() + " seconds");
                }
                if (!PlayerDisconnectResponse.parseFrom(response.getData()).getSuccess()) {
                    throw new IllegalStateException("Controller rejected the disconnect request");
                }
                return null;
            } catch (Exception e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                throw new CompletionException("Failed to disconnect player " + playerId + ": " + e.getMessage(), e);
            }
        });
    }

    /**
     * Notifies the controller that a player switched servers.
     * Completes exceptionally if the controller rejects the request or no valid reply is received.
     */
    public CompletableFuture<Void> reportServerSwitch(UUID playerId, String newServerName) {
        return inPlayerOrder(playerId, () -> {
            try {
                PlayerServerSwitchRequest request = PlayerServerSwitchRequest.newBuilder()
                        .setPlayerId(playerId.toString())
                        .setNewServerName(newServerName)
                        .build();

                String subject = networkId + ".player.switch";
                Message response = natsConnection.request(subject, request.toByteArray(), REQUEST_TIMEOUT);
                if (response == null) {
                    throw new TimeoutException(
                            "No controller reply within " + REQUEST_TIMEOUT.toSeconds() + " seconds");
                }
                if (!PlayerServerSwitchResponse.parseFrom(response.getData()).getSuccess()) {
                    throw new IllegalStateException("Controller rejected the server-switch request");
                }
                return null;
            } catch (Exception e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                throw new CompletionException(
                        "Failed to switch player " + playerId + " to server " + newServerName + ": " + e.getMessage(),
                        e);
            }
        });
    }

    private <T> CompletableFuture<T> inPlayerOrder(UUID playerId, Supplier<T> report) {
        AtomicReference<CompletableFuture<T>> scheduled = new AtomicReference<>();
        lastReports.compute(playerId, (id, previous) -> {
            CompletableFuture<?> predecessor = previous != null ? previous : CompletableFuture.completedFuture(null);
            CompletableFuture<T> next =
                    predecessor.handle((result, failure) -> null).thenApplyAsync(ignored -> report.get(), executor);
            scheduled.set(next);
            return next;
        });
        CompletableFuture<T> future = scheduled.get();
        future.whenComplete((result, failure) -> lastReports.remove(playerId, future));
        return future;
    }

    /**
     * Notifies the controller that a player's client settings changed.
     */
    public CompletableFuture<Void> reportClientSettings(String sessionId, PlayerClientSettings settings) {
        return CompletableFuture.runAsync(
                () -> {
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
                },
                executor);
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
