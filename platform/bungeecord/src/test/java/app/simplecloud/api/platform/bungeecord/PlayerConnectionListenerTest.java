package app.simplecloud.api.platform.bungeecord;

import app.simplecloud.api.internal.integration.player.LoginResult;
import app.simplecloud.api.internal.integration.player.PlayerLogin;
import app.simplecloud.api.internal.integration.player.PlayerSessionReporter;
import app.simplecloud.api.internal.integration.presence.ProxyPresenceTracker;
import app.simplecloud.api.platform.shared.LuckPermsSynchronizer;
import app.simplecloud.api.platform.shared.PlayerSynchronizer;
import app.simplecloud.api.player.PlayerClientSettings;
import net.md_5.bungee.api.Callback;
import net.md_5.bungee.api.SkinConfiguration;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.connection.PendingConnection;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.event.SettingsChangedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PlayerConnectionListenerTest {

    @ParameterizedTest
    @CsvSource({
            "success, false", "failure, false", "exception, false",
            "success, true", "failure, true", "exception, true"
    })
    @SuppressWarnings("unchecked")
    void loginWaitsForControllerAndAlwaysReleasesEvent(String outcome, boolean completedBeforeEvent) {
        UUID playerId = UUID.randomUUID();
        CompletableFuture<LoginResult> registration = new CompletableFuture<>();
        PlayerSessionReporter sessionReporter = mock(PlayerSessionReporter.class);
        when(sessionReporter.reportLogin(any(PlayerLogin.class))).thenReturn(registration);
        ProxyPresenceTracker presence = mock(ProxyPresenceTracker.class);
        LuckPermsSynchronizer luckPermsSynchronizer = mock(LuckPermsSynchronizer.class);
        PlayerConnectionListener listener = new PlayerConnectionListener(
                mock(Plugin.class), mock(PlayerSynchronizer.class), sessionReporter, presence, "proxy-1", luckPermsSynchronizer);
        ProxiedPlayer player = mock(ProxiedPlayer.class);
        PendingConnection connection = mock(PendingConnection.class);
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.getName()).thenReturn("Player");
        when(player.getDisplayName()).thenReturn("Player");
        when(player.getPendingConnection()).thenReturn(connection);
        when(connection.getSocketAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 25565));
        Callback<PostLoginEvent> callback = mock(Callback.class);
        if (completedBeforeEvent) {
            completeRegistration(registration, outcome);
        }
        PostLoginEvent event = new PostLoginEvent(player, null, callback);

        listener.onPlayerJoin(event);
        event.postCall();
        if (!completedBeforeEvent) {
            verifyNoInteractions(callback);
            verifyNoInteractions(luckPermsSynchronizer);
            completeRegistration(registration, outcome);
        }

        verify(callback).done(event, null);
        if (outcome.equals("success")) {
            verify(presence).updateSessionId(playerId.toString(), "session-1");
            verify(luckPermsSynchronizer).synchronize(playerId);
        } else {
            verifyNoInteractions(luckPermsSynchronizer);
        }
    }

    @Test
    void quitStopsLuckPermsSynchronizationForThePlayer() {
        UUID playerId = UUID.randomUUID();
        PlayerSessionReporter sessionReporter = mock(PlayerSessionReporter.class);
        when(sessionReporter.reportDisconnect(playerId)).thenReturn(CompletableFuture.completedFuture(null));
        LuckPermsSynchronizer luckPermsSynchronizer = mock(LuckPermsSynchronizer.class);
        PlayerConnectionListener listener = new PlayerConnectionListener(
                mock(Plugin.class), mock(PlayerSynchronizer.class), sessionReporter, mock(ProxyPresenceTracker.class), "proxy-1", luckPermsSynchronizer);
        ProxiedPlayer player = mock(ProxiedPlayer.class);
        when(player.getUniqueId()).thenReturn(playerId);

        listener.onPlayerQuit(new PlayerDisconnectEvent(player));

        verify(luckPermsSynchronizer).forget(playerId);
    }

    @Test
    @SuppressWarnings("unchecked") // mock(Callback.class) is a raw Callback passed as Callback<PostLoginEvent>
    void loginReportsThePlayerToTheController() {
        UUID playerId = UUID.randomUUID();
        InetSocketAddress address = new InetSocketAddress("127.0.0.1", 25565);
        PlayerSessionReporter sessionReporter = mock(PlayerSessionReporter.class);
        when(sessionReporter.reportLogin(any(PlayerLogin.class))).thenReturn(new CompletableFuture<>());
        PlayerConnectionListener listener = new PlayerConnectionListener(
                mock(Plugin.class), mock(PlayerSynchronizer.class), sessionReporter, mock(ProxyPresenceTracker.class), "proxy-1", LuckPermsSynchronizer.DISABLED);
        ProxiedPlayer player = mock(ProxiedPlayer.class);
        PendingConnection connection = mock(PendingConnection.class);
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.getName()).thenReturn("Player");
        when(player.getDisplayName()).thenReturn("Display");
        when(player.getPendingConnection()).thenReturn(connection);
        when(connection.getSocketAddress()).thenReturn(address);
        when(connection.getVersion()).thenReturn(767);
        when(connection.isOnlineMode()).thenReturn(true);

        listener.onPlayerJoin(new PostLoginEvent(player, null, mock(Callback.class)));

        PlayerLogin expected = new PlayerLogin(playerId, "Player", "Display", "proxy-1",
                String.valueOf(address.hashCode()), "en_US", 767, true, null);
        verify(sessionReporter).reportLogin(expected);
    }

    @Test
    @SuppressWarnings("unchecked")
    void loginSendsClientSettingsAlreadyReportedByTheClient() {
        UUID playerId = UUID.randomUUID();
        PlayerSessionReporter sessionReporter = mock(PlayerSessionReporter.class);
        when(sessionReporter.reportLogin(any(PlayerLogin.class)))
                .thenReturn(CompletableFuture.completedFuture(LoginResult.success("session-1")));
        when(sessionReporter.reportClientSettings(anyString(), any())).thenReturn(CompletableFuture.completedFuture(null));
        PlayerConnectionListener listener = new PlayerConnectionListener(
                mock(Plugin.class), mock(PlayerSynchronizer.class), sessionReporter, mock(ProxyPresenceTracker.class), "proxy-1", LuckPermsSynchronizer.DISABLED);
        ProxiedPlayer player = playerWithSettings(playerId);
        PendingConnection connection = mock(PendingConnection.class);
        when(player.getName()).thenReturn("Player");
        when(player.getDisplayName()).thenReturn("Player");
        when(player.getPendingConnection()).thenReturn(connection);
        when(connection.getSocketAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 25565));
        PostLoginEvent event = new PostLoginEvent(player, null, mock(Callback.class));

        listener.onPlayerJoin(event);
        event.postCall();

        verify(sessionReporter).reportClientSettings(eq("session-1"), any(PlayerClientSettings.class));
    }

    @Test
    void settingsChangeIsIgnoredUntilTheSessionIsKnown() {
        PlayerSessionReporter sessionReporter = mock(PlayerSessionReporter.class);
        PlayerConnectionListener listener = new PlayerConnectionListener(
                mock(Plugin.class), mock(PlayerSynchronizer.class), sessionReporter, mock(ProxyPresenceTracker.class), "proxy-1", LuckPermsSynchronizer.DISABLED);

        listener.onSettingsChanged(new SettingsChangedEvent(playerWithSettings(UUID.randomUUID())));

        verify(sessionReporter, never()).reportClientSettings(anyString(), any());
    }

    @Test
    void settingsChangeIsForwardedWithTheSession() {
        UUID playerId = UUID.randomUUID();
        PlayerSessionReporter sessionReporter = mock(PlayerSessionReporter.class);
        when(sessionReporter.reportClientSettings(anyString(), any())).thenReturn(CompletableFuture.completedFuture(null));
        ProxyPresenceTracker presence = mock(ProxyPresenceTracker.class);
        when(presence.getSessionId(playerId.toString())).thenReturn("session-1");
        PlayerConnectionListener listener = new PlayerConnectionListener(
                mock(Plugin.class), mock(PlayerSynchronizer.class), sessionReporter, presence, "proxy-1", LuckPermsSynchronizer.DISABLED);

        listener.onSettingsChanged(new SettingsChangedEvent(playerWithSettings(playerId)));

        verify(sessionReporter).reportClientSettings(eq("session-1"), any(PlayerClientSettings.class));
    }

    @Test
    void clientSettingsAreConvertedToTheCloudModel() {
        PlayerClientSettings result = PlayerConnectionListener.toClientSettings(playerWithSettings(UUID.randomUUID()));

        assertEquals(PlayerClientSettings.ChatMode.HIDDEN, result.getChatMode());
        assertEquals("de_DE", result.getLocale());
        assertEquals(PlayerClientSettings.MainHand.LEFT, result.getMainHand());
        assertEquals(12, result.getViewDistance());
        assertEquals(true, result.hasChatColors());
        assertEquals(true, result.isClientListingAllowed());
        assertEquals(List.of("JACKET", "RIGHT_PANTS"), result.getSkinParts());
    }

    private static ProxiedPlayer playerWithSettings(UUID playerId) {
        ProxiedPlayer player = mock(ProxiedPlayer.class);
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.getChatMode()).thenReturn(ProxiedPlayer.ChatMode.HIDDEN);
        when(player.getLocale()).thenReturn(Locale.GERMANY);
        when(player.getMainHand()).thenReturn(ProxiedPlayer.MainHand.LEFT);
        when(player.getViewDistance()).thenReturn((byte) 12);
        when(player.hasChatColors()).thenReturn(true);
        SkinConfiguration skinParts = mock(SkinConfiguration.class);
        when(skinParts.hasJacket()).thenReturn(true);
        when(skinParts.hasRightPants()).thenReturn(true);
        when(player.getSkinParts()).thenReturn(skinParts);
        return player;
    }

    private static void completeRegistration(CompletableFuture<LoginResult> registration, String outcome) {
        switch (outcome) {
            case "success" -> registration.complete(LoginResult.success("session-1"));
            case "failure" -> registration.complete(LoginResult.failure("Request timeout"));
            case "exception" -> registration.completeExceptionally(new IllegalStateException("Disconnected"));
            default -> throw new AssertionError(outcome);
        }
    }

}
