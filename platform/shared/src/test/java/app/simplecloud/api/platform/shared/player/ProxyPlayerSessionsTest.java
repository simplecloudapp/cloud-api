package app.simplecloud.api.platform.shared.player;

import app.simplecloud.api.internal.integration.player.LoginResult;
import app.simplecloud.api.internal.integration.player.PlayerLogin;
import app.simplecloud.api.internal.integration.player.PlayerSessionReporter;
import app.simplecloud.api.internal.integration.presence.ProxyPresenceTracker;
import app.simplecloud.api.platform.shared.luckperms.LuckPermsSynchronizer;
import app.simplecloud.api.player.PlayerClientSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ProxyPlayerSessionsTest {

    private static final InetSocketAddress ADDRESS = new InetSocketAddress("127.0.0.1", 25565);

    private final UUID playerId = UUID.randomUUID();
    private final PlayerSessionReporter reporter = mock(PlayerSessionReporter.class);
    private final ProxyPresenceTracker presence = new ProxyPresenceTracker("proxy-1");
    private final LuckPermsSynchronizer luckPerms = mock(LuckPermsSynchronizer.class);
    private final CompletableFuture<LoginResult> loginReport = new CompletableFuture<>();
    private ProxyPlayerSessions sessions;

    @BeforeEach
    void setUp() {
        when(reporter.reportLogin(any())).thenReturn(loginReport);
        when(reporter.reportDisconnect(any())).thenReturn(CompletableFuture.completedFuture(null));
        when(reporter.reportServerSwitch(any(), anyString())).thenReturn(CompletableFuture.completedFuture(null));
        when(reporter.reportClientSettings(anyString(), any())).thenReturn(CompletableFuture.completedFuture(null));
        sessions = sessions("proxy-1", Duration.ofSeconds(5));
    }

    @Test
    void loginReportsThePlayerOnThisProxy() {
        sessions.login(player(Locale.GERMANY), () -> null);

        verify(reporter).reportLogin(new PlayerLogin(playerId, "Player", "Display", "proxy-1",
                String.valueOf(ADDRESS.hashCode()), "de_DE", 767, true, "texture"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " "})
    void loginWithoutLocaleOrProxyNameUsesDefaults(String proxyName) {
        sessions(proxyName, Duration.ofSeconds(5)).login(player(null), () -> null);

        verify(reporter).reportLogin(new PlayerLogin(playerId, "Player", "Display", "unknown",
                String.valueOf(ADDRESS.hashCode()), "en_US", 767, true, "texture"));
    }

    @Test
    void successfulLoginCompletesAfterTheControllerRegisteredTheSession() {
        CompletableFuture<Void> login = sessions.login(player(Locale.GERMANY), () -> null);
        assertFalse(login.isDone());
        verifyNoInteractions(luckPerms);

        loginReport.complete(LoginResult.success("session-1"));

        assertTrue(login.isDone());
        assertEquals("session-1", presence.getSessionId(playerId.toString()));
        verify(luckPerms).synchronize(playerId);
    }

    @Test
    void rejectedLoginCompletesWithoutASession() {
        CompletableFuture<Void> login = sessions.login(player(Locale.GERMANY), () -> null);

        loginReport.complete(LoginResult.failure("Request timeout"));

        assertTrue(login.isDone());
        assertFalse(login.isCompletedExceptionally());
        assertNull(presence.getSessionId(playerId.toString()));
        verifyNoInteractions(luckPerms);
    }

    @Test
    void failedLoginReportCompletesWithoutASession() {
        CompletableFuture<Void> login = sessions.login(player(Locale.GERMANY), () -> null);

        loginReport.completeExceptionally(new IllegalStateException("Disconnected"));

        assertTrue(login.isDone());
        assertFalse(login.isCompletedExceptionally());
        assertNull(presence.getSessionId(playerId.toString()));
        verifyNoInteractions(luckPerms);
    }

    @Test
    void loginIsReleasedWhenTheControllerDoesNotAnswer() throws Exception {
        CompletableFuture<Void> login = sessions("proxy-1", Duration.ofMillis(10)).login(player(Locale.GERMANY), () -> null);

        login.get(1, TimeUnit.SECONDS);

        verifyNoInteractions(luckPerms);
    }

    @Test
    void clientSettingsSentBeforeTheSessionAreSentOnceItIsRegistered() {
        PlayerClientSettings settings = new PlayerClientSettings();
        sessions.login(player(Locale.GERMANY), () -> settings);

        sessions.clientSettingsChanged(playerId, "Player", settings);
        verify(reporter, never()).reportClientSettings(anyString(), any());
        loginReport.complete(LoginResult.success("session-1"));

        verify(reporter).reportClientSettings("session-1", settings);
    }

    @Test
    void loginWithoutClientSettingsSendsNone() {
        sessions.login(player(Locale.GERMANY), () -> null);

        loginReport.complete(LoginResult.success("session-1"));

        verify(reporter, never()).reportClientSettings(anyString(), any());
    }

    @Test
    void clientSettingsChangeIsForwardedWithTheSession() {
        sessions.login(player(Locale.GERMANY), () -> null);
        loginReport.complete(LoginResult.success("session-1"));
        PlayerClientSettings settings = new PlayerClientSettings();

        sessions.clientSettingsChanged(playerId, "Player", settings);

        verify(reporter).reportClientSettings("session-1", settings);
    }

    @Test
    void disconnectReportsAndForgetsThePlayer() {
        sessions.login(player(Locale.GERMANY), () -> null);
        loginReport.complete(LoginResult.success("session-1"));

        sessions.disconnect(playerId, "Player");

        verify(reporter).reportDisconnect(playerId);
        verify(luckPerms).forget(playerId);
        assertNull(presence.getSessionId(playerId.toString()));
        sessions.clientSettingsChanged(playerId, "Player", new PlayerClientSettings());
        verify(reporter, never()).reportClientSettings(anyString(), any());
    }

    @Test
    void failedDisconnectReportStillForgetsThePlayer() {
        when(reporter.reportDisconnect(playerId)).thenReturn(CompletableFuture.failedFuture(new IllegalStateException()));

        sessions.disconnect(playerId, "Player");

        verify(luckPerms).forget(playerId);
        assertNull(presence.getSessionId(playerId.toString()));
    }

    @Test
    void serverSwitchIsReported() {
        sessions.serverSwitched(playerId, "Player", "lobby-1");

        verify(reporter).reportServerSwitch(playerId, "lobby-1");
    }

    private ProxyPlayerSessions sessions(String proxyName, Duration loginWait) {
        return new ProxyPlayerSessions(reporter, presence, proxyName, luckPerms, loginWait);
    }

    private ConnectingPlayer player(Locale locale) {
        return new ConnectingPlayer(playerId, "Player", "Display", ADDRESS, locale, 767, true, "texture");
    }
}
