package app.simplecloud.api.platform.shared.luckperms;

import app.simplecloud.api.CloudApi;
import app.simplecloud.api.platform.shared.FailureLog;
import app.simplecloud.api.platform.shared.player.OnlinePlayers;
import app.simplecloud.api.player.CloudPlayer;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.event.EventSubscription;
import net.luckperms.api.event.user.UserDataRecalculateEvent;
import net.luckperms.api.model.user.User;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores each player's LuckPerms primary group in their {@value #PRIMARY_GROUP_PROPERTY} cloud property.
 */
public final class LuckPermsPrimaryGroupSynchronizer implements LuckPermsSynchronizer {

    public static final String PRIMARY_GROUP_PROPERTY = "luckperms-primary-group";

    private final CloudApi cloudApi;
    private final LuckPerms luckPerms;
    private final Object eventSubscriber;
    private final OnlinePlayers onlinePlayers;
    private final FailureLog failureLog;
    private final Set<UUID> registeredPlayers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, String> desiredPrimaryGroups = new ConcurrentHashMap<>();
    private final Map<UUID, String> storedPrimaryGroups = new ConcurrentHashMap<>();
    private final Set<UUID> storesInFlight = ConcurrentHashMap.newKeySet();
    private final Set<UUID> storeAgainRequested = ConcurrentHashMap.newKeySet();

    private EventSubscription<UserDataRecalculateEvent> subscription;
    private volatile boolean running;

    public LuckPermsPrimaryGroupSynchronizer(
            CloudApi cloudApi,
            LuckPerms luckPerms,
            Object eventSubscriber,
            OnlinePlayers onlinePlayers,
            FailureLog failureLog) {
        this.cloudApi = cloudApi;
        this.luckPerms = luckPerms;
        this.eventSubscriber = eventSubscriber;
        this.onlinePlayers = onlinePlayers;
        this.failureLog = failureLog;
    }

    public void start() {
        if (subscription != null) {
            return;
        }
        subscription = luckPerms
                .getEventBus()
                .subscribe(eventSubscriber, UserDataRecalculateEvent.class, event -> synchronize(event.getUser()));
        running = true;
    }

    @Override
    public void stop() {
        running = false;
        if (subscription != null) {
            subscription.close();
            subscription = null;
        }
        registeredPlayers.clear();
        desiredPrimaryGroups.clear();
        storedPrimaryGroups.clear();
        storesInFlight.clear();
        storeAgainRequested.clear();
    }

    @Override
    public void synchronize(UUID playerId) {
        if (!running || !onlinePlayers.isOnline(playerId)) {
            return;
        }
        registeredPlayers.add(playerId);
        User user = luckPerms.getUserManager().getUser(playerId);
        if (user != null) {
            synchronize(user);
        }
    }

    @Override
    public void forget(UUID playerId) {
        registeredPlayers.remove(playerId);
        desiredPrimaryGroups.remove(playerId);
        storedPrimaryGroups.remove(playerId);
        storeAgainRequested.remove(playerId);
    }

    private void synchronize(User user) {
        UUID playerId = user.getUniqueId();
        if (shouldSynchronize(playerId)) {
            return;
        }

        String primaryGroup = user.getPrimaryGroup();
        desiredPrimaryGroups.put(playerId, primaryGroup);
        String storedPrimaryGroup = storedPrimaryGroups.get(playerId);
        if (primaryGroup.equals(storedPrimaryGroup)) {
            return;
        }
        storeDesiredPrimaryGroup(playerId);
    }

    private void storeDesiredPrimaryGroup(UUID playerId) {
        boolean storeStarted = storesInFlight.add(playerId);
        if (!storeStarted) {
            storeAgainRequested.add(playerId);
            return;
        }

        String primaryGroup = desiredPrimaryGroups.get(playerId);
        CompletableFuture<Boolean> store = storePrimaryGroup(playerId, primaryGroup);
        store.whenComplete((stored, failure) -> finishStore(playerId, primaryGroup, stored, failure));
    }

    private CompletableFuture<Boolean> storePrimaryGroup(UUID playerId, String primaryGroup) {
        CompletableFuture<Optional<CloudPlayer>> lookup = cloudApi.player().find(playerId);
        return lookup.thenCompose(cloudPlayer -> {
            if (cloudPlayer.isEmpty()) {
                return CompletableFuture.completedFuture(false);
            }
            return storePrimaryGroupIfChanged(playerId, cloudPlayer.get(), primaryGroup);
        });
    }

    private CompletableFuture<Boolean> storePrimaryGroupIfChanged(
            UUID playerId, CloudPlayer cloudPlayer, String primaryGroup) {
        String propertyValue = cloudPlayer.getProperties().get(PRIMARY_GROUP_PROPERTY);
        if (primaryGroup.equals(propertyValue)) {
            return CompletableFuture.completedFuture(true);
        }
        CompletableFuture<Map<String, String>> update =
                cloudApi.player().updatePlayerProperty(playerId, PRIMARY_GROUP_PROPERTY, primaryGroup);
        return update.thenApply(updatedProperties -> true);
    }

    private void finishStore(UUID playerId, String primaryGroup, Boolean stored, Throwable failure) {
        boolean storedSuccessfully = failure == null && stored;
        if (failure != null) {
            failureLog.logFailure("Failed to synchronize the LuckPerms primary group of " + playerId, failure);
        }
        if (storedSuccessfully) {
            storedPrimaryGroups.put(playerId, primaryGroup);
        }

        storesInFlight.remove(playerId);
        boolean storeAgain = storeAgainRequested.remove(playerId);

        if (shouldSynchronize(playerId)) {
            forget(playerId);
            return;
        }

        String desiredPrimaryGroup = desiredPrimaryGroups.get(playerId);
        boolean desiredGroupChanged = storedSuccessfully && !primaryGroup.equals(desiredPrimaryGroup);
        if (storeAgain || desiredGroupChanged) {
            storeDesiredPrimaryGroup(playerId);
        }
    }

    private boolean shouldSynchronize(UUID playerId) {
        return !running || !registeredPlayers.contains(playerId) || !onlinePlayers.isOnline(playerId);
    }
}
