package app.simplecloud.api.internal.integration.presence;

import app.simplecloud.api.presence.ProxyPresencePlayer;
import app.simplecloud.api.presence.ProxyPresencePlayerProvider;
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
 * Responds to controller presence-compare requests for a single proxy.
 */
public final class ProxyPresenceResponder {

    private static final Logger LOGGER = Logger.getLogger(ProxyPresenceResponder.class.getName());
    private static final int FNV_32A_OFFSET_BASIS = 0x811c9dc5;
    private static final int FNV_32A_PRIME = 0x01000193;

    private final Connection natsConnection;
    private final String serverId;
    private final String subject;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile ProxyPresencePlayerProvider playerProvider;

    private Dispatcher dispatcher;

    public ProxyPresenceResponder(
            Connection natsConnection,
            String networkId,
            String serverId,
            ProxyPresencePlayerProvider playerProvider
    ) {
        this.natsConnection = Objects.requireNonNull(natsConnection, "natsConnection");
        this.serverId = serverId == null ? "" : serverId;
        this.subject = Objects.requireNonNull(networkId, "networkId") + ".server." + this.serverId + ".presence.compare";
        this.playerProvider = playerProvider;
    }

    public ProxyPresenceResponder(
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

    public void registerPlayerProvider(ProxyPresencePlayerProvider playerProvider) {
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
            List<ProxyPresencePlayer> players = currentPlayers();
            int localHash = computeHash(players);
            boolean match = localHash == request.getHash();

            LOGGER.info("[Presence] Compare request received — controller hash: " + request.getHash()
                    + ", local hash: " + localHash + ", match: " + match
                    + ", online players (" + players.size() + "): "
                    + players.stream().map(p -> p.getName() + "(" + p.getPlayerId() + ")").toList());

            ProxyPresenceCompareResponse.Builder response = ProxyPresenceCompareResponse.newBuilder()
                    .setMatch(match);

            if (!match) {
                response.addAllPlayers(players.stream().map(ProxyPresencePlayer::toProto).toList());
            }

            natsConnection.publish(replyTo, response.build().toByteArray());
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to process presence compare request for " + subject, e);
        }
    }

    private List<ProxyPresencePlayer> currentPlayers() {
        ProxyPresencePlayerProvider currentProvider = playerProvider;
        if (currentProvider == null) {
            return List.of();
        }

        Collection<ProxyPresencePlayer> suppliedPlayers = currentProvider.getProxyPresencePlayers();
        if (suppliedPlayers == null || suppliedPlayers.isEmpty()) {
            return List.of();
        }

        return suppliedPlayers.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(ProxyPresencePlayer::hashRecord))
                .toList();
    }

    static int computeHash(Collection<ProxyPresencePlayer> players) {
        if (players == null || players.isEmpty()) {
            return 0;
        }

        int hash = FNV_32A_OFFSET_BASIS;
        for (ProxyPresencePlayer player : players) {
            byte[] bytes = player.hashRecord().getBytes(StandardCharsets.UTF_8);
            for (byte currentByte : bytes) {
                hash ^= currentByte & 0xff;
                hash *= FNV_32A_PRIME;
            }
        }
        return hash;
    }
}
