package app.simplecloud.api.internal.player;

import app.simplecloud.api.CloudApiException;
import app.simplecloud.api.CloudApiOptions;
import app.simplecloud.api.internal.util.ValidationUtil;
import app.simplecloud.api.internal.web.ApiCalls;
import app.simplecloud.api.internal.web.ApiExceptions;
import app.simplecloud.api.player.CloudPlayer;
import app.simplecloud.api.player.ConnectResponse;
import app.simplecloud.api.player.ConnectTarget;
import app.simplecloud.api.player.PlayerApi;
import app.simplecloud.api.player.PlayerClientSettings;
import app.simplecloud.api.player.PlayerProfile;
import app.simplecloud.api.player.PlayerQuery;
import app.simplecloud.api.player.PlayerSession;
import app.simplecloud.api.player.UpdateClientSettingsRequest;
import app.simplecloud.api.web.ApiClient;
import app.simplecloud.api.web.ApiException;
import app.simplecloud.api.web.apis.PlayersApi;
import app.simplecloud.api.web.models.ModelsConnectPlayerRequest;
import app.simplecloud.api.web.models.ModelsConnectPlayerResponse;
import app.simplecloud.api.web.models.ModelsCurrentSessionResponse;
import app.simplecloud.api.web.models.ModelsDeletePlayerPropertiesRequest;
import app.simplecloud.api.web.models.ModelsKickPlayerRequest;
import app.simplecloud.api.web.models.ModelsKickPlayerResponse;
import app.simplecloud.api.web.models.ModelsListPlayerProfilesResponse;
import app.simplecloud.api.web.models.ModelsListPlayerSessionsResponse;
import app.simplecloud.api.web.models.ModelsListPlayersResponse;
import app.simplecloud.api.web.models.ModelsOnlinePlayerCountResponse;
import app.simplecloud.api.web.models.ModelsOnlinePlayerResponse;
import app.simplecloud.api.web.models.ModelsOnlinePlayersResponse;
import app.simplecloud.api.web.models.ModelsPatchPlayerRequest;
import app.simplecloud.api.web.models.ModelsPlayerClientSettingsResponse;
import app.simplecloud.api.web.models.ModelsPlayerOnlineTimeResponse;
import app.simplecloud.api.web.models.ModelsPlayerProfileResponse;
import app.simplecloud.api.web.models.ModelsPlayerPropertiesResponse;
import app.simplecloud.api.web.models.ModelsPlayerResponse;
import app.simplecloud.api.web.models.ModelsPlayerSessionResponse;
import app.simplecloud.api.web.models.ModelsUpdateClientSettingsRequest;
import app.simplecloud.api.web.models.ModelsUpdateClientSettingsResponse;
import app.simplecloud.api.web.models.ModelsUpdatePlayerPropertiesRequest;
import app.simplecloud.api.web.models.ModelsUpdatePlayerPropertiesResponse;
import build.buf.gen.simplecloud.player.v2.ConnectPlayerRequest;
import build.buf.gen.simplecloud.player.v2.ConnectPlayerResponse;
import io.nats.client.Connection;
import io.nats.client.Message;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import java.util.Optional;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

import static app.simplecloud.api.internal.util.ProtoConversionUtil.emptyToNull;

public class PlayerApiImpl implements PlayerApi {

    private static final Logger LOGGER = Logger.getLogger(PlayerApiImpl.class.getName());
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final int MAX_PAGE_SIZE = 100;

    private final CloudApiOptions options;
    private final Connection natsConnection;
    private final PlayersApi playersApi;
    private final Executor executor;

    public PlayerApiImpl(CloudApiOptions options, Connection natsConnection, ApiClient httpClient, Executor executor) {
        this(options, natsConnection, new PlayersApi(httpClient), executor);
    }

    PlayerApiImpl(CloudApiOptions options, Connection natsConnection, PlayersApi playersApi, Executor executor) {
        this.options = options;
        this.natsConnection = natsConnection;
        this.playersApi = playersApi;
        this.executor = executor;
    }

    @Override
    public CompletableFuture<CloudPlayer> get(UUID uniqueId) {
        return find(uniqueId)
                .thenApply(player ->
                        player.orElseThrow(() -> ApiExceptions.notFound("Player '" + uniqueId + "' not found")));
    }

    @Override
    public CompletableFuture<CloudPlayer> get(String name) {
        return find(name)
                .thenApply(
                        player -> player.orElseThrow(() -> ApiExceptions.notFound("Player '" + name + "' not found")));
    }

    @Override
    public CompletableFuture<Optional<CloudPlayer>> find(UUID uniqueId) {
        return callOrEmptyIfNotFound(() -> {
            ModelsPlayerResponse response = playersApi.getPlayer(options.getNetworkId(), uniqueId.toString());
            return response != null ? convertPlayerResponse(response) : null;
        });
    }

    @Override
    public CompletableFuture<Optional<CloudPlayer>> find(String name) {
        if (!ValidationUtil.isPlayerName(name)) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return callOrEmptyIfNotFound(() -> {
            ModelsPlayerResponse response = playersApi.getPlayerByName(options.getNetworkId(), name);
            return response != null ? convertPlayerResponse(response) : null;
        });
    }

    @Override
    public CompletableFuture<List<CloudPlayer>> getOnlinePlayers() {
        return getOnlinePlayers(null);
    }

    @Override
    public CompletableFuture<List<CloudPlayer>> getOnlinePlayers(@Nullable String serverName) {
        return call(() -> {
            ModelsOnlinePlayersResponse response = playersApi.listOnlinePlayers(options.getNetworkId(), serverName);
            if (response.getPlayers() == null) {
                return List.of();
            }

            List<CloudPlayer> players = new ArrayList<>();
            for (ModelsOnlinePlayerResponse player : response.getPlayers()) {
                players.add(convertPlayer(player));
            }
            return players;
        });
    }

    @Override
    public CompletableFuture<List<CloudPlayer>> list(@Nullable PlayerQuery query) {
        PlayerQuery q = query != null ? query.copy() : PlayerQuery.create();
        List<String> playerIds = q.getPlayerIds() == null
                ? null
                : q.getPlayerIds().stream().map(UUID::toString).toList();

        return call(() -> {
            Integer onlineTimeMin = toControllerOnlineTimeSeconds(q.getOnlineTimeSecondsMin());
            Integer onlineTimeMax = toControllerOnlineTimeSeconds(q.getOnlineTimeSecondsMax());
            ModelsListPlayersResponse response = playersApi.listPlayers(
                    options.getNetworkId(),
                    q.getOnline(),
                    q.getServerName(),
                    q.getProxyName(),
                    playerIds,
                    toOffsetDateTime(q.getFirstSeenFrom()),
                    toOffsetDateTime(q.getFirstSeenTo()),
                    toOffsetDateTime(q.getLastSeenFrom()),
                    toOffsetDateTime(q.getLastSeenTo()),
                    onlineTimeMin,
                    onlineTimeMax,
                    q.getSearch(),
                    q.getSortBy() != null ? q.getSortBy().getValue() : null,
                    q.getSortOrder() != null ? q.getSortOrder().getValue() : null,
                    q.getLimit(),
                    q.getOffset());

            List<CloudPlayer> players = new ArrayList<>();
            if (response.getPlayers() != null) {
                for (ModelsPlayerResponse player : response.getPlayers()) {
                    players.add(convertPlayerResponse(player));
                }
            }
            return players;
        });
    }

    @Override
    public CompletableFuture<Integer> getOnlinePlayerCount() {
        return call(() -> {
            ModelsOnlinePlayerCountResponse response = playersApi.getOnlinePlayerCount(options.getNetworkId());
            return response.getCount() != null ? response.getCount() : 0;
        });
    }

    @Override
    public CompletableFuture<Long> getOnlineTimeSeconds(UUID uniqueId) {
        return call(() -> {
            ModelsPlayerOnlineTimeResponse response =
                    playersApi.getPlayerOnlineTime(options.getNetworkId(), uniqueId.toString());

            if (response == null || response.getOnlineTimeSeconds() == null) {
                return 0L;
            }
            return response.getOnlineTimeSeconds().longValue();
        });
    }

    @Override
    public CompletableFuture<CloudPlayer> setOnlineTimeSeconds(UUID uniqueId, long seconds) {
        return call(() -> {
            int controllerSeconds = toControllerOnlineTimeSeconds(seconds);
            ModelsPatchPlayerRequest request = new ModelsPatchPlayerRequest().onlineTimeSeconds(controllerSeconds);
            return patchPlayer(uniqueId, request);
        });
    }

    @Override
    public CompletableFuture<CloudPlayer> resetFirstSeen(UUID uniqueId) {
        ModelsPatchPlayerRequest request = new ModelsPatchPlayerRequest().resetFirstSeen(true);
        return call(() -> patchPlayer(uniqueId, request));
    }

    @Override
    public CompletableFuture<CloudPlayer> resetLastSeen(UUID uniqueId) {
        ModelsPatchPlayerRequest request = new ModelsPatchPlayerRequest().resetLastSeen(true);
        return call(() -> patchPlayer(uniqueId, request));
    }

    private CloudPlayer patchPlayer(UUID uniqueId, ModelsPatchPlayerRequest request) throws ApiException {
        ModelsPlayerResponse response = playersApi.patchPlayer(options.getNetworkId(), uniqueId.toString(), request);
        return convertPlayerResponse(response);
    }

    @Override
    public CompletableFuture<Map<String, String>> getPlayerProperties(UUID uniqueId) {
        return call(() -> fetchPlayerProperties(uniqueId));
    }

    @Override
    public CompletableFuture<Map<String, String>> setPlayerProperties(UUID uniqueId, Map<String, String> properties) {
        return call(() -> {
            ModelsUpdatePlayerPropertiesRequest request = new ModelsUpdatePlayerPropertiesRequest();
            request.setProperties(properties);

            ModelsUpdatePlayerPropertiesResponse response =
                    playersApi.updatePlayerProperties(options.getNetworkId(), uniqueId.toString(), request);
            return toPropertiesMap(response.getProperties());
        });
    }

    @Override
    public CompletableFuture<Map<String, String>> updatePlayerProperties(
            UUID uniqueId, Map<String, String> properties) {
        return call(() -> {
            ModelsUpdatePlayerPropertiesRequest request = new ModelsUpdatePlayerPropertiesRequest();
            request.setProperties(properties);

            ModelsUpdatePlayerPropertiesResponse response =
                    playersApi.patchPlayerProperties(options.getNetworkId(), uniqueId.toString(), request);
            return toPropertiesMap(response.getProperties());
        });
    }

    @Override
    public CompletableFuture<Map<String, String>> deletePlayerProperties(UUID uniqueId, List<String> keys) {
        return call(() -> {
            ModelsDeletePlayerPropertiesRequest request = new ModelsDeletePlayerPropertiesRequest();
            request.setKeys(keys);

            playersApi.deletePlayerProperties(options.getNetworkId(), uniqueId.toString(), request);
            return fetchPlayerProperties(uniqueId);
        });
    }

    @Override
    public CompletableFuture<List<PlayerProfile>> getProfiles(UUID uniqueId, int limit, int offset) {
        return call(() -> {
            validatePagination(limit, offset);
            ModelsListPlayerProfilesResponse response =
                    playersApi.listPlayerProfiles(options.getNetworkId(), uniqueId.toString(), limit, offset);

            List<PlayerProfile> profiles = new ArrayList<>();
            if (response.getProfiles() != null) {
                for (ModelsPlayerProfileResponse profile : response.getProfiles()) {
                    profiles.add(convertProfile(profile));
                }
            }
            return profiles;
        });
    }

    @Override
    public CompletableFuture<PlayerProfile> getLatestProfile(UUID uniqueId) {
        return findLatestProfile(uniqueId)
                .thenApply(profile -> profile.orElseThrow(
                        () -> ApiExceptions.notFound("No profile recorded for player '" + uniqueId + "'")));
    }

    @Override
    public CompletableFuture<Optional<PlayerProfile>> findLatestProfile(UUID uniqueId) {
        return callOrEmptyIfNotFound(() -> {
            ModelsPlayerProfileResponse response =
                    playersApi.getLatestPlayerProfile(options.getNetworkId(), uniqueId.toString());
            return response != null ? convertProfile(response) : null;
        });
    }

    @Override
    public CompletableFuture<List<PlayerSession>> getSessions(UUID uniqueId, int limit, int offset) {
        return call(() -> {
            validatePagination(limit, offset);
            ModelsListPlayerSessionsResponse response =
                    playersApi.listPlayerSessions(options.getNetworkId(), uniqueId.toString(), limit, offset);

            List<PlayerSession> sessions = new ArrayList<>();
            if (response.getSessions() != null) {
                for (ModelsPlayerSessionResponse session : response.getSessions()) {
                    sessions.add(convertSession(session, null));
                }
            }
            return sessions;
        });
    }

    @Override
    public CompletableFuture<PlayerSession> getCurrentSession(UUID uniqueId) {
        return findCurrentSession(uniqueId)
                .thenApply(session -> session.orElseThrow(
                        () -> ApiExceptions.notFound("Player '" + uniqueId + "' has no active session")));
    }

    @Override
    public CompletableFuture<Optional<PlayerSession>> findCurrentSession(UUID uniqueId) {
        return callOrEmptyIfNotFound(() -> {
            ModelsCurrentSessionResponse response =
                    playersApi.getCurrentSession(options.getNetworkId(), uniqueId.toString());
            if (response == null || response.getSession() == null) {
                return null;
            }
            return convertSession(response.getSession(), response.getSettings());
        });
    }

    @Override
    public CompletableFuture<PlayerClientSettings> getClientSettings(UUID uniqueId) {
        return findClientSettings(uniqueId)
                .thenApply(settings -> settings.orElseThrow(
                        () -> ApiExceptions.notFound("No client settings for player '" + uniqueId + "'")));
    }

    @Override
    public CompletableFuture<Optional<PlayerClientSettings>> findClientSettings(UUID uniqueId) {
        return callOrEmptyIfNotFound(() -> {
            ModelsPlayerClientSettingsResponse response =
                    playersApi.getClientSettings(options.getNetworkId(), uniqueId.toString());
            return response != null ? convertClientSettings(response) : null;
        });
    }

    @Override
    public CompletableFuture<PlayerClientSettings> updateClientSettings(
            UUID uniqueId, UpdateClientSettingsRequest request) {
        return call(() -> {
            if (request == null) {
                throw new IllegalArgumentException("request must not be null");
            }

            ModelsUpdateClientSettingsRequest body = new ModelsUpdateClientSettingsRequest();
            if (request.getChatMode() != null) {
                body.setChatMode(request.getChatMode().name());
            }
            body.setLocale(request.getLocale());
            if (request.getMainHand() != null) {
                body.setMainHand(request.getMainHand().name());
            }
            body.setViewDistance(request.getViewDistance());
            body.setChatColors(request.getChatColors());
            body.setClientListingAllowed(request.getClientListingAllowed());
            body.setSkinParts(request.getSkinParts());

            ModelsUpdateClientSettingsResponse response =
                    playersApi.updateClientSettings(options.getNetworkId(), uniqueId.toString(), body);
            if (response.getSettings() == null) {
                throw new CloudApiException(
                        CloudApiException.Reason.UNKNOWN,
                        "Controller did not return updated client settings: " + response.getMessage());
            }
            return convertClientSettings(response.getSettings());
        });
    }

    @Override
    public CompletableFuture<Boolean> kick(UUID uniqueId, @Nullable Component reason) {
        return call(() -> {
            ModelsKickPlayerRequest request = new ModelsKickPlayerRequest();
            if (reason != null) {
                request.setReason(GsonComponentSerializer.gson().serialize(reason));
            }

            try {
                ModelsKickPlayerResponse response =
                        playersApi.kickPlayer(options.getNetworkId(), uniqueId.toString(), request);
                return Boolean.TRUE.equals(response.getSuccess());
            } catch (ApiException e) {
                if (e.getCode() == 404) {
                    return false;
                }
                throw e;
            }
        });
    }

    @Override
    public CompletableFuture<ConnectResponse> connectTo(UUID uniqueId, ConnectTarget target) {
        ModelsConnectPlayerRequest request = new ModelsConnectPlayerRequest();
        switch (target) {
            case ConnectTarget.ServerName t -> request.setServerName(t.serverName());
            case ConnectTarget.ServerId t -> request.setServerId(t.serverId());
            case ConnectTarget.GroupName t -> {
                request.setServerGroupName(t.groupName());
                request.setSelectionMode(Objects.requireNonNull(t.selectionMode()).name());
            }
            case ConnectTarget.GroupId t -> {
                request.setServerGroupId(t.groupId());
                request.setSelectionMode(Objects.requireNonNull(t.selectionMode()).name());
            }
            case ConnectTarget.PersistentServer t -> request.setPersistentServerId(t.persistentServerId());
        }
        return connectViaController(uniqueId, request);
    }

    @Override
    public CompletableFuture<CloudPlayer.ConnectResult> connectDirect(UUID uniqueId, String serverName) {
        return CompletableFuture.supplyAsync(
                () -> {
                    try {
                        ConnectPlayerRequest request = ConnectPlayerRequest.newBuilder()
                                .setPlayerId(uniqueId.toString())
                                .setServerName(serverName)
                                .build();

                        String subject = options.getNetworkId() + ".player." + uniqueId + ".connect";
                        Message response = natsConnection.request(subject, request.toByteArray(), REQUEST_TIMEOUT);

                        if (response == null) {
                            return CloudPlayer.ConnectResult.PLAYER_NOT_FOUND;
                        }

                        ConnectPlayerResponse protoResponse = ConnectPlayerResponse.parseFrom(response.getData());
                        return switch (protoResponse.getResult()) {
                            case CONNECT_RESULT_SUCCESS -> CloudPlayer.ConnectResult.SUCCESS;
                            case CONNECT_RESULT_SERVER_NOT_FOUND -> CloudPlayer.ConnectResult.SERVER_NOT_FOUND;
                            case CONNECT_RESULT_ALREADY_CONNECTED -> CloudPlayer.ConnectResult.ALREADY_CONNECTED;
                            default -> CloudPlayer.ConnectResult.CONNECTION_FAILED;
                        };
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return CloudPlayer.ConnectResult.CONNECTION_FAILED;
                    } catch (Exception e) {
                        LOGGER.log(Level.FINE, "Failed to connect player " + uniqueId + " to " + serverName, e);
                        return CloudPlayer.ConnectResult.CONNECTION_FAILED;
                    }
                },
                executor);
    }

    private CompletableFuture<ConnectResponse> connectViaController(UUID uniqueId, ModelsConnectPlayerRequest request) {
        return CompletableFuture.supplyAsync(
                () -> {
                    try {
                        ModelsConnectPlayerResponse response =
                                playersApi.connectPlayer(options.getNetworkId(), uniqueId.toString(), request);
                        return new ConnectResponse(
                                toConnectResult(response), response != null ? response.getServerName() : null);
                    } catch (ApiException e) {
                        if (e.getCode() == 404) {
                            return new ConnectResponse(
                                    isPlayerNotOnline(e)
                                            ? CloudPlayer.ConnectResult.PLAYER_NOT_FOUND
                                            : CloudPlayer.ConnectResult.SERVER_NOT_FOUND,
                                    null);
                        }
                        throw ApiExceptions.from(e);
                    }
                },
                executor);
    }

    private static boolean isPlayerNotOnline(ApiException e) {
        String body = e.getResponseBody();
        return body != null && body.contains("Player is not online");
    }

    static CloudPlayer.ConnectResult toConnectResult(ModelsConnectPlayerResponse response) {
        if (response == null || response.getResult() == null) {
            return CloudPlayer.ConnectResult.CONNECTION_FAILED;
        }
        return switch (response.getResult()) {
            case ConnectResultSuccess -> CloudPlayer.ConnectResult.SUCCESS;
            case ConnectResultAlreadyConnected -> CloudPlayer.ConnectResult.ALREADY_CONNECTED;
            case ConnectResultNoServerAvailable -> CloudPlayer.ConnectResult.NO_SERVER_AVAILABLE;
            case ConnectResultServerNotFound -> CloudPlayer.ConnectResult.SERVER_NOT_FOUND;
            default -> CloudPlayer.ConnectResult.CONNECTION_FAILED;
        };
    }

    private CloudPlayer convertPlayer(ModelsOnlinePlayerResponse player) {
        assert player.getId() != null;
        return new CloudPlayerImpl(
                this,
                natsConnection,
                options.getNetworkId(),
                UUID.fromString(player.getId()),
                player.getName(),
                player.getDisplayName(),
                player.getConnectedProxyName(),
                player.getConnectedServerName(),
                player.getOnline() != null && player.getOnline(),
                toOnlineTimeSeconds(player.getOnlineTimeSeconds()),
                player.getSessionId(),
                player.getFirstSeen(),
                player.getLastSeen(),
                player.getProperties());
    }

    private CloudPlayer convertPlayerResponse(ModelsPlayerResponse player) {
        return new CloudPlayerImpl(
                this,
                natsConnection,
                options.getNetworkId(),
                UUID.fromString(Objects.requireNonNull(player.getId())),
                player.getName(),
                player.getDisplayName(),
                player.getConnectedProxyName(),
                player.getConnectedServerName(),
                player.getOnline() != null && player.getOnline(),
                toOnlineTimeSeconds(player.getOnlineTimeSeconds()),
                player.getSessionId(),
                player.getFirstSeen(),
                player.getLastSeen(),
                player.getProperties());
    }

    private PlayerProfile convertProfile(ModelsPlayerProfileResponse response) {
        return new PlayerProfile(
                response.getId(),
                UUID.fromString(Objects.requireNonNull(response.getPlayerId())),
                response.getName(),
                emptyToNull(response.getTexture()),
                response.getCreatedAt());
    }

    private PlayerSession convertSession(
            ModelsPlayerSessionResponse response, @Nullable ModelsPlayerClientSettingsResponse settings) {
        return new PlayerSession(
                response.getId(),
                response.getNetworkId(),
                UUID.fromString(Objects.requireNonNull(response.getPlayerId())),
                emptyToNull(response.getAddressHash()),
                response.getClientVersion() != null ? response.getClientVersion() : 0,
                Boolean.TRUE.equals(response.getOnlineMode()),
                response.getLoginTime(),
                emptyToNull(response.getLogoutTime()),
                response.getDurationSeconds() != null ? response.getDurationSeconds().longValue() : null,
                settings != null ? convertClientSettings(settings) : null);
    }

    private PlayerClientSettings convertClientSettings(ModelsPlayerClientSettingsResponse response) {
        PlayerClientSettings result = new PlayerClientSettings();
        result.setChatMode(PlayerClientSettings.ChatMode.parse(response.getChatMode()));
        result.setLocale(emptyToNull(response.getLocale()));
        result.setMainHand(PlayerClientSettings.MainHand.parse(response.getMainHand()));
        result.setViewDistance(response.getViewDistance() != null ? response.getViewDistance() : 0);
        result.setChatColors(Boolean.TRUE.equals(response.getChatColors()));
        result.setClientListingAllowed(Boolean.TRUE.equals(response.getClientListingAllowed()));
        result.setSkinParts(response.getSkinParts());
        return result;
    }

    private Map<String, String> fetchPlayerProperties(UUID uniqueId) throws ApiException {
        ModelsPlayerPropertiesResponse response =
                playersApi.getPlayerProperties(options.getNetworkId(), uniqueId.toString());
        return toPropertiesMap(response.getProperties());
    }

    private Map<String, String> toPropertiesMap(Map<String, String> properties) {
        return properties != null ? properties : new HashMap<>();
    }

    private <T> CompletableFuture<T> call(ApiCalls.Call<T> call) {
        return ApiCalls.supplyAsync(executor, call);
    }

    private <T> CompletableFuture<Optional<T>> callOrEmptyIfNotFound(ApiCalls.Call<T> call) {
        return CompletableFuture.supplyAsync(
                () -> {
                    try {
                        return Optional.ofNullable(call.execute());
                    } catch (ApiException e) {
                        if (e.getCode() == 404) {
                            return Optional.empty();
                        }
                        throw ApiExceptions.from(e);
                    }
                },
                executor);
    }

    private static void validatePagination(int limit, int offset) {
        if (limit < 1 || limit > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_PAGE_SIZE);
        }
        if (offset < 0) {
            throw new IllegalArgumentException("offset must be >= 0");
        }
    }

    private static OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant != null ? OffsetDateTime.ofInstant(instant, ZoneOffset.UTC) : null;
    }

    private static Integer toControllerOnlineTimeSeconds(Long seconds) {
        return seconds != null ? toControllerOnlineTimeSeconds(seconds.longValue()) : null;
    }

    private static int toControllerOnlineTimeSeconds(long seconds) {
        if (seconds < 0 || seconds > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("seconds must be between 0 and " + Integer.MAX_VALUE);
        }
        return (int) seconds;
    }

    private static long toOnlineTimeSeconds(Integer onlineTimeSeconds) {
        return onlineTimeSeconds != null ? onlineTimeSeconds.longValue() : 0L;
    }
}
