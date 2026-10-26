package app.simplecloud.api.internal.integration.player;

import app.simplecloud.api.internal.CloudApiImpl;
import app.simplecloud.api.player.CloudPlayer;
import build.buf.gen.simplecloud.player.v2.*;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.Message;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Handles kick and connect requests from the controller for players on this proxy, and replies to them.
 */
public class PlayerCommandHandler {

    private final Connection natsConnection;
    private final String networkId;
    private final Executor executor;
    private final ProxyPlayers proxyPlayers;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private Dispatcher requestDispatcher;

    public PlayerCommandHandler(CloudApiImpl cloudApi, ProxyPlayers proxyPlayers) {
        this(cloudApi.getNatsConnection(), cloudApi.getNetworkId(), cloudApi.getExecutor(), proxyPlayers);
    }

    PlayerCommandHandler(Connection natsConnection, String networkId, Executor executor, ProxyPlayers proxyPlayers) {
        this.natsConnection = natsConnection;
        this.networkId = networkId;
        this.executor = executor;
        this.proxyPlayers = proxyPlayers;
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
        try {
            KickPlayerRequest request = KickPlayerRequest.parseFrom(msg.getData());
            UUID playerId = UUID.fromString(request.getPlayerId());
            Optional<ProxyPlayer> player = proxyPlayers.findPlayer(playerId);
            // Every proxy receives the request; only the proxy holding the player answers.
            if (player.isEmpty()) return;

            Component reason = kickReason(request);
            CompletableFuture.runAsync(() -> player.get().kick(reason), executor)
                    .thenRun(() -> sendKickResponse(msg, true))
                    .exceptionally(e -> {
                        sendKickResponse(msg, false);
                        return null;
                    });
        } catch (Exception e) {
            sendKickResponse(msg, false);
        }
    }

    private static Component kickReason(KickPlayerRequest request) {
        String reasonJson = request.getReason().getJson();
        if (reasonJson.isBlank()) {
            return Component.empty();
        }
        return GsonComponentSerializer.gson().deserialize(reasonJson);
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
        try {
            ConnectPlayerRequest request = ConnectPlayerRequest.parseFrom(msg.getData());
            UUID playerId = UUID.fromString(request.getPlayerId());
            Optional<ProxyPlayer> player = proxyPlayers.findPlayer(playerId);
            // Every proxy receives the request; only the proxy holding the player answers.
            if (player.isEmpty()) return;

            ConnectTarget target = ConnectTarget.from(request);

            player.get().connect(target)
                    .thenAccept(result -> sendConnectResponse(msg, result))
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
