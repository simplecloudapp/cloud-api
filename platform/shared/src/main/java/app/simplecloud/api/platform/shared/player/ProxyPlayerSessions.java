package app.simplecloud.api.platform.shared.player;

import app.simplecloud.api.internal.integration.player.LoginResult;
import app.simplecloud.api.internal.integration.player.PlayerLogin;
import app.simplecloud.api.internal.integration.player.PlayerSessionReporter;
import app.simplecloud.api.internal.integration.presence.ProxyPresenceTracker;
import app.simplecloud.api.platform.shared.luckperms.LuckPermsSynchronizer;
import app.simplecloud.api.player.PlayerClientSettings;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
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

    public ProxyPlayerSessions(
            PlayerSessionReporter sessionReporter,
            ProxyPresenceTracker presenceTracker,
            String proxyName,
            LuckPermsSynchronizer luckPermsSynchronizer
    ) {
        this(sessionReporter, presenceTracker, proxyName, luckPermsSynchronizer, LOGIN_WAIT);
    }

    ProxyPlayerSessions(
            PlayerSessionReporter sessionReporter,
            ProxyPresenceTracker presenceTracker,
            String proxyName,
            LuckPermsSynchronizer luckPermsSynchronizer,
            Duration loginWait
    ) {
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
    public CompletableFuture<Void> login(ConnectingPlayer player, Supplier<PlayerClientSettings> clientSettings) {
        presenceTracker.trackLogin(player.id().toString());
        CompletableFuture<LoginResult> loginReport = sessionReporter.reportLogin(toPlayerLogin(player));
        CompletableFuture<Void> registration = loginReport
                .thenAccept(result -> applyLoginResult(player, result, clientSettings))
                .exceptionally(failure -> {
                    LOGGER.log(Level.SEVERE, "Failed to send login event for " + player.name(), failure);
                    return null;
                });
        return registration.copy().completeOnTimeout(null, loginWait.toMillis(), TimeUnit.MILLISECONDS);
    }

    public void disconnect(UUID playerId, String playerName) {
        sessionReporter.reportDisconnect(playerId).exceptionally(failure -> {
            LOGGER.log(Level.SEVERE, "Failed to send disconnect event for " + playerName, failure);
            return null;
        });
        presenceTracker.remove(playerId.toString());
        luckPermsSynchronizer.forget(playerId);
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
                String.valueOf(player.address().hashCode()),
                player.locale() != null ? player.locale().toString() : "en_US",
                player.protocolVersion(),
                player.onlineMode(),
                player.texture()
        );
    }

    private void applyLoginResult(ConnectingPlayer player, LoginResult result, Supplier<PlayerClientSettings> clientSettings) {
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

    private void sendClientSettings(String playerName, String sessionId, PlayerClientSettings settings) {
        sessionReporter.reportClientSettings(sessionId, settings).exceptionally(failure -> {
            LOGGER.log(Level.FINE, "Failed to send client settings for " + playerName, failure);
            return null;
        });
    }
}
