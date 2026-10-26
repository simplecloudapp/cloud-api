package app.simplecloud.api.platform.shared;

import app.simplecloud.api.CloudApi;
import app.simplecloud.api.internal.integration.player.ProxyPlayer;
import app.simplecloud.api.internal.integration.player.ProxyPlayers;
import app.simplecloud.api.player.CloudPlayer;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.event.EventSubscription;
import net.luckperms.api.event.user.UserDataRecalculateEvent;
import net.luckperms.api.model.user.User;

import java.util.Map;
import java.util.Objects;
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
    private final ProxyPlayers proxyPlayers;
    private final FailureLog failureLog;
    private final Set<UUID> registeredPlayers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, String> desiredPrimaryGroups = new ConcurrentHashMap<>();
    private final Map<UUID, String> synchronizedPrimaryGroups = new ConcurrentHashMap<>();
    private final Set<UUID> synchronizationsInProgress = ConcurrentHashMap.newKeySet();
    private final Set<UUID> refreshRequested = ConcurrentHashMap.newKeySet();

    private EventSubscription<UserDataRecalculateEvent> subscription;
    private volatile boolean running;

    public LuckPermsPlayerPropertySynchronizer(
            CloudApi cloudApi,
            LuckPerms luckPerms,
            Object eventSubscriber,
            ProxyPlayers proxyPlayers,
            FailureLog failureLog
    ) {
        this.cloudApi = cloudApi;
        this.luckPerms = luckPerms;
        this.eventSubscriber = eventSubscriber;
        this.proxyPlayers = proxyPlayers;
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
        synchronizedPrimaryGroups.clear();
        synchronizationsInProgress.clear();
        refreshRequested.clear();
    }

    @Override
    public void synchronize(UUID playerId) {
        if (!running || !isOnThisProxy(playerId)) {
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
        synchronizedPrimaryGroups.remove(playerId);
        refreshRequested.remove(playerId);
    }

    private void synchronize(User user) {
        UUID playerId = user.getUniqueId();
        if (!running || !registeredPlayers.contains(playerId) || !isOnThisProxy(playerId)) {
            return;
        }

        String primaryGroup = user.getPrimaryGroup();
        desiredPrimaryGroups.put(playerId, primaryGroup);
        if (primaryGroup.equals(synchronizedPrimaryGroups.get(playerId))) {
            return;
        }
        synchronizeNext(playerId);
    }

    private void synchronizeNext(UUID playerId) {
        if (!synchronizationsInProgress.add(playerId)) {
            refreshRequested.add(playerId);
            return;
        }

        String primaryGroup = desiredPrimaryGroups.get(playerId);
        cloudApi.player().find(playerId)
                .thenCompose(player -> player
                        .map(found -> updateIfChanged(playerId, found, primaryGroup))
                        .orElseGet(() -> CompletableFuture.completedFuture(false)))
                .whenComplete((synchronizedValue, throwable) -> {
                    if (throwable != null) {
                        failureLog.logFailure("Failed to synchronize LuckPerms properties for " + playerId, throwable);
                    } else if (Boolean.TRUE.equals(synchronizedValue)) {
                        synchronizedPrimaryGroups.put(playerId, primaryGroup);
                    }
                    synchronizationsInProgress.remove(playerId);
                    boolean shouldRefresh = refreshRequested.remove(playerId);

                    if (!running || !registeredPlayers.contains(playerId) || !isOnThisProxy(playerId)) {
                        forget(playerId);
                    } else if (shouldRefresh || (Boolean.TRUE.equals(synchronizedValue) &&
                            !Objects.equals(
                                    desiredPrimaryGroups.get(playerId),
                                    synchronizedPrimaryGroups.get(playerId)
                            ))) {
                        synchronizeNext(playerId);
                    }
                });
    }

    private CompletableFuture<Boolean> updateIfChanged(UUID playerId, CloudPlayer player, String primaryGroup) {
        if (primaryGroup.equals(player.getProperties().get(PRIMARY_GROUP_PROPERTY))) {
            return CompletableFuture.completedFuture(true);
        }
        return cloudApi.player().updatePlayerProperty(playerId, PRIMARY_GROUP_PROPERTY, primaryGroup)
                .thenApply(ignored -> true);
    }

    private boolean isOnThisProxy(UUID playerId) {
        Optional<ProxyPlayer> player = proxyPlayers.findPlayer(playerId);
        return player.isPresent();
    }
}
