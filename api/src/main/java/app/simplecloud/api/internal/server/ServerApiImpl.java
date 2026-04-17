package app.simplecloud.api.internal.server;

import app.simplecloud.api.CloudApiOptions;
import app.simplecloud.api.cache.QueryCache;
import app.simplecloud.api.cache.QueryKey;
import app.simplecloud.api.server.Server;
import app.simplecloud.api.server.ServerApi;
import app.simplecloud.api.server.ServerQuery;
import app.simplecloud.api.server.UpdateServerRequest;
import app.simplecloud.api.web.ApiException;
import app.simplecloud.api.web.apis.ServersApi;
import app.simplecloud.api.web.models.ModelsDeletePropertiesRequest;
import app.simplecloud.api.web.models.ModelsDeletePropertiesResponse;
import app.simplecloud.api.web.models.ModelsListServersResponse;
import app.simplecloud.api.web.models.ModelsPatchPropertiesRequest;
import app.simplecloud.api.web.models.ModelsPatchPropertiesResponse;
import app.simplecloud.api.web.models.ModelsPatchServerRequest;
import app.simplecloud.api.web.models.ModelsPatchServerResponse;
import app.simplecloud.api.web.models.ModelsServerSummary;
import app.simplecloud.api.web.models.V0PersistentServersPropertiesDeleteRequest;
import app.simplecloud.api.web.models.V0PersistentServersPropertiesPatchRequest;
import app.simplecloud.api.web.models.V0ServersPatchRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

public class ServerApiImpl implements ServerApi {
    private static final Logger LOGGER = Logger.getLogger(ServerApiImpl.class.getName());

    private final CloudApiOptions options;
    private final ServersApi serversApi;
    private final QueryCache cache;

    public ServerApiImpl(CloudApiOptions options, QueryCache cache) {
        this.options = options;
        this.cache = cache;
        this.serversApi = new ServersApi();
        this.serversApi.setCustomBaseUrl(options.getControllerUrl());
        if (options.getComponent() != null && !options.getComponent().isBlank()) {
            this.serversApi.getApiClient().addDefaultHeader("X-SC-Component", options.getComponent());
        }
    }

    @Override
    public CompletableFuture<Server> getServerById(String id) {
        QueryKey key = QueryKey.of("server", id);

        return cache.fetch(key, () -> CompletableFuture.supplyAsync(() -> {
            try {
                ServerQuery query = ServerQuery.create();
                ModelsListServersResponse serversResponse = executeQuery(query);

                List<ModelsServerSummary> servers = serversResponse.getServers();
                if (servers != null) {
                    for (ModelsServerSummary summary : servers) {
                        if (id.equals(summary.getServerId())) {
                            return new ServerImpl(summary);
                        }
                    }
                }
                throw new RuntimeException("Server not found: " + id);
            } catch (ApiException e) {
                throw new RuntimeException(e);
            }
        }));
    }

    @Override
    public CompletableFuture<Server> getServerByNumericalId(String groupName, int numericalId) {
        QueryKey key = QueryKey.of("server", "numerical", groupName, numericalId);

        return cache.fetch(key, () -> CompletableFuture.supplyAsync(() -> {
            try {
                ServerQuery query = ServerQuery.create()
                        .filterByServerGroupName(groupName)
                        .filterByNumericalId(numericalId);
                ModelsListServersResponse serversResponse = executeQuery(query);

                List<ModelsServerSummary> servers = serversResponse.getServers();
                if (servers == null) {
                    return null;
                }

                for (ModelsServerSummary summary : servers) {
                    if (summary.getNumericalId() != null && summary.getNumericalId() == numericalId) {
                        return new ServerImpl(summary);
                    }
                }
                throw new RuntimeException("Server not found in group " + groupName + " with numerical ID " + numericalId);

            } catch (ApiException e) {
                throw new RuntimeException(e);
            }
        }));
    }

    @Override
    public CompletableFuture<List<Server>> getServersByGroup(String groupName) {
        QueryKey key = QueryKey.of("servers", "group", groupName);

        return cache.fetch(key, () -> CompletableFuture.supplyAsync(() -> {
            try {
                ServerQuery query = ServerQuery.create()
                        .filterByServerGroupName(groupName);
                ModelsListServersResponse serversResponse = executeQuery(query);

                List<ModelsServerSummary> servers = serversResponse.getServers();
                if (servers == null) {
                    return List.of();
                }

                return servers.stream()
                        .<Server>map(ServerImpl::new)
                        .toList();
            } catch (ApiException e) {
                throw new RuntimeException(e);
            }
        }));
    }

    @Override
    public CompletableFuture<List<Server>> getAllServers(@Nullable ServerQuery query) {
        QueryKey key = buildQueryKey(query);

        CompletableFuture<List<Server>> future = cache.fetch(key, () -> CompletableFuture.supplyAsync(() -> {
            try {
                ModelsListServersResponse serversResponse = executeQuery(query);

                List<ModelsServerSummary> servers = serversResponse.getServers();
                if (servers == null) {
                    return List.of();
                }

                return servers.stream()
                        .<Server>map(ServerImpl::new)
                        .toList();
            } catch (ApiException e) {
                throw new RuntimeException(e);
            }
        }));

        return future.thenApply(servers -> {
            if (debugEmptyServerResultsEnabled() && servers.isEmpty()) {
                logEmptyServerResult(query, key);
            }
            return servers;
        });
    }

    private QueryKey buildQueryKey(@Nullable ServerQuery query) {
        if (query == null) {
            return QueryKey.of("servers");
        }
        // Snapshot mutable builder state so cache keys remain stable after query construction.
        return QueryKey.of("servers", "query",
                snapshot(query.getServerGroupIds()),
                snapshot(query.getStates()),
                query.getServerhostId(),
                query.getPersistentServerId(),
                snapshot(query.getServerGroupTypes()),
                snapshot(query.getServerGroupNames()),
                snapshot(query.getServerGroupTags()),
                snapshot(query.getNumericalIds()),
                query.getSortBy(),
                query.getSortOrder()
        );
    }

    private static <T> List<T> snapshot(@Nullable List<T> values) {
        return values == null ? null : List.copyOf(values);
    }

    private void logEmptyServerResult(@Nullable ServerQuery query, QueryKey key) {
        LOGGER.warning(() -> "getAllServers returned an empty result"
                + " [runtimeServer=" + runtimeServerName()
                + ", uniqueId=" + envValue("SIMPLECLOUD_UNIQUE_ID")
                + ", group=" + envValue("SIMPLECLOUD_GROUP")
                + ", numericalId=" + envValue("SIMPLECLOUD_NUMERICAL_ID")
                + ", component=" + (options.getComponent() == null ? "" : options.getComponent())
                + ", controllerUrl=" + options.getControllerUrl()
                + ", cacheKey=" + key
                + ", query=" + describeQuery(query)
                + "]");
    }

    private String describeQuery(@Nullable ServerQuery query) {
        if (query == null) {
            return "null";
        }

        return "{serverGroupIds=" + query.getServerGroupIds()
                + ", states=" + query.getStates()
                + ", serverhostId=" + query.getServerhostId()
                + ", persistentServerId=" + query.getPersistentServerId()
                + ", serverGroupTypes=" + query.getServerGroupTypes()
                + ", serverGroupNames=" + query.getServerGroupNames()
                + ", serverGroupTags=" + query.getServerGroupTags()
                + ", numericalIds=" + query.getNumericalIds()
                + ", sortBy=" + query.getSortBy()
                + ", sortOrder=" + query.getSortOrder()
                + "}";
    }

    private boolean debugEmptyServerResultsEnabled() {
        return switch (envValue("SIMPLECLOUD_CLOUD_API_DEBUG").toLowerCase(Locale.ROOT)) {
            case "true", "1", "yes", "on" -> true;
            default -> false;
        };
    }

    private String runtimeServerName() {
        String groupName = envValue("SIMPLECLOUD_GROUP");
        String numericalId = envValue("SIMPLECLOUD_NUMERICAL_ID");
        if (!groupName.isBlank() && !numericalId.isBlank()) {
            return groupName + "-" + numericalId;
        }
        if (!groupName.isBlank()) {
            return groupName;
        }
        return envValue("SIMPLECLOUD_UNIQUE_ID");
    }

    private String envValue(String key) {
        String value = System.getenv(key);
        return value == null ? "" : value.trim();
    }

    private ModelsListServersResponse executeQuery(@Nullable ServerQuery query) throws ApiException {
        String serverGroupId = null;
        if (query != null && query.getServerGroupIds() != null && !query.getServerGroupIds().isEmpty()) {
            serverGroupId = String.join(",", query.getServerGroupIds());
        }

        String state = null;
        if (query != null && query.getStates() != null && !query.getStates().isEmpty()) {
            state = query.getStates().stream()
                    .map(Enum::name)
                    .collect(java.util.stream.Collectors.joining(","));
        }

        String serverhostId = query != null ? query.getServerhostId() : null;
        String persistentServerId = query != null ? query.getPersistentServerId() : null;

        String serverGroupType = null;
        if (query != null && query.getServerGroupTypes() != null && !query.getServerGroupTypes().isEmpty()) {
            serverGroupType = query.getServerGroupTypes().stream()
                    .map(Enum::name)
                    .collect(java.util.stream.Collectors.joining(","));
        }

        String serverGroupName = null;
        if (query != null && query.getServerGroupNames() != null && !query.getServerGroupNames().isEmpty()) {
            serverGroupName = String.join(",", query.getServerGroupNames());
        }

        String serverGroupTags = null;
        if (query != null && query.getServerGroupTags() != null && !query.getServerGroupTags().isEmpty()) {
            serverGroupTags = String.join(",", query.getServerGroupTags());
        }

        String numericalIds = null;
        if (query != null && query.getNumericalIds() != null && !query.getNumericalIds().isEmpty()) {
            numericalIds = query.getNumericalIds().stream().map(Object::toString).collect(java.util.stream.Collectors.joining(","));
        }

        String sortBy = query != null ? query.getSortBy() : null;
        String sortOrder = query != null ? query.getSortOrder() : null;

        return serversApi.v0ServersGet(
                this.options.getNetworkId(),
                this.options.getNetworkSecret(),
                serverGroupId,
                state,
                serverhostId,
                persistentServerId,
                serverGroupType,
                serverGroupName,
                serverGroupTags,
                numericalIds,
                sortBy,
                sortOrder
        );
    }

    @Override
    public CompletableFuture<Void> stopServer(String id) {
        return CompletableFuture.runAsync(() -> {
            try {
                serversApi.v0ServersDelete(
                        this.options.getNetworkId(),
                        this.options.getNetworkSecret(),
                        id
                );

                // Invalidate this server and list caches
                cache.invalidate(QueryKey.of("server", id));
                cache.invalidateAll(QueryKey.of("servers"));
            } catch (ApiException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public CompletableFuture<Server> updateServer(String id, UpdateServerRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ModelsPatchServerRequest patchRequest = getModelsPatchServerRequest(request);

                ModelsPatchServerResponse response = serversApi.v0ServersPatch(
                        this.options.getNetworkId(),
                        this.options.getNetworkSecret(),
                        id,
                        new V0ServersPatchRequest(patchRequest)
                );

                if (response.getServer() == null) {
                    throw new RuntimeException("Server update response did not contain server data");
                }

                Server server = new ServerImpl(response.getServer());

                // Update cache with new data and invalidate list caches
                cache.set(QueryKey.of("server", id), server);
                cache.invalidateAll(QueryKey.of("servers"));

                return server;
            } catch (ApiException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @NotNull
    private static ModelsPatchServerRequest getModelsPatchServerRequest(UpdateServerRequest request) {
        ModelsPatchServerRequest patchRequest = new ModelsPatchServerRequest();
        patchRequest.setMaxMemory(request.getMaxMemory());
        patchRequest.setMaxPlayers(request.getMaxPlayers());
        patchRequest.setMinMemory(request.getMinMemory());
        patchRequest.setProperties(request.getProperties());
        patchRequest.setPlayerCount(request.getPlayerCount());
        if (request.getState() != null) {
            patchRequest.setState(ModelsPatchServerRequest.StateEnum.valueOf(request.getState().name()));
        }
        return patchRequest;
    }

    @Override
    public CompletableFuture<Server> getCurrentServer() {
        String serverId = System.getenv("SIMPLECLOUD_UNIQUE_ID");
        if (serverId == null || serverId.isEmpty()) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("SIMPLECLOUD_UNIQUE_ID environment variable is not set")
            );
        }
        return getServerById(serverId);
    }

    @Override
    public CompletableFuture<Map<String, Object>> updateServerProperties(String id, Map<String, Object> properties) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ModelsPatchPropertiesRequest request = new ModelsPatchPropertiesRequest();
                request.setProperties(properties);

                ModelsPatchPropertiesResponse response = serversApi.v0ServersPropertiesPatch(
                        this.options.getNetworkId(),
                        this.options.getNetworkSecret(),
                        id,
                        new V0PersistentServersPropertiesPatchRequest(request)
                );

                // Invalidate server cache (properties changed)
                cache.invalidate(QueryKey.of("server", id));
                cache.invalidateAll(QueryKey.of("servers"));

                Map<String, Object> result = response.getProperties();
                return result != null ? result : new HashMap<>();
            } catch (ApiException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public CompletableFuture<Map<String, Object>> deleteServerProperties(String id, List<String> keys) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ModelsDeletePropertiesRequest request = new ModelsDeletePropertiesRequest();
                request.setKeys(keys);

                ModelsDeletePropertiesResponse response = serversApi.v0ServersPropertiesDelete(
                        this.options.getNetworkId(),
                        this.options.getNetworkSecret(),
                        id,
                        new V0PersistentServersPropertiesDeleteRequest(request)
                );

                // Invalidate server cache (properties changed)
                cache.invalidate(QueryKey.of("server", id));
                cache.invalidateAll(QueryKey.of("servers"));

                Map<String, Object> result = response.getProperties();
                return result != null ? result : new HashMap<>();
            } catch (ApiException e) {
                throw new RuntimeException(e);
            }
        });
    }
}
