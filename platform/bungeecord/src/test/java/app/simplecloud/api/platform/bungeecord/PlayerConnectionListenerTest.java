package app.simplecloud.api.platform.bungeecord;

import app.simplecloud.api.internal.integration.player.LoginResult;
import app.simplecloud.api.internal.integration.player.PlayerIntegration;
import app.simplecloud.api.internal.integration.presence.ProxyPresenceTracker;
import app.simplecloud.api.platform.shared.PlayerSynchronizer;
import net.md_5.bungee.api.Callback;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.connection.PendingConnection;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.PostLoginEvent;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.net.InetSocketAddress;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

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
        PlayerIntegration integration = mock(PlayerIntegration.class);
        when(integration.login(anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyInt(), anyBoolean(), nullable(String.class))).thenReturn(registration);
        ProxyPresenceTracker presence = mock(ProxyPresenceTracker.class);
        Consumer<UUID> synchronize = mock(Consumer.class);
        PlayerConnectionListener listener = new PlayerConnectionListener(
                mock(Plugin.class), mock(PlayerSynchronizer.class), integration, presence, "proxy-1", synchronize, ignored -> {});
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
            verifyNoInteractions(synchronize);
            completeRegistration(registration, outcome);
        }

        verify(callback).done(event, null);
        if (outcome.equals("success")) {
            verify(presence).updateSessionId(playerId.toString(), "session-1");
            verify(synchronize).accept(playerId);
        } else {
            verifyNoInteractions(synchronize);
        }
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
