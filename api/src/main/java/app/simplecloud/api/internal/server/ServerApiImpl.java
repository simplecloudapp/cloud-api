package app.simplecloud.api.internal.server;

import app.simplecloud.api.CloudApiException;
import app.simplecloud.api.CloudApiOptions;
import app.simplecloud.api.cache.QueryCache;
import app.simplecloud.api.cache.QueryKey;
import app.simplecloud.api.internal.networkcopy.NetworkContents;
import app.simplecloud.api.internal.networkcopy.NetworkCopySynchronizer;
import app.simplecloud.api.internal.web.ApiClients;
import app.simplecloud.api.internal.web.ApiErrors;
import app.simplecloud.api.internal.web.Identifiers;
import app.simplecloud.api.server.Server;
import app.simplecloud.api.server.ServerApi;
import app.simplecloud.api.server.ServerQuery;
import app.simplecloud.api.server.UpdateServerRequest;
import app.simplecloud.api.web.ApiException;
import app.simplecloud.api.web.ApiClient;
import app.simplecloud.api.web.ApiResponse;
import app.simplecloud.api.web.apis.ServersApi;
import app.simplecloud.api.web.models.ModelsDeletePropertiesRequest;
import app.simplecloud.api.web.models.ModelsDeletePropertiesResponse;
import app.simplecloud.api.web.models.ModelsPatchPropertiesRequest;
import app.simplecloud.api.web.models.ModelsPatchPropertiesResponse;
import app.simplecloud.api.web.models.ModelsPatchServerRequest;
import app.simplecloud.api.web.models.ModelsPatchServerResponse;
import app.simplecloud.api.web.models.ModelsStopServerResponse;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

public class ServerApiImpl implements ServerApi {
    private final CloudApiOptions options;
    private final ServersApi serversApi;
    private final QueryCache cache;
    private final Executor executor;
    private final NetworkCopySynchronizer networkCopySynchronizer;
    private final HttpServerReadApi httpReadApi;

    public ServerApiImpl(CloudApiOptions options, QueryCache cache) {
        this(options, cache, ApiClients.create(options));
    }

    public ServerApiImpl(CloudApiOptions options, QueryCache cache, ApiClient httpClient) {
        this(options, cache, httpClient, ForkJoinPool.commonPool());
    }

    public ServerApiImpl(CloudApiOptions options, QueryCache cache, ApiClient httpClient, Executor executor) {
        this(options, cache, httpClient, executor, NetworkCopySynchronizer.createNeverStarted());
    }

    public ServerApiImpl(CloudApiOptions options,
                         QueryCache cache,
                         ApiClient httpClient,
                         Executor executor,
                         NetworkCopySynchronizer networkCopySynchronizer) {
        this(options, cache, createServersApi(options, httpClient), executor, networkCopySynchronizer);
    }

    private ServerApiImpl(CloudApiOptions options,
                          QueryCache cache,
                          ServersApi serversApi,
                          Executor executor,
                          NetworkCopySynchronizer networkCopySynchronizer) {
        this.options = options;
        this.cache = cache;
        this.serversApi = serversApi;
        this.executor = executor;
        this.networkCopySynchronizer = networkCopySynchronizer;
        this.httpReadApi = new HttpServerReadApi(options, serversApi, cache, executor);
    }

    @Override
    public CompletableFuture<Server> getServerById(String id) {
        return findServerById(id).thenApply(server -> server.orElseThrow(
                () -> ApiErrors.notFound("Server with ID '" + id + "' not found")
        ));
    }

    @Override
    public CompletableFuture<Optional<Server>> findServerById(String id) {
        if (!Identifiers.isUuid(id)) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return selectServerReadApi().findServerById(id);
    }

    @Override
    public CompletableFuture<Server> getServerByNumericalId(String groupName, int numericalId) {
        return findServerByNumericalId(groupName, numericalId).thenApply(server -> server.orElseThrow(
                () -> ApiErrors.notFound("Server '" + groupName + "-" + numericalId + "' not found")
        ));
    }

    @Override
    public CompletableFuture<Optional<Server>> findServerByNumericalId(String groupName, int numericalId) {
        return selectServerReadApi().findServerByNumericalId(groupName, numericalId);
    }

    @Override
    public CompletableFuture<List<Server>> getServersByGroup(String groupName) {
        return selectServerReadApi().getServersByGroup(groupName);
    }

    @Override
    public CompletableFuture<List<Server>> getAllServers(@Nullable ServerQuery query) {
        return selectServerReadApi().getAllServers(query);
    }

    private InternalServerReadApi selectServerReadApi() {
        NetworkContents contents = networkCopySynchronizer.currentContents();
        if (contents == null) {
            return httpReadApi;
        }
        return new NetworkCopyServerReadApi(contents);
    }

    private static ServersApi createServersApi(CloudApiOptions options, ApiClient httpClient) {
        ServersApi serversApi = new ServersApi(httpClient);
        serversApi.setCustomBaseUrl(options.getControllerUrl());
        return serversApi;
    }

    @Override
    public CompletableFuture<Void> stopServer(String id) {
        return CompletableFuture.runAsync(() -> {
            try {
                ApiResponse<ModelsStopServerResponse> httpResponse = serversApi.stopServerWithHttpInfo(
                        this.options.getNetworkId(),
                        id
                );

                // Invalidate this server and list caches
                cache.invalidate(QueryKey.of("server", id));
                cache.invalidateAll(QueryKey.of("servers"));
                cache.invalidateAll(QueryKey.of("server", "numerical"));

                networkCopySynchronizer.waitForOwnWrite(httpResponse.getHeaders());
            } catch (ApiException e) {
                throw ApiErrors.from(e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Server> updateServer(String id, UpdateServerRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ModelsPatchServerRequest patchRequest = getModelsPatchServerRequest(request);

                ApiResponse<ModelsPatchServerResponse> httpResponse = serversApi.patchServerWithHttpInfo(
                        this.options.getNetworkId(),
                        id,
                        patchRequest
                );
                ModelsPatchServerResponse response = httpResponse.getData();

                if (response.getServer() == null) {
                    throw new CloudApiException(
                            CloudApiException.Reason.UNKNOWN,
                            "Server update response did not contain server data"
                    );
                }

                Server server = new ServerImpl(response.getServer());

                // Update cache with new data and invalidate list caches
                cache.set(QueryKey.of("server", id), server);
                cache.invalidateAll(QueryKey.of("servers"));
                cache.invalidateAll(QueryKey.of("server", "numerical"));

                networkCopySynchronizer.waitForOwnWrite(httpResponse.getHeaders());
                return server;
            } catch (ApiException e) {
                throw ApiErrors.from(e);
            }
        }, executor);
    }

    @NotNull
    private static ModelsPatchServerRequest getModelsPatchServerRequest(UpdateServerRequest request) {
        ModelsPatchServerRequest patchRequest = new ModelsPatchServerRequest();
        patchRequest.setMaxMemory(request.getMaxMemory());
        patchRequest.setMaxPlayers(request.getMaxPlayers());
        patchRequest.setMinMemory(request.getMinMemory());
        patchRequest.setProperties(request.getProperties());
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

                ApiResponse<ModelsPatchPropertiesResponse> httpResponse = serversApi.patchServerPropertiesWithHttpInfo(
                        this.options.getNetworkId(),
                        id,
                        request
                );
                ModelsPatchPropertiesResponse response = httpResponse.getData();

                // Invalidate server cache (properties changed)
                cache.invalidate(QueryKey.of("server", id));
                cache.invalidateAll(QueryKey.of("servers"));
                cache.invalidateAll(QueryKey.of("server", "numerical"));

                networkCopySynchronizer.waitForOwnWrite(httpResponse.getHeaders());
                Map<String, Object> result = response.getProperties();
                return result != null ? result : new HashMap<>();
            } catch (ApiException e) {
                throw ApiErrors.from(e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Map<String, Object>> deleteServerProperties(String id, List<String> keys) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ModelsDeletePropertiesRequest request = new ModelsDeletePropertiesRequest();
                request.setKeys(keys);

                ApiResponse<ModelsDeletePropertiesResponse> httpResponse = serversApi.deleteServerPropertiesWithHttpInfo(
                        this.options.getNetworkId(),
                        id,
                        request
                );
                ModelsDeletePropertiesResponse response = httpResponse.getData();

                // Invalidate server cache (properties changed)
                cache.invalidate(QueryKey.of("server", id));
                cache.invalidateAll(QueryKey.of("servers"));
                cache.invalidateAll(QueryKey.of("server", "numerical"));

                networkCopySynchronizer.waitForOwnWrite(httpResponse.getHeaders());
                Map<String, Object> result = response.getProperties();
                return result != null ? result : new HashMap<>();
            } catch (ApiException e) {
                throw ApiErrors.from(e);
            }
        }, executor);
    }
}
