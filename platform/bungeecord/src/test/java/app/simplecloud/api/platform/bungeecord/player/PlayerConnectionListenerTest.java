package app.simplecloud.api.platform.bungeecord.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import app.simplecloud.api.platform.shared.player.ConnectingPlayer;
import app.simplecloud.api.platform.shared.player.ProxyPlayerSessions;
import app.simplecloud.api.player.PlayerClientSettings;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.md_5.bungee.api.Callback;
import net.md_5.bungee.api.SkinConfiguration;
import net.md_5.bungee.api.connection.PendingConnection;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.plugin.Plugin;
import org.junit.jupiter.api.Test;

class PlayerConnectionListenerTest {

    private final UUID playerId = UUID.randomUUID();
    private final ProxyPlayerSessions sessions = mock(ProxyPlayerSessions.class);
    private final PlayerConnectionListener listener = new PlayerConnectionListener(mock(Plugin.class), sessions);

    @Test
    @SuppressWarnings("unchecked")
    void loginEventIsHeldUntilTheSessionIsRegistered() {
        CompletableFuture<Void> login = new CompletableFuture<>();
        when(sessions.login(any(), any(), any())).thenReturn(login);
        Callback<PostLoginEvent> callback = mock(Callback.class);
        PostLoginEvent event = new PostLoginEvent(player(), null, callback);

        listener.onPlayerJoin(event);
        event.postCall();
        verifyNoInteractions(callback);
        login.complete(null);

        verify(callback).done(event, null);
    }

    @Test
    void quitEndsTheSession() {
        ProxiedPlayer player = player();

        listener.onPlayerQuit(new PlayerDisconnectEvent(player));

        verify(sessions).disconnect(playerId, "Player", player);
    }

    @Test
    void connectingPlayerIsReadFromThePlayerAndItsConnection() {
        ConnectingPlayer result = PlayerConnectionListener.toConnectingPlayer(player());

        assertEquals(
                new ConnectingPlayer(
                        playerId,
                        "Player",
                        "Display",
                        new InetSocketAddress("127.0.0.1", 25565),
                        Locale.GERMANY,
                        767,
                        true,
                        null),
                result);
    }

    @Test
    void clientSettingsAreConvertedToTheCloudModel() {
        PlayerClientSettings result = PlayerConnectionListener.toClientSettings(player());

        assertEquals(
                new PlayerClientSettings(
                        PlayerClientSettings.ChatMode.HIDDEN,
                        "de_DE",
                        PlayerClientSettings.MainHand.LEFT,
                        12,
                        true,
                        true,
                        List.of("JACKET", "RIGHT_PANTS")),
                result);
    }

    private ProxiedPlayer player() {
        ProxiedPlayer player = mock(ProxiedPlayer.class);
        PendingConnection connection = mock(PendingConnection.class);
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.getName()).thenReturn("Player");
        when(player.getDisplayName()).thenReturn("Display");
        when(player.getPendingConnection()).thenReturn(connection);
        when(connection.getSocketAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 25565));
        when(connection.getVersion()).thenReturn(767);
        when(connection.isOnlineMode()).thenReturn(true);
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
}
