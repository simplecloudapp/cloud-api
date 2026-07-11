package app.simplecloud.api.platform.shared;

import app.simplecloud.api.CloudApi;
import app.simplecloud.api.player.CloudPlayer;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.event.EventSubscription;
import net.luckperms.api.event.user.UserDataRecalculateEvent;
import net.luckperms.api.model.user.User;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

/**
 * Keeps a small, bounded subset of LuckPerms user data in SimpleCloud player properties.
 */
public final class LuckPermsPlayerPropertySynchronizer {

    public static final String PRIMARY_GROUP_PROPERTY = "luckperms-primary-group";

    private final CloudApi cloudApi;
    private final LuckPerms luckPerms;
    private final Object eventSubscriber;
    private final Predicate<UUID> isOnline;
    private final BiConsumer<String, Throwable> errorLogger;
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
            Predicate<UUID> isOnline,
            BiConsumer<String, Throwable> errorLogger
    ) {
        this.cloudApi = cloudApi;
        this.luckPerms = luckPerms;
        this.eventSubscriber = eventSubscriber;
        this.isOnline = isOnline;
        this.errorLogger = errorLogger;
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

    public void stop() {
        running = false;
        if (subscription != null) {
            subscription.close();
            subscription = null;
        }
        desiredPrimaryGroups.clear();
        synchronizedPrimaryGroups.clear();
        synchronizationsInProgress.clear();
        refreshRequested.clear();
    }

    public void synchronize(UUID uniqueId) {
        if (!running) {
            return;
        }
        User user = luckPerms.getUserManager().getUser(uniqueId);
        if (user != null) {
            synchronize(user);
        }
    }

    public void forget(UUID uniqueId) {
        desiredPrimaryGroups.remove(uniqueId);
        synchronizedPrimaryGroups.remove(uniqueId);
        refreshRequested.remove(uniqueId);
    }

    private void synchronize(User user) {
        UUID uniqueId = user.getUniqueId();
        if (!running || !isOnline.test(uniqueId)) {
            return;
        }

        String primaryGroup = user.getPrimaryGroup();
        desiredPrimaryGroups.put(uniqueId, primaryGroup);
        if (primaryGroup.equals(synchronizedPrimaryGroups.get(uniqueId))) {
            return;
        }
        synchronizeNext(uniqueId);
    }

    private void synchronizeNext(UUID uniqueId) {
        if (!synchronizationsInProgress.add(uniqueId)) {
            refreshRequested.add(uniqueId);
            return;
        }

        String primaryGroup = desiredPrimaryGroups.get(uniqueId);
        cloudApi.player().get(uniqueId)
                .thenCompose(player -> updateIfChanged(uniqueId, player, primaryGroup))
                .whenComplete((synchronizedValue, throwable) -> {
                    if (throwable != null) {
                        errorLogger.accept("Failed to synchronize LuckPerms properties for " + uniqueId, throwable);
                    } else if (Boolean.TRUE.equals(synchronizedValue)) {
                        synchronizedPrimaryGroups.put(uniqueId, primaryGroup);
                    }
                    synchronizationsInProgress.remove(uniqueId);
                    boolean shouldRefresh = refreshRequested.remove(uniqueId);

                    if (!running || !isOnline.test(uniqueId)) {
                        forget(uniqueId);
                    } else if (shouldRefresh || (Boolean.TRUE.equals(synchronizedValue) &&
                            !Objects.equals(
                                    desiredPrimaryGroups.get(uniqueId),
                                    synchronizedPrimaryGroups.get(uniqueId)
                            ))) {
                        synchronizeNext(uniqueId);
                    }
                });
    }

    private CompletableFuture<Boolean> updateIfChanged(UUID uniqueId, CloudPlayer player, String primaryGroup) {
        if (player == null) {
            return CompletableFuture.completedFuture(false);
        }
        if (primaryGroup.equals(player.getProperties().get(PRIMARY_GROUP_PROPERTY))) {
            return CompletableFuture.completedFuture(true);
        }
        return cloudApi.player().updatePlayerProperty(uniqueId, PRIMARY_GROUP_PROPERTY, primaryGroup)
                .thenApply(ignored -> true);
    }
}
