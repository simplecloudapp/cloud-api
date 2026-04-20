package app.simplecloud.api.internal.player;

import app.simplecloud.api.CloudApiOptions;
import app.simplecloud.api.internal.web.ApiClients;
import app.simplecloud.api.player.CloudPlayer;
import app.simplecloud.api.player.PlayerApi;
import app.simplecloud.api.web.ApiException;
import app.simplecloud.api.web.apis.PlayersApi;
import app.simplecloud.api.web.models.ModelsDeletePlayerPropertiesRequest;
import app.simplecloud.api.web.models.ModelsOnlinePlayerCountResponse;
import app.simplecloud.api.web.models.ModelsOnlinePlayerResponse;
import app.simplecloud.api.web.models.ModelsOnlinePlayersResponse;
import app.simplecloud.api.web.models.ModelsPlayerPropertiesResponse;
import app.simplecloud.api.web.models.ModelsPlayerResponse;
import app.simplecloud.api.web.models.ModelsUpdatePlayerPropertiesRequest;
import app.simplecloud.api.web.models.ModelsUpdatePlayerPropertiesResponse;
import app.simplecloud.api.web.models.V0PlayersPropertiesDeleteRequest;
import app.simplecloud.api.web.models.V0PlayersPropertiesPutRequest;
import io.nats.client.Connection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Implementation of PlayerApi using REST API for queries.
 */
public class PlayerApiImpl implements PlayerApi {

    private final CloudApiOptions options;
    private final Connection natsConnection;
    private final PlayersApi playersApi;

    public PlayerApiImpl(CloudApiOptions options, Connection natsConnection) {
        this.options = options;
        this.natsConnection = natsConnection;
        this.playersApi = new PlayersApi();
        this.playersApi.setCustomBaseUrl(options.getControllerUrl());
        ApiClients.applyTimeouts(this.playersApi.getApiClient(), options);
        if (options.getComponent() != null && !options.getComponent().isBlank()) {
            this.playersApi.getApiClient().addDefaultHeader("X-SC-Component", options.getComponent());
        }
    }

    @Override
    public CompletableFuture<CloudPlayer> get(UUID uniqueId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ModelsPlayerResponse response = playersApi.v0PlayersIdGet(
                        options.getNetworkId(),
                        options.getNetworkSecret(),
                        uniqueId.toString()
                );

                if (response == null) {
                    return null;
                }

                return convertPlayerResponse(response);
            } catch (ApiException e) {
                if (e.getCode() == 404) {
                    return null;
                }
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public CompletableFuture<CloudPlayer> get(String name) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ModelsPlayerResponse response = playersApi.v0PlayersNameGet(
                        options.getNetworkId(),
                        options.getNetworkSecret(),
                        name
                );

                if (response == null) {
                    return null;
                }

                return convertPlayerResponse(response);
            } catch (ApiException e) {
                if (e.getCode() == 404) {
                    return null;
                }
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public CompletableFuture<List<CloudPlayer>> getOnlinePlayers() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ModelsOnlinePlayersResponse response = playersApi.v0PlayersOnlineGet(
                        options.getNetworkId(),
                        options.getNetworkSecret(),
                        null
                );

                if (response.getPlayers() == null) {
                    return List.of();
                }

                List<CloudPlayer> players = new ArrayList<>();
                for (ModelsOnlinePlayerResponse player : response.getPlayers()) {
                    players.add(convertPlayer(player));
                }
                return players;
            } catch (ApiException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public CompletableFuture<Integer> getOnlinePlayerCount() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ModelsOnlinePlayerCountResponse response = playersApi.v0PlayersOnlineCountGet(
                        options.getNetworkId(),
                        options.getNetworkSecret()
                );
                return response.getCount() != null ? response.getCount() : 0;
            } catch (ApiException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public CompletableFuture<Map<String, String>> updatePlayerProperties(UUID uniqueId, Map<String, String> properties) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ModelsUpdatePlayerPropertiesRequest request = new ModelsUpdatePlayerPropertiesRequest();
                request.setProperties(properties);

                ModelsUpdatePlayerPropertiesResponse response = playersApi.v0PlayersPropertiesPatch(
                        options.getNetworkId(),
                        options.getNetworkSecret(),
                        uniqueId.toString(),
                        new V0PlayersPropertiesPutRequest(request)
                );
                return toPropertiesMap(response.getProperties());
            } catch (ApiException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public CompletableFuture<Map<String, String>> deletePlayerProperties(UUID uniqueId, List<String> keys) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ModelsDeletePlayerPropertiesRequest request = new ModelsDeletePlayerPropertiesRequest();
                request.setKeys(keys);

                playersApi.v0PlayersPropertiesDelete(
                        options.getNetworkId(),
                        options.getNetworkSecret(),
                        uniqueId.toString(),
                        new V0PlayersPropertiesDeleteRequest(request)
                );
                return getPlayerProperties(uniqueId);
            } catch (ApiException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private CloudPlayer convertPlayer(ModelsOnlinePlayerResponse player) {
        return new CloudPlayerImpl(
                natsConnection,
                options.getNetworkId(),
                UUID.fromString(player.getId()),
                player.getName(),
                player.getDisplayName(),
                player.getConnectedProxyName(),
                player.getConnectedServerName(),
                player.getOnline() != null && player.getOnline(),
                player.getOnlineTimeSeconds() != null ? player.getOnlineTimeSeconds().longValue() : 0L,
                player.getSessionId(),
                player.getFirstSeen(),
                player.getLastSeen(),
                player.getProperties()
        );
    }

    private CloudPlayer convertPlayerResponse(ModelsPlayerResponse player) {
        return new CloudPlayerImpl(
                natsConnection,
                options.getNetworkId(),
                UUID.fromString(player.getId()),
                player.getName(),
                player.getDisplayName(),
                player.getConnectedProxyName(),
                player.getConnectedServerName(),
                player.getOnline() != null && player.getOnline(),
                player.getOnlineTimeSeconds() != null ? player.getOnlineTimeSeconds().longValue() : 0L,
                player.getSessionId(),
                player.getFirstSeen(),
                player.getLastSeen(),
                player.getProperties()
        );
    }

    private Map<String, String> getPlayerProperties(UUID uniqueId) throws ApiException {
        ModelsPlayerPropertiesResponse response = playersApi.v0PlayersPropertiesGet(
                options.getNetworkId(),
                options.getNetworkSecret(),
                uniqueId.toString()
        );
        return toPropertiesMap(response.getProperties());
    }

    private Map<String, String> toPropertiesMap(Map<String, String> properties) {
        return properties != null ? properties : new HashMap<>();
    }
}
