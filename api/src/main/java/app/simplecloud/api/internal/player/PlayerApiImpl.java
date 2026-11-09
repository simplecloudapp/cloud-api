package app.simplecloud.api.internal.player;

import app.simplecloud.api.CloudApiException;
import app.simplecloud.api.CloudApiOptions;
import app.simplecloud.api.internal.web.ApiClients;
import app.simplecloud.api.internal.web.ApiErrors;
import app.simplecloud.api.internal.web.Identifiers;
import app.simplecloud.api.player.CloudPlayer;
import app.simplecloud.api.player.ConnectOutcome;
import app.simplecloud.api.player.ConnectStatus;
import app.simplecloud.api.player.ConnectTarget;
import app.simplecloud.api.player.PlayerApi;
import app.simplecloud.api.player.PlayerClientSettings;
import app.simplecloud.api.player.PlayerProfile;
import app.simplecloud.api.player.PlayerQuery;
import app.simplecloud.api.player.PlayerSession;
import app.simplecloud.api.player.ServerSelectionMode;
import app.simplecloud.api.player.UpdateClientSettingsRequest;
import app.simplecloud.api.web.ApiException;
import app.simplecloud.api.web.ApiClient;
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
import com.google.protobuf.InvalidProtocolBufferException;
import io.nats.client.Connection;
import io.nats.client.Message;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

public class PlayerApiImpl implements PlayerApi {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final int MAX_PAGE_SIZE = 100;

    private final CloudApiOptions options;
    private final Connection natsConnection;
    private final PlayersApi playersApi;
    private final Executor executor;

    public PlayerApiImpl(CloudApiOptions options, Connection natsConnection) {
        this(options, natsConnection, ApiClients.create(options));
    }

    public PlayerApiImpl(CloudApiOptions options, Connection natsConnection, ApiClient httpClient) {
        this(options, natsConnection, httpClient, ForkJoinPool.commonPool());
    }

    public PlayerApiImpl(CloudApiOptions options, Connection natsConnection, ApiClient httpClient, Executor executor) {
        this(options, natsConnection, new PlayersApi(httpClient), executor);
    }

    PlayerApiImpl(CloudApiOptions options, Connection natsConnection, PlayersApi playersApi) {
        this(options, natsConnection, playersApi, ForkJoinPool.commonPool());
    }

    PlayerApiImpl(CloudApiOptions options, Connection natsConnection, PlayersApi playersApi, Executor executor) {
        this.options = options;
        this.natsConnection = natsConnection;
        this.playersApi = playersApi;
        this.playersApi.setCustomBaseUrl(options.getControllerUrl());
        this.executor = executor;
    }
    
    @Override
    public CompletableFuture<CloudPlayer> get(UUID uniqueId) {
        return find(uniqueId).thenApply(player -> player.orElseThrow(
                () -> ApiErrors.notFound("Player '" + uniqueId + "' not found")
        ));
    }

    @Override
    public CompletableFuture<CloudPlayer> get(String name) {
        return find(name).thenApply(player -> player.orElseThrow(
                () -> ApiErrors.notFound("Player '" + name + "' not found")
        ));
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
        if (!Identifiers.isPlayerName(name)) {
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
        PlayerQuery q = query != null ? query : PlayerQuery.create();
        List<String> playerIds = q.getPlayerIds() == null ? null : q.getPlayerIds().stream().map(UUID::toString).toList();

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
                    q.getOffset()
            );

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
            ModelsPlayerOnlineTimeResponse response = playersApi.getPlayerOnlineTime(
                    options.getNetworkId(),
                    uniqueId.toString()
            );

            if (response == null || response.getOnlineTimeSeconds() == null) {
                return 0L;
            }
            return response.getOnlineTimeSeconds().longValue();
        });
    }

    @Override
    public CompletableFuture<CloudPlayer> setOnlineTimeSeconds(UUID uniqueId, long seconds) {
        return call(() -> {
            // Validated inside the call, so an out-of-range value fails the returned future.
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
        ModelsPlayerResponse response = playersApi.patchPlayer(
                options.getNetworkId(),
                uniqueId.toString(),
                request
        );
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

            ModelsUpdatePlayerPropertiesResponse response = playersApi.updatePlayerProperties(
                    options.getNetworkId(),
                    uniqueId.toString(),
                    request
            );
            return toPropertiesMap(response.getProperties());
        });
    }

    @Override
    public CompletableFuture<Map<String, String>> updatePlayerProperties(UUID uniqueId, Map<String, String> properties) {
        return call(() -> {
            ModelsUpdatePlayerPropertiesRequest request = new ModelsUpdatePlayerPropertiesRequest();
            request.setProperties(properties);

            ModelsUpdatePlayerPropertiesResponse response = playersApi.patchPlayerProperties(
                    options.getNetworkId(),
                    uniqueId.toString(),
                    request
            );
            return toPropertiesMap(response.getProperties());
        });
    }

    @Override
    public CompletableFuture<Map<String, String>> deletePlayerProperties(UUID uniqueId, List<String> keys) {
        return call(() -> {
            ModelsDeletePlayerPropertiesRequest request = new ModelsDeletePlayerPropertiesRequest();
            request.setKeys(keys);

            playersApi.deletePlayerProperties(
                    options.getNetworkId(),
                    uniqueId.toString(),
                    request
            );
            return fetchPlayerProperties(uniqueId);
        });
    }
    
    @Override
    public CompletableFuture<List<PlayerProfile>> getProfiles(UUID uniqueId, int limit, int offset) {
        return call(() -> {
            validatePagination(limit, offset);
            ModelsListPlayerProfilesResponse response = playersApi.listPlayerProfiles(
                    options.getNetworkId(),
                    uniqueId.toString(),
                    limit,
                    offset
            );

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
        return findLatestProfile(uniqueId).thenApply(profile -> profile.orElseThrow(
                () -> ApiErrors.notFound("No profile recorded for player '" + uniqueId + "'")
        ));
    }

    @Override
    public CompletableFuture<Optional<PlayerProfile>> findLatestProfile(UUID uniqueId) {
        return callOrEmptyIfNotFound(() -> {
            ModelsPlayerProfileResponse response = playersApi.getLatestPlayerProfile(
                    options.getNetworkId(),
                    uniqueId.toString()
            );
            return response != null ? convertProfile(response) : null;
        });
    }

    @Override
    public CompletableFuture<List<PlayerSession>> getSessions(UUID uniqueId, int limit, int offset) {
        return call(() -> {
            validatePagination(limit, offset);
            ModelsListPlayerSessionsResponse response = playersApi.listPlayerSessions(
                    options.getNetworkId(),
                    uniqueId.toString(),
                    limit,
                    offset
            );

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
        return findCurrentSession(uniqueId).thenApply(session -> session.orElseThrow(
                () -> ApiErrors.notFound("Player '" + uniqueId + "' has no active session")
        ));
    }

    @Override
    public CompletableFuture<Optional<PlayerSession>> findCurrentSession(UUID uniqueId) {
        return callOrEmptyIfNotFound(() -> {
            ModelsCurrentSessionResponse response = playersApi.getCurrentSession(
                    options.getNetworkId(),
                    uniqueId.toString()
            );
            if (response == null || response.getSession() == null) {
                return null;
            }
            return convertSession(response.getSession(), response.getSettings());
        });
    }

    @Override
    public CompletableFuture<PlayerClientSettings> getClientSettings(UUID uniqueId) {
        return findClientSettings(uniqueId).thenApply(settings -> settings.orElseThrow(
                () -> ApiErrors.notFound("No client settings for player '" + uniqueId + "'")
        ));
    }

    @Override
    public CompletableFuture<Optional<PlayerClientSettings>> findClientSettings(UUID uniqueId) {
        return callOrEmptyIfNotFound(() -> {
            ModelsPlayerClientSettingsResponse response = playersApi.getClientSettings(
                    options.getNetworkId(),
                    uniqueId.toString()
            );
            return response != null ? convertClientSettings(response) : null;
        });
    }

    @Override
    public CompletableFuture<PlayerClientSettings> updateClientSettings(UUID uniqueId, UpdateClientSettingsRequest request) {
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

            ModelsUpdateClientSettingsResponse response = playersApi.updateClientSettings(
                    options.getNetworkId(),
                    uniqueId.toString(),
                    body
            );
            if (response.getSettings() == null) {
                throw new CloudApiException(
                        CloudApiException.Reason.UNKNOWN,
                        "Controller did not return updated client settings: " + response.getMessage()
                );
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
                ModelsKickPlayerResponse response = playersApi.kickPlayer(
                        options.getNetworkId(),
                        uniqueId.toString(),
                        request
                );
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
    public CompletableFuture<ConnectOutcome> connectTo(UUID uniqueId, ConnectTarget target) {
        Objects.requireNonNull(uniqueId, "uniqueId");
        Objects.requireNonNull(target, "target");
        String value = target.getValue();
        ModelsConnectPlayerRequest request = new ModelsConnectPlayerRequest();
        return switch (target.getKind()) {
            case REGISTERED_SERVER_NAME -> connectViaProxy(uniqueId, value);
            case SERVER_ID -> {
                request.setServerId(value);
                yield connectViaController(uniqueId, request);
            }
            case SERVER_NAME -> {
                request.setServerName(value);
                yield connectViaController(uniqueId, request);
            }
            case GROUP_ID -> {
                request.setServerGroupId(value);
                request.setSelectionMode(selectionModeName(target));
                yield connectViaController(uniqueId, request);
            }
            case GROUP_NAME -> {
                request.setServerGroupName(value);
                request.setSelectionMode(selectionModeName(target));
                yield connectViaController(uniqueId, request);
            }
            case PERSISTENT_SERVER_ID -> {
                request.setPersistentServerId(value);
                yield connectViaController(uniqueId, request);
            }
        };
    }

    /**
     * Returns the target's selection mode for the request, or null, which leaves the choice to the controller.
     */
    private static @Nullable String selectionModeName(ConnectTarget target) {
        ServerSelectionMode selectionMode = target.getSelectionMode();
        return selectionMode != null ? selectionMode.name() : null;
    }

    private CompletableFuture<ConnectOutcome> connectViaProxy(UUID uniqueId, String registeredServerName) {
        return CompletableFuture.supplyAsync(() -> requestConnectFromProxy(uniqueId, registeredServerName), executor);
    }

    private ConnectOutcome requestConnectFromProxy(UUID uniqueId, String registeredServerName) {
        ConnectPlayerRequest request = ConnectPlayerRequest.newBuilder()
                .setPlayerId(uniqueId.toString())
                .setServerName(registeredServerName)
                .build();
        String subject = options.getNetworkId() + ".player." + uniqueId + ".connect";
        Message answer = sendToProxy(uniqueId, subject, request);
        ConnectPlayerResponse proxyResponse;
        try {
            proxyResponse = ConnectPlayerResponse.parseFrom(answer.getData());
        } catch (InvalidProtocolBufferException e) {
            throw unreadableAnswer("The player's proxy sent a connect answer that can't be read", e);
        }
        ConnectStatus status = toConnectStatus(proxyResponse);
        return ConnectOutcome.of(status, null);
    }

    private Message sendToProxy(UUID uniqueId, String subject, ConnectPlayerRequest request) {
        Message answer;
        try {
            answer = natsConnection.request(subject, request.toByteArray(), REQUEST_TIMEOUT);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            String message = "The connect for player '" + uniqueId + "' was interrupted; "
                    + "the player may still have been moved";
            throw new CloudApiException(CloudApiException.Reason.UNKNOWN, 0, message, e);
        } catch (RuntimeException e) {
            String message = "The connect for player '" + uniqueId + "' couldn't be sent to the proxies: "
                    + e.getMessage();
            throw new CloudApiException(CloudApiException.Reason.UNAVAILABLE, 0, message, e);
        }
        if (answer == null) {
            String message = "No proxy answered the connect for player '" + uniqueId + "' within "
                    + REQUEST_TIMEOUT.toSeconds() + " seconds; the player may be offline or may still have been moved";
            throw new CloudApiException(CloudApiException.Reason.UNAVAILABLE, message);
        }
        return answer;
    }

    private static ConnectStatus toConnectStatus(ConnectPlayerResponse proxyResponse) {
        return switch (proxyResponse.getResult()) {
            case CONNECT_RESULT_SUCCESS -> ConnectStatus.SUCCESS;
            case CONNECT_RESULT_ALREADY_CONNECTED -> ConnectStatus.ALREADY_CONNECTED;
            case CONNECT_RESULT_SERVER_NOT_FOUND -> ConnectStatus.SERVER_NOT_REGISTERED;
            // The controller counts a result the proxy left unset as failed too.
            case CONNECT_RESULT_FAILED, CONNECT_RESULT_UNSPECIFIED -> ConnectStatus.FAILED;
            case UNRECOGNIZED -> throw unreadableAnswer(
                    "The player's proxy sent a connect result this version doesn't know: "
                            + proxyResponse.getResultValue(),
                    null
            );
        };
    }

    private static CloudApiException unreadableAnswer(String message, @Nullable Exception cause) {
        return new CloudApiException(CloudApiException.Reason.UNKNOWN, 0, message, cause);
    }

    private CompletableFuture<ConnectOutcome> connectViaController(UUID uniqueId, ModelsConnectPlayerRequest request) {
        return CompletableFuture.supplyAsync(() -> requestConnectFromController(uniqueId, request), executor);
    }

    private ConnectOutcome requestConnectFromController(UUID uniqueId, ModelsConnectPlayerRequest request) {
        ModelsConnectPlayerResponse response;
        try {
            response = playersApi.connectPlayer(options.getNetworkId(), uniqueId.toString(), request);
        } catch (ApiException e) {
            if (e.getCode() == 404) {
                return notFoundOutcome(e);
            }
            throw ApiErrors.from(e);
        }
        ConnectStatus status = toConnectStatus(response);
        return ConnectOutcome.of(status, response.getServerName());
    }

    /**
     * Tells the two 404 answers apart. Only their text differs, and {@code .61} reads the same text.
     */
    private static ConnectOutcome notFoundOutcome(ApiException notFound) {
        String body = notFound.getResponseBody();
        boolean playerNotOnline = body != null && body.contains("Player is not online");
        ConnectStatus status = playerNotOnline ? ConnectStatus.PLAYER_NOT_ONLINE : ConnectStatus.TARGET_NOT_FOUND;
        return ConnectOutcome.of(status, null);
    }

    private static ConnectStatus toConnectStatus(@Nullable ModelsConnectPlayerResponse response) {
        if (response == null || response.getResult() == null) {
            throw unreadableAnswer("The controller's connect answer has no result", null);
        }
        return switch (response.getResult()) {
            case ConnectResultSuccess -> ConnectStatus.SUCCESS;
            case ConnectResultAlreadyConnected -> ConnectStatus.ALREADY_CONNECTED;
            case ConnectResultNoServerAvailable -> ConnectStatus.NO_SERVER_AVAILABLE;
            case ConnectResultServerNotFound -> ConnectStatus.SERVER_NOT_REGISTERED;
            case ConnectResultFailed -> ConnectStatus.FAILED;
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
                player.getProperties()
        );
    }

    private CloudPlayer convertPlayerResponse(ModelsPlayerResponse player) {
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
                player.getProperties()
        );
    }

    private PlayerProfile convertProfile(ModelsPlayerProfileResponse response) {
        PlayerProfile result = new PlayerProfile();
        result.setId(response.getId());
        result.setPlayerId(UUID.fromString(response.getPlayerId()));
        result.setName(response.getName());
        result.setTexture(emptyToNull(response.getTexture()));
        result.setCreatedAt(response.getCreatedAt());
        return result;
    }

    private PlayerSession convertSession(
            ModelsPlayerSessionResponse response,
            @Nullable ModelsPlayerClientSettingsResponse settings
    ) {
        PlayerSession result = new PlayerSession();
        result.setId(response.getId());
        result.setNetworkId(response.getNetworkId());
        result.setPlayerId(UUID.fromString(response.getPlayerId()));
        result.setAddressHash(emptyToNull(response.getAddressHash()));
        result.setClientVersion(response.getClientVersion() != null ? response.getClientVersion() : 0);
        result.setOnlineMode(Boolean.TRUE.equals(response.getOnlineMode()));
        result.setLoginTime(response.getLoginTime());
        result.setLogoutTime(emptyToNull(response.getLogoutTime()));
        if (response.getDurationSeconds() != null) {
            result.setDurationSeconds(response.getDurationSeconds().longValue());
        }
        if (settings != null) {
            result.setClientSettings(convertClientSettings(settings));
        }
        return result;
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

    private static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    private Map<String, String> fetchPlayerProperties(UUID uniqueId) throws ApiException {
        ModelsPlayerPropertiesResponse response = playersApi.getPlayerProperties(
                options.getNetworkId(),
                uniqueId.toString()
        );
        return toPropertiesMap(response.getProperties());
    }

    private Map<String, String> toPropertiesMap(Map<String, String> properties) {
        return properties != null ? properties : new HashMap<>();
    }

    private <T> CompletableFuture<T> call(ApiCall<T> call) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return call.execute();
            } catch (ApiException e) {
                throw ApiErrors.from(e);
            }
        }, executor);
    }

    private <T> CompletableFuture<Optional<T>> callOrEmptyIfNotFound(ApiCall<T> call) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return Optional.ofNullable(call.execute());
            } catch (ApiException e) {
                if (e.getCode() == 404) {
                    return Optional.empty();
                }
                throw ApiErrors.from(e);
            }
        }, executor);
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

    @FunctionalInterface
    private interface ApiCall<T> {
        T execute() throws ApiException;
    }
}
