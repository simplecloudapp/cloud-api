package app.simplecloud.api.platform.shared;

import static app.simplecloud.api.platform.shared.luckperms.LuckPermsPrimaryGroupSynchronizer.PRIMARY_GROUP_PROPERTY;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import app.simplecloud.api.CloudApi;
import app.simplecloud.api.platform.shared.luckperms.LuckPermsPrimaryGroupSynchronizer;
import app.simplecloud.api.player.CloudPlayer;
import app.simplecloud.api.player.PlayerApi;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.event.EventBus;
import net.luckperms.api.event.user.UserDataRecalculateEvent;
import net.luckperms.api.model.user.User;
import net.luckperms.api.model.user.UserManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class LuckPermsPrimaryGroupSynchronizerTest {

    private final UUID playerId = UUID.randomUUID();
    private final PlayerApi playerApi = mock(PlayerApi.class);
    private final LuckPerms luckPerms = mock(LuckPerms.class);
    private final EventBus eventBus = mock(EventBus.class);
    private final User user = mock(User.class);
    private final CloudPlayer cloudPlayer = mock(CloudPlayer.class);
    private final UserDataRecalculateEvent recalculateEvent = mock(UserDataRecalculateEvent.class);
    private boolean online = true;
    private LuckPermsPrimaryGroupSynchronizer synchronizer;
    private Consumer<UserDataRecalculateEvent> recalculation;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        CloudApi cloudApi = mock(CloudApi.class);
        UserManager userManager = mock(UserManager.class);
        when(cloudApi.player()).thenReturn(playerApi);
        when(luckPerms.getEventBus()).thenReturn(eventBus);
        when(luckPerms.getUserManager()).thenReturn(userManager);
        when(userManager.getUser(playerId)).thenReturn(user);
        when(user.getUniqueId()).thenReturn(playerId);
        when(user.getPrimaryGroup()).thenReturn("default");
        when(recalculateEvent.getUser()).thenReturn(user);
        when(cloudPlayer.getProperties()).thenReturn(Map.of());
        when(playerApi.find(playerId)).thenReturn(CompletableFuture.completedFuture(Optional.of(cloudPlayer)));
        when(playerApi.updatePlayerProperty(eq(playerId), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(Map.of()));

        synchronizer = new LuckPermsPrimaryGroupSynchronizer(cloudApi, luckPerms, this, ignored -> online);
        synchronizer.start();
        ArgumentCaptor<Consumer<UserDataRecalculateEvent>> subscriber = ArgumentCaptor.forClass(Consumer.class);
        verify(eventBus).subscribe(eq(this), eq(UserDataRecalculateEvent.class), subscriber.capture());
        recalculation = subscriber.getValue();
    }

    @Test
    void registeredPlayerHasThePrimaryGroupStored() {
        synchronizer.synchronize(playerId);

        verify(playerApi).updatePlayerProperty(playerId, PRIMARY_GROUP_PROPERTY, "default");
    }

    @Test
    void recalculationBeforeTheRegistrationIsIgnored() {
        recalculation.accept(recalculateEvent);

        verifyNoInteractions(playerApi);
    }

    @Test
    void changedGroupIsStored() {
        synchronizer.synchronize(playerId);

        changeGroupTo("admin");

        verify(playerApi).updatePlayerProperty(playerId, PRIMARY_GROUP_PROPERTY, "admin");
    }

    @Test
    void unchangedGroupIsNotStoredAgain() {
        synchronizer.synchronize(playerId);

        recalculation.accept(recalculateEvent);

        verify(playerApi).find(playerId);
    }

    @Test
    void groupAlreadyInTheCloudPropertiesIsNotWrittenAgain() {
        when(cloudPlayer.getProperties()).thenReturn(Map.of(PRIMARY_GROUP_PROPERTY, "default"));

        synchronizer.synchronize(playerId);

        verify(playerApi, never()).updatePlayerProperty(any(), anyString(), anyString());
    }

    @Test
    void playerUnknownToTheCloudIsNotWritten() {
        when(playerApi.find(playerId)).thenReturn(CompletableFuture.completedFuture(Optional.empty()));

        synchronizer.synchronize(playerId);

        verify(playerApi, never()).updatePlayerProperty(any(), anyString(), anyString());
    }

    @Test
    void playerNotOnThisServerIsNotSynchronized() {
        online = false;

        synchronizer.synchronize(playerId);

        verifyNoInteractions(playerApi);
    }

    @Test
    void forgottenPlayerIsNoLongerSynchronized() {
        synchronizer.synchronize(playerId);
        synchronizer.forget(playerId);

        changeGroupTo("admin");

        verify(playerApi).find(playerId);
    }

    @Test
    void stopForgetsAllPlayers() {
        synchronizer.synchronize(playerId);
        synchronizer.stop();
        synchronizer.start();

        changeGroupTo("admin");

        verify(playerApi).find(playerId);
    }

    @Test
    void failedStoreIsRetriedOnTheNextRecalculation() {
        when(playerApi.find(playerId))
                .thenReturn(
                        CompletableFuture.failedFuture(new IllegalStateException("controller unavailable")),
                        CompletableFuture.completedFuture(Optional.of(cloudPlayer)));
        synchronizer.synchronize(playerId);

        recalculation.accept(recalculateEvent);

        verify(playerApi).updatePlayerProperty(playerId, PRIMARY_GROUP_PROPERTY, "default");
    }

    @Test
    void groupChangeDuringAFailedStoreIsStoredAfterIt() {
        CompletableFuture<Map<String, String>> failingStore = new CompletableFuture<>();
        when(playerApi.updatePlayerProperty(playerId, PRIMARY_GROUP_PROPERTY, "default"))
                .thenReturn(failingStore);
        synchronizer.synchronize(playerId);

        changeGroupTo("admin");
        verify(playerApi, never()).updatePlayerProperty(playerId, PRIMARY_GROUP_PROPERTY, "admin");
        failingStore.completeExceptionally(new IllegalStateException("controller unavailable"));

        verify(playerApi).updatePlayerProperty(playerId, PRIMARY_GROUP_PROPERTY, "admin");
    }

    @Test
    void groupChangedBackDuringAStoreIsStoredAgainAfterIt() {
        when(user.getPrimaryGroup()).thenReturn("admin");
        CompletableFuture<Map<String, String>> slowStore = new CompletableFuture<>();
        when(playerApi.updatePlayerProperty(playerId, PRIMARY_GROUP_PROPERTY, "default"))
                .thenReturn(slowStore);
        synchronizer.synchronize(playerId);

        changeGroupTo("default");
        changeGroupTo("admin");
        slowStore.complete(Map.of());

        verify(playerApi, times(2)).updatePlayerProperty(playerId, PRIMARY_GROUP_PROPERTY, "admin");
    }

    private void changeGroupTo(String group) {
        when(user.getPrimaryGroup()).thenReturn(group);
        recalculation.accept(recalculateEvent);
    }
}
