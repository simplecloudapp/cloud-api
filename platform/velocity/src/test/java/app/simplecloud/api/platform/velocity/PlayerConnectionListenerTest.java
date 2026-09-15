package app.simplecloud.api.platform.velocity;

import app.simplecloud.api.internal.integration.player.LoginResult;
import app.simplecloud.api.internal.integration.player.PlayerIntegration;
import app.simplecloud.api.internal.integration.presence.ProxyPresenceTracker;
import app.simplecloud.api.platform.shared.PlayerSynchronizer;
import com.velocitypowered.api.event.Continuation;
import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.util.GameProfile;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.net.InetSocketAddress;
import java.util.List;
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
                mock(PlayerSynchronizer.class), integration, presence, "proxy-1", synchronize, ignored -> {});
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.getUsername()).thenReturn("Player");
        when(player.getGameProfile()).thenReturn(new GameProfile(playerId, "Player", List.of()));
        when(player.getRemoteAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 25565));
        when(player.getProtocolVersion()).thenReturn(ProtocolVersion.MINECRAFT_1_21);
        if (completedBeforeEvent) {
            completeRegistration(registration, outcome);
        }
        PostLoginEvent event = new PostLoginEvent(player);
        Continuation continuation = mock(Continuation.class);

        EventTask task = listener.onPlayerJoin(event);
        task.execute(continuation);
        if (!completedBeforeEvent) {
            verifyNoInteractions(continuation);
            verifyNoInteractions(synchronize);
            completeRegistration(registration, outcome);
        }

        verify(continuation).resume();
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
