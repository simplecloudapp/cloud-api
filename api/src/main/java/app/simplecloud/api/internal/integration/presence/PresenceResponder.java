package app.simplecloud.api.internal.integration.presence;

import app.simplecloud.api.presence.PresencePlayer;
import app.simplecloud.api.presence.PresencePlayerProvider;
import build.buf.gen.simplecloud.controller.v2.PresenceCompareRequest;
import build.buf.gen.simplecloud.controller.v2.ProxyPresenceCompareResponse;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.Message;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Responds to controller presence-compare requests for a single proxy or game server.
 */
public final class PresenceResponder {

    private static final Logger LOGGER = Logger.getLogger(PresenceResponder.class.getName());
    private static final int FNV_32A_OFFSET_BASIS = 0x811c9dc5;
    private static final int FNV_32A_PRIME = 0x01000193;

    private final Connection natsConnection;
    private final String serverId;
    private final String subject;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile PresencePlayerProvider playerProvider;

    private Dispatcher dispatcher;

    public PresenceResponder(
            Connection natsConnection,
            String networkId,
            String serverId,
            PresencePlayerProvider playerProvider
    ) {
        this.natsConnection = Objects.requireNonNull(natsConnection, "natsConnection");
        this.serverId = serverId == null ? "" : serverId;
        this.subject = Objects.requireNonNull(networkId, "networkId") + ".server." + this.serverId + ".presence.compare";
        this.playerProvider = playerProvider;
    }

    public PresenceResponder(
            Connection natsConnection,
            String networkId,
            String serverId
    ) {
        this(natsConnection, networkId, serverId, null);
    }

    public void start() {
        if (serverId.isBlank()) {
            LOGGER.warning("Presence responder not started because SIMPLECLOUD_UNIQUE_ID is missing");
            return;
        }
        if (!running.compareAndSet(false, true)) {
            return;
        }

        dispatcher = natsConnection.createDispatcher(null);
        dispatcher.subscribe(subject, this::handleCompareRequest);
    }

    public void registerPlayerProvider(PresencePlayerProvider playerProvider) {
        this.playerProvider = Objects.requireNonNull(playerProvider, "playerProvider");
    }

    public void unregisterPlayerProvider() {
        this.playerProvider = null;
    }

    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        if (dispatcher != null) {
            dispatcher.unsubscribe(subject);
        }
    }

    private void handleCompareRequest(Message message) {
        String replyTo = message.getReplyTo();
        if (replyTo == null || replyTo.isBlank()) {
            return;
        }

        try {
            PresenceCompareRequest request = PresenceCompareRequest.parseFrom(message.getData());
            List<PresencePlayer> players = currentPlayers();
            natsConnection.publish(replyTo, buildResponse(request, players).toByteArray());
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to process presence compare request for " + subject, e);
        }
    }

    private List<PresencePlayer> currentPlayers() {
        PresencePlayerProvider currentProvider = playerProvider;
        if (currentProvider == null) {
            return List.of();
        }

        Collection<PresencePlayer> suppliedPlayers = currentProvider.getPresencePlayers();
        if (suppliedPlayers == null || suppliedPlayers.isEmpty()) {
            return List.of();
        }

        return suppliedPlayers.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(PresencePlayer::hashRecord))
                .toList();
    }

    static ProxyPresenceCompareResponse buildResponse(
            PresenceCompareRequest request,
            Collection<PresencePlayer> players
    ) {
        Objects.requireNonNull(request, "request");
        List<PresencePlayer> currentPlayers = (players == null ? List.<PresencePlayer>of() : players).stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(PresencePlayer::hashRecord))
                .toList();
        boolean match = computeHash(currentPlayers) == request.getHash();

        ProxyPresenceCompareResponse.Builder response = ProxyPresenceCompareResponse.newBuilder()
                .setMatch(match);
        if (!match) {
            response.addAllPlayers(currentPlayers.stream().map(PresencePlayer::toProto).toList());
        }
        return response.build();
    }

    static int computeHash(Collection<PresencePlayer> players) {
        if (players == null || players.isEmpty()) {
            return 0;
        }

        List<String> records = players.stream()
                .filter(Objects::nonNull)
                .map(PresencePlayer::hashRecord)
                .sorted()
                .toList();
        String payload = records.size() + "\u001e" + String.join("\u001e", records);

        int hash = FNV_32A_OFFSET_BASIS;
        for (byte currentByte : payload.getBytes(StandardCharsets.UTF_8)) {
            hash ^= currentByte & 0xff;
            hash *= FNV_32A_PRIME;
        }
        return hash;
    }
}
