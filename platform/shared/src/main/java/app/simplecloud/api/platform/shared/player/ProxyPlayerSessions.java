package app.simplecloud.api.platform.shared.player;

import app.simplecloud.api.internal.integration.player.LoginResult;
import app.simplecloud.api.internal.integration.player.PlayerLogin;
import app.simplecloud.api.internal.integration.player.PlayerSessionReporter;
import app.simplecloud.api.internal.integration.presence.ProxyPresenceTracker;
import app.simplecloud.api.platform.shared.luckperms.LuckPermsSynchronizer;
import app.simplecloud.api.player.PlayerClientSettings;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Reports the sessions of the players on this proxy to the controller.
 */
public final class ProxyPlayerSessions {

    private static final Logger LOGGER = Logger.getLogger(ProxyPlayerSessions.class.getName());
    private static final Duration LOGIN_WAIT = Duration.ofSeconds(5);

    private final PlayerSessionReporter sessionReporter;
    private final ProxyPresenceTracker presenceTracker;
    private final String proxyName;
    private final LuckPermsSynchronizer luckPermsSynchronizer;
    private final Duration loginWait;
    private final Map<UUID, Object> connections = new ConcurrentHashMap<>();
    private final Map<UUID, CompletableFuture<?>> reports = new ConcurrentHashMap<>();

    public ProxyPlayerSessions(
            PlayerSessionReporter sessionReporter,
            ProxyPresenceTracker presenceTracker,
            String proxyName,
            LuckPermsSynchronizer luckPermsSynchronizer) {
        this(sessionReporter, presenceTracker, proxyName, luckPermsSynchronizer, LOGIN_WAIT);
    }

    ProxyPlayerSessions(
            PlayerSessionReporter sessionReporter,
            ProxyPresenceTracker presenceTracker,
            String proxyName,
            LuckPermsSynchronizer luckPermsSynchronizer,
            Duration loginWait) {
        this.sessionReporter = sessionReporter;
        this.presenceTracker = presenceTracker;
        this.proxyName = proxyName != null && !proxyName.isBlank() ? proxyName : "unknown";
        this.luckPermsSynchronizer = luckPermsSynchronizer;
        this.loginWait = loginWait;
    }

    /**
     * Reports the login and completes once the controller registered the session, the registration
     * failed, or the wait timed out. The returned future never completes exceptionally.
     *
     * @param clientSettings the settings the client has sent so far, or {@code null} if none yet
     */
    public CompletableFuture<Void> login(
            ConnectingPlayer player, Object connection, Supplier<PlayerClientSettings> clientSettings) {
        connections.put(player.id(), connection);
        presenceTracker.trackLogin(player.id().toString());
        CompletableFuture<LoginResult> loginReport =
                afterPreviousReport(player.id(), () -> sessionReporter.reportLogin(toPlayerLogin(player)));
        CompletableFuture<Void> registration = loginReport
                .thenAccept(result -> {
                    if (connections.get(player.id()) == connection) {
                        applyLoginResult(player, result, clientSettings);
                    }
                })
                .exceptionally(failure -> {
                    LOGGER.log(Level.SEVERE, "Failed to send login event for " + player.name(), failure);
                    return null;
                });
        return registration.copy().completeOnTimeout(null, loginWait.toMillis(), TimeUnit.MILLISECONDS);
    }

    public void disconnect(UUID playerId, String playerName, Object connection) {
        if (!connections.remove(playerId, connection)) {
            return;
        }
        presenceTracker.remove(playerId.toString());
        luckPermsSynchronizer.forget(playerId);
        afterPreviousReport(playerId, () -> sessionReporter.reportDisconnect(playerId))
                .exceptionally(failure -> {
                    LOGGER.log(Level.SEVERE, "Failed to send disconnect event for " + playerName, failure);
                    return null;
                });
    }

    public void serverSwitched(UUID playerId, String playerName, String serverName) {
        sessionReporter.reportServerSwitch(playerId, serverName).exceptionally(failure -> {
            LOGGER.log(Level.SEVERE, "Failed to send server switch event for " + playerName, failure);
            return null;
        });
    }

    /**
     * Forwards changed client settings. Settings sent before the session exists are picked up by {@link #login}.
     */
    public void clientSettingsChanged(UUID playerId, String playerName, PlayerClientSettings settings) {
        String sessionId = presenceTracker.getSessionId(playerId.toString());
        if (sessionId == null) {
            return;
        }
        sendClientSettings(playerName, sessionId, settings);
    }

    private PlayerLogin toPlayerLogin(ConnectingPlayer player) {
        return new PlayerLogin(
                player.id(),
                player.name(),
                player.displayName(),
                proxyName,
                hashAddress(player.address()),
                player.locale() != null ? player.locale().toString() : "en_US",
                player.protocolVersion(),
                player.onlineMode(),
                player.texture());
    }

    private void applyLoginResult(
            ConnectingPlayer player, LoginResult result, Supplier<PlayerClientSettings> clientSettings) {
        if (!result.success()) {
            LOGGER.warning("Login failed for " + player.name() + ": " + result.errorMessage());
            return;
        }
        String sessionId = result.sessionId();
        presenceTracker.updateSessionId(player.id().toString(), sessionId);
        luckPermsSynchronizer.synchronize(player.id());
        PlayerClientSettings settings = clientSettings.get();
        if (settings != null) {
            sendClientSettings(player.name(), sessionId, settings);
        }
    }

    private <T> CompletableFuture<T> afterPreviousReport(UUID playerId, Supplier<CompletableFuture<T>> report) {
        CompletableFuture<T> next = new CompletableFuture<>();
        CompletableFuture<?> previous = reports.put(playerId, next);
        CompletableFuture<?> start = previous != null ? previous : CompletableFuture.completedFuture(null);
        start.handle((ignored, failure) -> null)
                .thenCompose(ignored -> report.get())
                .whenComplete((result, failure) -> {
                    reports.remove(playerId, next);
                    if (failure != null) {
                        next.completeExceptionally(failure);
                    } else {
                        next.complete(result);
                    }
                });
        return next;
    }

    static String hashAddress(SocketAddress address) {
        byte[] value = address instanceof InetSocketAddress socketAddress && socketAddress.getAddress() != null
                ? socketAddress.getAddress().getAddress()
                : String.valueOf(address).getBytes(StandardCharsets.UTF_8);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private void sendClientSettings(String playerName, String sessionId, PlayerClientSettings settings) {
        sessionReporter.reportClientSettings(sessionId, settings).exceptionally(failure -> {
            LOGGER.log(Level.FINE, "Failed to send client settings for " + playerName, failure);
            return null;
        });
    }
}
