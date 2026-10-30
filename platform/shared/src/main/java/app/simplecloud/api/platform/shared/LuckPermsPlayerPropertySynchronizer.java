package app.simplecloud.api.platform.shared;

import app.simplecloud.api.CloudApi;
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
 * Keeps a small, bounded subset of LuckPerms user data in SimpleCloud player properties.
 */
public final class LuckPermsPlayerPropertySynchronizer implements PlayerPropertySynchronizer {

    public static final String PRIMARY_GROUP_PROPERTY = "luckperms-primary-group";

    private final CloudApi cloudApi;
    private final LuckPerms luckPerms;
    private final Object eventSubscriber;
    private final OnlinePlayers onlinePlayers;
    private final FailureLog failureLog;
    // Players whose login the controller accepted; only their primary groups are stored.
    private final Set<UUID> registeredPlayers = ConcurrentHashMap.newKeySet();
    // The primary group LuckPerms last reported for each player.
    private final Map<UUID, String> desiredPrimaryGroups = new ConcurrentHashMap<>();
    // The primary group last stored in each player's cloud properties.
    private final Map<UUID, String> storedPrimaryGroups = new ConcurrentHashMap<>();
    // Players with a store in flight. Stores for one player never overlap.
    private final Set<UUID> storesInFlight = ConcurrentHashMap.newKeySet();
    // Players whose group changed while a store was in flight, so another store follows it.
    private final Set<UUID> storeAgainRequested = ConcurrentHashMap.newKeySet();

    private EventSubscription<UserDataRecalculateEvent> subscription;
    private volatile boolean running;

    public LuckPermsPlayerPropertySynchronizer(
            CloudApi cloudApi,
            LuckPerms luckPerms,
            Object eventSubscriber,
            OnlinePlayers onlinePlayers,
            FailureLog failureLog
    ) {
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
        subscription = luckPerms.getEventBus().subscribe(
                eventSubscriber,
                UserDataRecalculateEvent.class,
                event -> synchronize(event.getUser())
        );
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
        if (!shouldSynchronize(playerId)) {
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

    /**
     * Stores the player's desired primary group in their cloud properties. If a store for the player is
     * already in flight, it only asks for another one to follow it.
     */
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

    /**
     * Stores the primary group in the player's cloud properties unless it is already there.
     * Completes with {@code false} if the cloud doesn't know the player.
     */
    private CompletableFuture<Boolean> storePrimaryGroup(UUID playerId, String primaryGroup) {
        CompletableFuture<Optional<CloudPlayer>> lookup = cloudApi.player().find(playerId);
        return lookup.thenCompose(cloudPlayer -> {
            if (cloudPlayer.isEmpty()) {
                return CompletableFuture.completedFuture(false);
            }
            return storePrimaryGroupIfChanged(playerId, cloudPlayer.get(), primaryGroup);
        });
    }

    private CompletableFuture<Boolean> storePrimaryGroupIfChanged(UUID playerId, CloudPlayer cloudPlayer, String primaryGroup) {
        String propertyValue = cloudPlayer.getProperties().get(PRIMARY_GROUP_PROPERTY);
        if (primaryGroup.equals(propertyValue)) {
            return CompletableFuture.completedFuture(true);
        }
        CompletableFuture<Map<String, String>> update =
                cloudApi.player().updatePlayerProperty(playerId, PRIMARY_GROUP_PROPERTY, primaryGroup);
        return update.thenApply(updatedProperties -> true);
    }

    /**
     * Records the result of a store, then forgets the player, stores again or stops.
     *
     * @param stored whether the group is now stored; {@code null} if the store failed
     */
    private void finishStore(UUID playerId, String primaryGroup, Boolean stored, Throwable failure) {
        boolean storedSuccessfully = failure == null && stored;
        if (failure != null) {
            failureLog.logFailure("Failed to synchronize LuckPerms properties for " + playerId, failure);
        }
        if (storedSuccessfully) {
            storedPrimaryGroups.put(playerId, primaryGroup);
        }

        // Clear the in-flight mark before taking the request for another store. A group change that
        // arrives in between then starts its own store instead of leaving a request nobody takes.
        storesInFlight.remove(playerId);
        boolean storeAgain = storeAgainRequested.remove(playerId);

        if (!shouldSynchronize(playerId)) {
            forget(playerId);
            return;
        }
        // Store again if a change arrived during this store, or if the group changed back to the one stored
        // before: synchronize(User) skips such a change because it matches the old stored group.
        String desiredPrimaryGroup = desiredPrimaryGroups.get(playerId);
        boolean desiredGroupChanged = storedSuccessfully && !primaryGroup.equals(desiredPrimaryGroup);
        if (storeAgain || desiredGroupChanged) {
            storeDesiredPrimaryGroup(playerId);
        }
    }

    /**
     * Whether the player's primary group is still synchronized: the synchronizer runs, the controller
     * accepted the player's login, and the player is still on this server.
     */
    private boolean shouldSynchronize(UUID playerId) {
        return running && registeredPlayers.contains(playerId) && onlinePlayers.isOnline(playerId);
    }
}
