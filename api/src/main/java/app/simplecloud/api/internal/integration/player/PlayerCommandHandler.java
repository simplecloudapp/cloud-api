package app.simplecloud.api.internal.integration.player;

import app.simplecloud.api.internal.CloudApiImpl;
import app.simplecloud.api.player.CloudPlayer;
import app.simplecloud.api.server.ServerApi;
import build.buf.gen.simplecloud.player.v2.*;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.Message;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;

/**
 * Handles kick and connect requests from the controller for players on this proxy, and replies to them.
 */
public class PlayerCommandHandler {

    private final Connection natsConnection;
    private final String networkId;
    private final ServerApi serverApi;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private Dispatcher requestDispatcher;
    private BiFunction<String, String, CompletableFuture<Boolean>> kickHandler;
    private BiFunction<String, ConnectTarget, CompletableFuture<CloudPlayer.ConnectResult>> connectHandler;

    public PlayerCommandHandler(CloudApiImpl cloudApi) {
        this(cloudApi.getNatsConnection(), cloudApi.getNetworkId(), cloudApi.server());
    }

    PlayerCommandHandler(Connection natsConnection, String networkId, ServerApi serverApi) {
        this.natsConnection = natsConnection;
        this.networkId = networkId;
        this.serverApi = serverApi;
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
     * Handler receives (playerUuid, target) and returns the result, null to not respond.
     */
    public void onConnect(BiFunction<String, ConnectTarget, CompletableFuture<CloudPlayer.ConnectResult>> handler) {
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
            String reason = request.hasReason() && !request.getReason().getJson().isBlank()
                    ? request.getReason().getJson()
                    : null;

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

            ConnectTarget target = request.getServerId().isEmpty()
                    ? ConnectTarget.byName(request.getServerName(), serverApi::findServerById)
                    : ConnectTarget.byId(request.getServerId(), serverApi::findServerById);

            connectHandler.apply(playerUuid, target)
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
