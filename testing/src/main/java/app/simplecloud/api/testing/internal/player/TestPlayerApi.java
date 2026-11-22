package app.simplecloud.api.testing.internal.player;

import app.simplecloud.api.player.CloudPlayer;
import app.simplecloud.api.player.PlayerApi;
import app.simplecloud.api.player.PlayerClientSettings;
import app.simplecloud.api.player.PlayerProfile;
import app.simplecloud.api.player.PlayerQuery;
import app.simplecloud.api.player.PlayerSession;
import app.simplecloud.api.player.ServerSelectionMode;
import app.simplecloud.api.player.UpdateClientSettingsRequest;
import app.simplecloud.api.testing.internal.CloudState;
import app.simplecloud.api.testing.internal.Futures;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.Nullable;

public final class TestPlayerApi implements PlayerApi {

    private static final int MAX_PAGE_SIZE = 100;

    private final CloudState cloud;
    private final @Nullable Supplier<PlayerApi> facade;

    public TestPlayerApi(CloudState cloud) {
        this(cloud, null);
    }

    public TestPlayerApi(CloudState cloud, @Nullable Supplier<PlayerApi> facade) {
        this.cloud = cloud;
        this.facade = facade;
    }

    private PlayerApi facade() {
        return facade == null ? this : facade.get();
    }

    @Override
    public CompletableFuture<CloudPlayer> get(UUID uniqueId) {
        return Futures.call(() -> cloud.read(() -> snapshot(cloud.requirePlayer(uniqueId))));
    }

    @Override
    public CompletableFuture<CloudPlayer> get(String name) {
        return Futures.call(() -> cloud.read(() -> cloud.playerByName(name))
                .map(this::snapshot)
                .orElseThrow(() -> CloudState.notFound("Player '" + name + "' not found")));
    }

    @Override
    public CompletableFuture<Optional<CloudPlayer>> find(UUID uniqueId) {
        return Futures.call(() -> cloud.read(() -> cloud.playerById(uniqueId)).map(this::snapshot));
    }

    @Override
    public CompletableFuture<Optional<CloudPlayer>> find(String name) {
        return Futures.call(() -> cloud.read(() -> cloud.playerByName(name)).map(this::snapshot));
    }

    @Override
    public CompletableFuture<List<CloudPlayer>> getOnlinePlayers() {
        return getOnlinePlayers(null);
    }

    @Override
    public CompletableFuture<List<CloudPlayer>> getOnlinePlayers(@Nullable String serverName) {
        PlayerQuery query = PlayerQuery.create().filterByOnline(true);
        if (serverName != null) {
            query.filterByServer(serverName);
        }
        return list(query);
    }

    @Override
    public CompletableFuture<List<CloudPlayer>> list(@Nullable PlayerQuery query) {
        return Futures.call(() -> cloud.read(
                () -> cloud.players(query).stream().map(this::snapshot).toList()));
    }

    @Override
    public CompletableFuture<Integer> getOnlinePlayerCount() {
        return Futures.call(() -> cloud.read(
                () -> cloud.players(PlayerQuery.create().filterByOnline(true)).size()));
    }

    @Override
    public CompletableFuture<Long> getOnlineTimeSeconds(UUID uniqueId) {
        return Futures.call(() -> cloud.read(() -> cloud.requirePlayer(uniqueId).onlineTimeSeconds(cloud.now())));
    }

    @Override
    public CompletableFuture<CloudPlayer> setOnlineTimeSeconds(UUID uniqueId, long seconds) {
        return Futures.call(() -> {
            if (seconds < 0 || seconds > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("seconds must be between 0 and " + Integer.MAX_VALUE);
            }
            return snapshot(cloud.setOnlineTime(uniqueId, seconds));
        });
    }

    @Override
    public CompletableFuture<CloudPlayer> resetFirstSeen(UUID uniqueId) {
        return Futures.call(() -> snapshot(cloud.modifyPlayer(uniqueId, player -> player.firstSeen = cloud.now())));
    }

    @Override
    public CompletableFuture<CloudPlayer> resetLastSeen(UUID uniqueId) {
        return Futures.call(() -> snapshot(cloud.modifyPlayer(uniqueId, player -> player.lastSeen = cloud.now())));
    }

    @Override
    public CompletableFuture<Map<String, String>> getPlayerProperties(UUID uniqueId) {
        return Futures.call(() -> cloud.read(() -> Map.copyOf(cloud.requirePlayer(uniqueId).properties)));
    }

    @Override
    public CompletableFuture<Map<String, String>> setPlayerProperties(UUID uniqueId, Map<String, String> properties) {
        return Futures.call(() -> cloud.setPlayerProperties(uniqueId, properties));
    }

    @Override
    public CompletableFuture<Map<String, String>> updatePlayerProperties(
            UUID uniqueId, Map<String, String> properties) {
        return Futures.call(() -> cloud.updatePlayerProperties(uniqueId, properties));
    }

    @Override
    public CompletableFuture<Map<String, String>> deletePlayerProperties(UUID uniqueId, List<String> keys) {
        return Futures.call(() -> cloud.deletePlayerProperties(uniqueId, keys));
    }

    @Override
    public CompletableFuture<List<PlayerProfile>> getProfiles(UUID uniqueId, int limit, int offset) {
        return Futures.call(() -> {
            validatePagination(limit, offset);
            return cloud.read(() -> page(cloud.requirePlayer(uniqueId).profiles, limit, offset).stream()
                    .map(profile -> profile.toProfile(uniqueId))
                    .toList());
        });
    }

    @Override
    public CompletableFuture<PlayerProfile> getLatestProfile(UUID uniqueId) {
        return findLatestProfile(uniqueId)
                .thenApply(profile -> profile.orElseThrow(
                        () -> CloudState.notFound("No profile recorded for player '" + uniqueId + "'")));
    }

    @Override
    public CompletableFuture<Optional<PlayerProfile>> findLatestProfile(UUID uniqueId) {
        return Futures.call(() -> cloud.read(() ->
                cloud.playerById(uniqueId).map(PlayerData::latestProfile).map(profile -> profile.toProfile(uniqueId))));
    }

    @Override
    public CompletableFuture<List<PlayerSession>> getSessions(UUID uniqueId, int limit, int offset) {
        return Futures.call(() -> {
            validatePagination(limit, offset);
            return cloud.read(() -> page(cloud.requirePlayer(uniqueId).sessions, limit, offset).stream()
                    .map(session -> session.toSession(cloud.networkId(), uniqueId, null))
                    .toList());
        });
    }

    @Override
    public CompletableFuture<PlayerSession> getCurrentSession(UUID uniqueId) {
        return findCurrentSession(uniqueId)
                .thenApply(session -> session.orElseThrow(
                        () -> CloudState.notFound("Player '" + uniqueId + "' has no active session")));
    }

    @Override
    public CompletableFuture<Optional<PlayerSession>> findCurrentSession(UUID uniqueId) {
        return Futures.call(() -> cloud.read(() -> cloud.playerById(uniqueId).flatMap(player -> {
            SessionData session = player.currentSession();
            return Optional.ofNullable(session)
                    .map(current -> current.toSession(cloud.networkId(), uniqueId, player.clientSettings()));
        })));
    }

    @Override
    public CompletableFuture<PlayerClientSettings> getClientSettings(UUID uniqueId) {
        return findClientSettings(uniqueId)
                .thenApply(settings -> settings.orElseThrow(
                        () -> CloudState.notFound("No client settings for player '" + uniqueId + "'")));
    }

    @Override
    public CompletableFuture<Optional<PlayerClientSettings>> findClientSettings(UUID uniqueId) {
        return Futures.call(() -> cloud.read(() -> cloud.playerById(uniqueId).map(PlayerData::clientSettings)));
    }

    @Override
    public CompletableFuture<PlayerClientSettings> updateClientSettings(
            UUID uniqueId, UpdateClientSettingsRequest request) {
        return Futures.call(() -> {
            Objects.requireNonNull(request, "request");
            return cloud.updateClientSettings(uniqueId, request);
        });
    }

    @Override
    public CompletableFuture<Boolean> kick(UUID uniqueId, @Nullable Component reason) {
        return Futures.call(() -> cloud.kick(uniqueId, reason));
    }

    @Override
    public CompletableFuture<CloudPlayer.ConnectResult> connect(UUID uniqueId, String serverName) {
        return connectDirect(uniqueId, serverName);
    }

    @Override
    public CompletableFuture<CloudPlayer.ConnectResult> connectDirect(UUID uniqueId, String serverName) {
        return Futures.call(() -> cloud.connect(uniqueId, serverName));
    }

    @Override
    public CompletableFuture<CloudPlayer.ConnectResult> connectToGroup(
            UUID uniqueId, String groupName, ServerSelectionMode selectionMode) {
        return Futures.call(() -> cloud.connectToGroup(
                uniqueId, groupName, selectionMode == null ? ServerSelectionMode.RANDOM : selectionMode));
    }

    @Override
    public CompletableFuture<CloudPlayer.ConnectResult> connectToPersistentServer(
            UUID uniqueId, String persistentServerId) {
        return Futures.call(() -> cloud.connectToPersistentServer(uniqueId, persistentServerId));
    }

    private CloudPlayer snapshot(PlayerData player) {
        return cloud.snapshot(player, facade());
    }

    private static <T> List<T> page(List<T> oldestFirst, int limit, int offset) {
        List<T> newestFirst = oldestFirst.reversed();
        if (offset >= newestFirst.size()) {
            return List.of();
        }
        return List.copyOf(newestFirst.subList(offset, Math.min(newestFirst.size(), offset + limit)));
    }

    private static void validatePagination(int limit, int offset) {
        if (limit < 1 || limit > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_PAGE_SIZE);
        }
        if (offset < 0) {
            throw new IllegalArgumentException("offset must be >= 0");
        }
    }
}
