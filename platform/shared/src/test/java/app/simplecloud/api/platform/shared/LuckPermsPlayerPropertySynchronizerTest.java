package app.simplecloud.api.platform.shared;

import app.simplecloud.api.CloudApi;
import app.simplecloud.api.player.CloudPlayer;
import app.simplecloud.api.player.PlayerApi;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.event.EventBus;
import net.luckperms.api.event.user.UserDataRecalculateEvent;
import net.luckperms.api.model.user.User;
import net.luckperms.api.model.user.UserManager;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LuckPermsPlayerPropertySynchronizerTest {

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void recalculationWaitsForRegistrationAndStopsAfterDisconnect() {
        UUID playerId = UUID.randomUUID();
        CloudApi cloudApi = mock(CloudApi.class);
        PlayerApi playerApi = mock(PlayerApi.class);
        LuckPerms luckPerms = mock(LuckPerms.class);
        EventBus eventBus = mock(EventBus.class);
        UserManager userManager = mock(UserManager.class);
        User user = mock(User.class);
        CloudPlayer player = mock(CloudPlayer.class);
        UserDataRecalculateEvent event = mock(UserDataRecalculateEvent.class);
        when(cloudApi.player()).thenReturn(playerApi);
        when(luckPerms.getEventBus()).thenReturn(eventBus);
        when(luckPerms.getUserManager()).thenReturn(userManager);
        when(userManager.getUser(playerId)).thenReturn(user);
        when(user.getUniqueId()).thenReturn(playerId);
        when(user.getPrimaryGroup()).thenReturn("default");
        when(event.getUser()).thenReturn(user);
        when(player.getProperties()).thenReturn(Map.of());
        when(playerApi.find(playerId)).thenReturn(CompletableFuture.completedFuture(Optional.of(player)));
        when(playerApi.updatePlayerProperty(eq(playerId), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(Map.of()));
        OnlinePlayers everyoneOnline = ignored -> true;
        FailureLog failOnFailure = (message, cause) -> {
            throw new AssertionError(message, cause);
        };
        LuckPermsPlayerPropertySynchronizer synchronizer = new LuckPermsPlayerPropertySynchronizer(
                cloudApi, luckPerms, this, everyoneOnline, failOnFailure);
        synchronizer.start();
        ArgumentCaptor<Consumer<UserDataRecalculateEvent>> callback = ArgumentCaptor.forClass(Consumer.class);
        verify(eventBus).subscribe(eq(this), eq(UserDataRecalculateEvent.class), callback.capture());

        callback.getValue().accept(event);
        verifyNoInteractions(playerApi);

        synchronizer.synchronize(playerId);
        verify(playerApi).find(playerId);
        verify(playerApi).updatePlayerProperty(playerId, "luckperms-primary-group", "default");

        when(user.getPrimaryGroup()).thenReturn("admin");
        callback.getValue().accept(event);
        verify(playerApi, times(2)).find(playerId);
        verify(playerApi).updatePlayerProperty(playerId, "luckperms-primary-group", "admin");

        synchronizer.forget(playerId);
        callback.getValue().accept(event);
        verify(playerApi, times(2)).find(playerId);

        synchronizer.synchronize(playerId);
        verify(playerApi, times(3)).find(playerId);
        synchronizer.stop();
        synchronizer.start();
        callback.getValue().accept(event);
        verify(playerApi, times(3)).find(playerId);
    }

    @Test
    void playerNotOnThisServerIsNotSynchronized() {
        UUID playerId = UUID.randomUUID();
        CloudApi cloudApi = mock(CloudApi.class);
        PlayerApi playerApi = mock(PlayerApi.class);
        when(cloudApi.player()).thenReturn(playerApi);
        LuckPerms luckPerms = mock(LuckPerms.class);
        when(luckPerms.getEventBus()).thenReturn(mock(EventBus.class));
        OnlinePlayers nobodyOnline = ignored -> false;
        LuckPermsPlayerPropertySynchronizer synchronizer = new LuckPermsPlayerPropertySynchronizer(
                cloudApi, luckPerms, this, nobodyOnline, (message, cause) -> {});
        synchronizer.start();

        synchronizer.synchronize(playerId);

        verifyNoInteractions(playerApi);
        verify(luckPerms, never()).getUserManager();
    }

    @Test
    void failedSynchronizationIsWrittenToTheFailureLog() {
        UUID playerId = UUID.randomUUID();
        CloudApi cloudApi = mock(CloudApi.class);
        PlayerApi playerApi = mock(PlayerApi.class);
        LuckPerms luckPerms = mock(LuckPerms.class);
        UserManager userManager = mock(UserManager.class);
        User user = mock(User.class);
        IllegalStateException controllerFailure = new IllegalStateException("controller unavailable");
        when(cloudApi.player()).thenReturn(playerApi);
        when(luckPerms.getEventBus()).thenReturn(mock(EventBus.class));
        when(luckPerms.getUserManager()).thenReturn(userManager);
        when(userManager.getUser(playerId)).thenReturn(user);
        when(user.getUniqueId()).thenReturn(playerId);
        when(user.getPrimaryGroup()).thenReturn("default");
        when(playerApi.find(playerId)).thenReturn(CompletableFuture.failedFuture(controllerFailure));
        List<String> loggedMessages = new ArrayList<>();
        List<Throwable> loggedCauses = new ArrayList<>();
        FailureLog failureLog = (message, cause) -> {
            loggedMessages.add(message);
            loggedCauses.add(cause);
        };
        LuckPermsPlayerPropertySynchronizer synchronizer = new LuckPermsPlayerPropertySynchronizer(
                cloudApi, luckPerms, this, ignored -> true, failureLog);
        synchronizer.start();

        synchronizer.synchronize(playerId);

        assertEquals(List.of("Failed to synchronize LuckPerms properties for " + playerId), loggedMessages);
        // The failure arrives wrapped by the future chain.
        assertSame(controllerFailure, loggedCauses.get(0).getCause());
    }
}
