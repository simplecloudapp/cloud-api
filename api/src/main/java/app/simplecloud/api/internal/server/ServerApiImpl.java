package app.simplecloud.api.internal.server;

import app.simplecloud.api.CloudApiOptions;
import app.simplecloud.api.cache.QueryCache;
import app.simplecloud.api.cache.QueryKey;
import app.simplecloud.api.group.Group;
import app.simplecloud.api.persistentserver.PersistentServer;
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
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class ServerApiImpl implements ServerApi {

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
    public CompletableFuture<Server> getServerByName(String serverName, char splitChar) {
        ParsedServerName parsedServerName = parseServerName(serverName, splitChar);
        if (parsedServerName == null) {
            return CompletableFuture.completedFuture(null);
        }

        QueryKey key = QueryKey.of("server", "serverName", serverName, splitChar);

        return cache.fetch(key, () -> CompletableFuture.supplyAsync(() -> {
            try {
                ServerQuery query = ServerQuery.create()
                        .filterByServerBaseName(parsedServerName.fullName(), parsedServerName.serverBaseName())
                        .filterByNumericalId(-1, parsedServerName.numericalId());
                ModelsListServersResponse serversResponse = executeQuery(query);

                List<ModelsServerSummary> servers = serversResponse.getServers();
                if (servers == null || servers.isEmpty()) {
                    throw new RuntimeException("Server not found: " + serverName);
                }

                Server server = findServerByName(servers, parsedServerName);
                if (server != null) {
                    return server;
                }

                throw new RuntimeException("Server not found: " + serverName);
            } catch (ApiException e) {
                throw new RuntimeException(e);
            }
        }));
    }

    @Override
    public CompletableFuture<Server> getServerByNumericalId(String serverBaseName, int numericalId) {
        QueryKey key = QueryKey.of("server", "numerical", "serverBaseName", serverBaseName, numericalId);

        return cache.fetch(key, () -> CompletableFuture.supplyAsync(() -> {
            try {
                ServerQuery query = ServerQuery.create()
                        .filterByServerBaseName(serverBaseName)
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
                throw new RuntimeException("Server not found for server base name " + serverBaseName + " with numerical ID " + numericalId);

            } catch (ApiException e) {
                throw new RuntimeException(e);
            }
        }));
    }

    @Override
    public CompletableFuture<List<Server>> getServersByServerBaseName(String serverBaseName) {
        QueryKey key = QueryKey.of("servers", "serverBaseName", serverBaseName);

        return cache.fetch(key, () -> CompletableFuture.supplyAsync(() -> {
            try {
                ServerQuery query = ServerQuery.create()
                        .filterByServerBaseName(serverBaseName);
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

        return cache.fetch(key, () -> CompletableFuture.supplyAsync(() -> {
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
    }

    private QueryKey buildQueryKey(@Nullable ServerQuery query) {
        if (query == null) {
            return QueryKey.of("servers");
        }
        // Build a deterministic cache key from query parameters
        return QueryKey.of("servers", "query",
                query.getServerGroupIds(),
                query.getStates(),
                query.getServerhostId(),
                query.getPersistentServerId(),
                query.getServerGroupTypes(),
                query.getServerBaseNames(),
                query.getServerGroupTags(),
                query.getNumericalIds(),
                query.getSortBy(),
                query.getSortOrder()
        );
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

        String serverBaseName = null;
        if (query != null && query.getServerBaseNames() != null && !query.getServerBaseNames().isEmpty()) {
            serverBaseName = String.join(",", query.getServerBaseNames());
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
                serverBaseName,
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

    private Server findServerByName(List<ModelsServerSummary> servers, ParsedServerName parsedServerName) {
        Server fallback = null;

        for (ModelsServerSummary summary : servers) {
            Server server = new ServerImpl(summary);
            if (isExactPersistentServerMatch(server, parsedServerName)
                    || isExactGroupServerMatch(server, parsedServerName)) {
                return server;
            }

            if (fallback == null && summary.getNumericalId() != null
                    && (summary.getNumericalId() == -1 || summary.getNumericalId() == parsedServerName.numericalId())) {
                fallback = server;
            }
        }

        return fallback;
    }

    private boolean isExactPersistentServerMatch(Server server, ParsedServerName parsedServerName) {
        if (server.getNumericalId() != -1) {
            return false;
        }

        PersistentServer persistentServer = server.getPersistentServer();
        return persistentServer != null && parsedServerName.fullName().equals(persistentServer.getName());
    }

    private boolean isExactGroupServerMatch(Server server, ParsedServerName parsedServerName) {
        if (server.getNumericalId() != parsedServerName.numericalId()) {
            return false;
        }

        Group group = server.getGroup();
        return group != null && parsedServerName.serverBaseName().equals(group.getName());
    }

    @Nullable
    private ParsedServerName parseServerName(String serverName, char splitChar) {
        if (serverName == null) {
            return null;
        }

        String trimmedServerName = serverName.trim();
        if (trimmedServerName.isEmpty()) {
            return null;
        }

        int separatorIndex = trimmedServerName.lastIndexOf(splitChar);
        if (separatorIndex <= 0 || separatorIndex == trimmedServerName.length() - 1) {
            return new ParsedServerName(trimmedServerName, trimmedServerName, -1);
        }

        String serverBaseName = trimmedServerName.substring(0, separatorIndex).trim();
        String numericalIdPart = trimmedServerName.substring(separatorIndex + 1).trim();
        if (serverBaseName.isEmpty() || numericalIdPart.isEmpty()) {
            return new ParsedServerName(trimmedServerName, trimmedServerName, -1);
        }

        try {
            return new ParsedServerName(trimmedServerName, serverBaseName, Integer.parseInt(numericalIdPart));
        } catch (NumberFormatException ignored) {
            return new ParsedServerName(trimmedServerName, trimmedServerName, -1);
        }
    }

    private record ParsedServerName(String fullName, String serverBaseName, int numericalId) {
    }
}
