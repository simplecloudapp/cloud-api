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

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

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
        when(playerApi.get(playerId)).thenReturn(CompletableFuture.completedFuture(player));
        when(playerApi.updatePlayerProperty(eq(playerId), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(Map.of()));
        LuckPermsPlayerPropertySynchronizer synchronizer = new LuckPermsPlayerPropertySynchronizer(
                cloudApi, luckPerms, this, ignored -> true, (message, error) -> {
                    throw new AssertionError(message, error);
                });
        synchronizer.start();
        ArgumentCaptor<Consumer<UserDataRecalculateEvent>> callback = ArgumentCaptor.forClass(Consumer.class);
        verify(eventBus).subscribe(eq(this), eq(UserDataRecalculateEvent.class), callback.capture());

        callback.getValue().accept(event);
        verifyNoInteractions(playerApi);

        synchronizer.synchronize(playerId);
        verify(playerApi).get(playerId);
        verify(playerApi).updatePlayerProperty(playerId, "luckperms-primary-group", "default");

        when(user.getPrimaryGroup()).thenReturn("admin");
        callback.getValue().accept(event);
        verify(playerApi, times(2)).get(playerId);
        verify(playerApi).updatePlayerProperty(playerId, "luckperms-primary-group", "admin");

        synchronizer.forget(playerId);
        callback.getValue().accept(event);
        verify(playerApi, times(2)).get(playerId);

        synchronizer.synchronize(playerId);
        verify(playerApi, times(3)).get(playerId);
        synchronizer.stop();
        synchronizer.start();
        callback.getValue().accept(event);
        verify(playerApi, times(3)).get(playerId);
    }
}
