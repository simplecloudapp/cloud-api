package app.simplecloud.api.platform.velocity;

import app.simplecloud.api.platform.shared.player.ConnectingPlayer;
import app.simplecloud.api.platform.shared.player.ProxyPlayerSessions;
import app.simplecloud.api.player.PlayerClientSettings;
import com.velocitypowered.api.event.Continuation;
import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.player.PlayerSettings;
import com.velocitypowered.api.proxy.player.SkinParts;
import com.velocitypowered.api.util.GameProfile;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PlayerConnectionListenerTest {

    private final UUID playerId = UUID.randomUUID();
    private final ProxyPlayerSessions sessions = mock(ProxyPlayerSessions.class);
    private final PlayerConnectionListener listener = new PlayerConnectionListener(sessions);

    @Test
    void loginEventIsHeldUntilTheSessionIsRegistered() {
        CompletableFuture<Void> login = new CompletableFuture<>();
        when(sessions.login(any(), any())).thenReturn(login);
        Continuation continuation = mock(Continuation.class);

        EventTask task = listener.onPlayerJoin(new PostLoginEvent(player(List.of())));
        task.execute(continuation);
        verifyNoInteractions(continuation);
        login.complete(null);

        verify(continuation).resume();
    }

    @Test
    void quitEndsTheSession() {
        listener.onPlayerQuit(new DisconnectEvent(player(List.of()), DisconnectEvent.LoginStatus.SUCCESSFUL_LOGIN));

        verify(sessions).disconnect(playerId, "Player");
    }

    @Test
    void connectingPlayerIsReadFromThePlayerAndItsProfile() {
        List<GameProfile.Property> properties = List.of(
                new GameProfile.Property("other", "ignored", ""),
                new GameProfile.Property("textures", "texture-value", "signature"));

        ConnectingPlayer result = PlayerConnectionListener.toConnectingPlayer(player(properties));

        assertEquals(new ConnectingPlayer(playerId, "Player", "Player", new InetSocketAddress("127.0.0.1", 25565),
                Locale.GERMANY, ProtocolVersion.MINECRAFT_1_21.getProtocol(), true, "texture-value"), result);
    }

    @Test
    void clientSettingsAreConvertedToTheCloudModel() {
        PlayerClientSettings result = PlayerConnectionListener.toClientSettings(settings());

        assertEquals(PlayerClientSettings.ChatMode.COMMANDS_ONLY, result.getChatMode());
        assertEquals("de_DE", result.getLocale());
        assertEquals(PlayerClientSettings.MainHand.LEFT, result.getMainHand());
        assertEquals(12, result.getViewDistance());
        assertTrue(result.hasChatColors());
        assertFalse(result.isClientListingAllowed());
        assertEquals(List.of("CAPE", "HAT"), result.getSkinParts());
    }

    private Player player(List<GameProfile.Property> profileProperties) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.getUsername()).thenReturn("Player");
        when(player.getGameProfile()).thenReturn(new GameProfile(playerId, "Player", profileProperties));
        when(player.getRemoteAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 25565));
        when(player.getEffectiveLocale()).thenReturn(Locale.GERMANY);
        when(player.getProtocolVersion()).thenReturn(ProtocolVersion.MINECRAFT_1_21);
        when(player.isOnlineMode()).thenReturn(true);
        return player;
    }

    private static PlayerSettings settings() {
        PlayerSettings settings = mock(PlayerSettings.class);
        when(settings.getChatMode()).thenReturn(PlayerSettings.ChatMode.COMMANDS_ONLY);
        when(settings.getLocale()).thenReturn(Locale.GERMANY);
        when(settings.getMainHand()).thenReturn(PlayerSettings.MainHand.LEFT);
        when(settings.getViewDistance()).thenReturn((byte) 12);
        when(settings.hasChatColors()).thenReturn(true);
        when(settings.isClientListingAllowed()).thenReturn(false);
        when(settings.getSkinParts()).thenReturn(new SkinParts((byte) 0x41));
        return settings;
    }
}
