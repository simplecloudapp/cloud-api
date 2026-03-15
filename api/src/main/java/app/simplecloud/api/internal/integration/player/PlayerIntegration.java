package app.simplecloud.api.internal.integration.player;

import app.simplecloud.api.internal.CloudApiImpl;
import app.simplecloud.api.player.CloudPlayer;
import build.buf.gen.simplecloud.player.v2.*;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.Message;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;

/**
 * Player integration for proxy servers.
 * Handles player session lifecycle (login, disconnect, switch) and controller-initiated actions (kick, connect).
 */
public class PlayerIntegration {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final Connection natsConnection;
    private final String networkId;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private Dispatcher requestDispatcher;
    private BiFunction<String, String, CompletableFuture<Boolean>> kickHandler;
    private BiFunction<String, String, CompletableFuture<CloudPlayer.ConnectResult>> connectHandler;

    public PlayerIntegration(CloudApiImpl cloudApi) {
        this.natsConnection = cloudApi.getNatsConnection();
        this.networkId = cloudApi.getNetworkId();
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
        });
    }

    /**
     * Notifies the controller that a player disconnected.
     */
    public CompletableFuture<Void> disconnect(String playerId) {
        return CompletableFuture.runAsync(() -> {
            try {
                PlayerDisconnectRequest request = PlayerDisconnectRequest.newBuilder()
                        .setPlayerId(playerId)
                        .build();

                String subject = networkId + ".player.disconnect";
                natsConnection.request(subject, request.toByteArray(), REQUEST_TIMEOUT);
            } catch (Exception ignored) {
            }
        });
    }

    /**
     * Notifies the controller that a player switched servers.
     */
    public CompletableFuture<Void> serverSwitch(String playerId, String newServerName) {
        return CompletableFuture.runAsync(() -> {
            try {
                PlayerServerSwitchRequest request = PlayerServerSwitchRequest.newBuilder()
                        .setPlayerId(playerId)
                        .setNewServerName(newServerName)
                        .build();

                String subject = networkId + ".player.switch";
                natsConnection.request(subject, request.toByteArray(), REQUEST_TIMEOUT);
            } catch (Exception ignored) {
            }
        });
    }

    /**
     * Registers a handler for kick requests from the controller.
     * Handler receives (playerUuid, reason) and returns true if kicked, null to not respond.
     */
    public void onKick(BiFunction<String, String, CompletableFuture<Boolean>> handler) {
        this.kickHandler = handler;
    }

    /**
     * Registers a handler for connect requests from the controller.
     * Handler receives (playerUuid, serverName) and returns the result, null to not respond.
     */
    public void onConnect(BiFunction<String, String, CompletableFuture<CloudPlayer.ConnectResult>> handler) {
        this.connectHandler = handler;
    }

    /**
     * Starts listening for controller requests.
     */
    public void start() {
        if (!running.compareAndSet(false, true)) return;

        requestDispatcher = natsConnection.createDispatcher(null);
        requestDispatcher.subscribe(networkId + ".player.*.kick", this::handleKickRequest);
        requestDispatcher.subscribe(networkId + ".player.*.connect", this::handleConnectRequest);
    }

    /**
     * Stops listening for controller requests.
     */
    public void stop() {
        if (!running.compareAndSet(true, false)) return;

        if (requestDispatcher != null) {
            requestDispatcher.unsubscribe(networkId + ".player.*.kick");
            requestDispatcher.unsubscribe(networkId + ".player.*.connect");
        }
    }

    private void handleKickRequest(Message msg) {
        if (kickHandler == null) {
            sendKickResponse(msg, false);
            return;
        }

        try {
            KickPlayerRequest request = KickPlayerRequest.parseFrom(msg.getData());
            String[] parts = msg.getSubject().split("\\.");
            String playerUuid = parts.length >= 3 ? parts[2] : request.getPlayerId();
            String reason = request.hasReason() ? request.getReason().getJson() : null;

            kickHandler.apply(playerUuid, reason)
                    .thenAccept(success -> {
                        if (success != null) sendKickResponse(msg, success);
                    })
                    .exceptionally(e -> {
                        sendKickResponse(msg, false);
                        return null;
                    });
        } catch (Exception e) {
            sendKickResponse(msg, false);
        }
    }

    private void sendKickResponse(Message msg, boolean success) {
        if (msg.getReplyTo() != null) {
            try {
                KickPlayerResponse response = KickPlayerResponse.newBuilder().setSuccess(success).build();
                natsConnection.publish(msg.getReplyTo(), response.toByteArray());
            } catch (Exception ignored) {
            }
        }
    }

    private void handleConnectRequest(Message msg) {
        if (connectHandler == null) {
            sendConnectResponse(msg, CloudPlayer.ConnectResult.PLAYER_NOT_FOUND);
            return;
        }

        try {
            ConnectPlayerRequest request = ConnectPlayerRequest.parseFrom(msg.getData());
            String[] parts = msg.getSubject().split("\\.");
            String playerUuid = parts.length >= 3 ? parts[2] : request.getPlayerId();

            connectHandler.apply(playerUuid, request.getServerName())
                    .thenAccept(result -> {
                        if (result != null) sendConnectResponse(msg, result);
                    })
                    .exceptionally(e -> {
                        sendConnectResponse(msg, CloudPlayer.ConnectResult.CONNECTION_FAILED);
                        return null;
                    });
        } catch (Exception e) {
            sendConnectResponse(msg, CloudPlayer.ConnectResult.CONNECTION_FAILED);
        }
    }

    private void sendConnectResponse(Message msg, CloudPlayer.ConnectResult result) {
        if (msg.getReplyTo() != null) {
            try {
                ConnectPlayerResponse.Builder builder = ConnectPlayerResponse.newBuilder();
                switch (result) {
                    case SUCCESS:
                        builder.setResult(build.buf.gen.simplecloud.player.v2.ConnectResult.CONNECT_RESULT_SUCCESS);
                        break;
                    case SERVER_NOT_FOUND:
                        builder.setResult(build.buf.gen.simplecloud.player.v2.ConnectResult.CONNECT_RESULT_SERVER_NOT_FOUND);
                        break;
                    case ALREADY_CONNECTED:
                        builder.setResult(build.buf.gen.simplecloud.player.v2.ConnectResult.CONNECT_RESULT_ALREADY_CONNECTED);
                        break;
                    default:
                        builder.setResult(build.buf.gen.simplecloud.player.v2.ConnectResult.CONNECT_RESULT_FAILED);
                        break;
                }
                natsConnection.publish(msg.getReplyTo(), builder.build().toByteArray());
            } catch (Exception ignored) {
            }
        }
    }
}
