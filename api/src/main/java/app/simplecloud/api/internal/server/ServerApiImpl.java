package app.simplecloud.api.internal.server;

import app.simplecloud.api.CloudApiException;
import app.simplecloud.api.CloudApiOptions;
import app.simplecloud.api.cache.QueryCache;
import app.simplecloud.api.cache.QueryKey;
import app.simplecloud.api.internal.util.ValidationUtil;
import app.simplecloud.api.internal.web.ApiCalls;
import app.simplecloud.api.internal.web.ApiExceptions;
import app.simplecloud.api.internal.workflow.WorkflowResults;
import app.simplecloud.api.runtime.SimpleCloudRuntime;
import app.simplecloud.api.server.Server;
import app.simplecloud.api.server.ServerApi;
import app.simplecloud.api.server.ServerQuery;
import app.simplecloud.api.server.ServerState;
import app.simplecloud.api.server.UpdateServerRequest;
import app.simplecloud.api.web.ApiClient;
import app.simplecloud.api.web.ApiException;
import app.simplecloud.api.web.apis.ServersApi;
import app.simplecloud.api.web.models.ModelsDeletePropertiesRequest;
import app.simplecloud.api.web.models.ModelsListServersResponse;
import app.simplecloud.api.web.models.ModelsPatchPropertiesRequest;
import app.simplecloud.api.web.models.ModelsPatchServerRequest;
import app.simplecloud.api.web.models.ModelsPatchServerResponse;
import app.simplecloud.api.web.models.ModelsSendCommandRequest;
import app.simplecloud.api.web.models.ModelsServerSummary;
import app.simplecloud.api.web.models.ModelsUpdatePropertiesRequest;
import app.simplecloud.api.workflow.WorkflowExecutionResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import static app.simplecloud.api.internal.util.QueryParams.joinOrNull;
import static app.simplecloud.api.internal.util.ValidationUtil.requireNotBlank;

public class ServerApiImpl implements ServerApi {
    private static final Duration TRANSIENT_MISS_RETRY_DELAY = Duration.ofMillis(200);
    private static final int TRANSIENT_MISS_MAX_RETRIES = 2;

    private final CloudApiOptions options;
    private final ServersApi serversApi;
    private final QueryCache cache;
    private final Executor executor;

    public ServerApiImpl(CloudApiOptions options, QueryCache cache, ApiClient httpClient, Executor executor) {
        this.options = options;
        this.cache = cache;
        this.serversApi = new ServersApi(httpClient);
        this.executor = executor;
    }

    @Override
    public CompletableFuture<Server> getServerById(String id) {
        return findServerById(id)
                .thenApply(server ->
                        server.orElseThrow(() -> ApiExceptions.notFound("Server with ID '" + id + "' not found")));
    }

    @Override
    public CompletableFuture<Optional<Server>> findServerById(String requestedId) {
        if (!ValidationUtil.isUuid(requestedId)) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        String id = ValidationUtil.normalizeUuid(requestedId);
        QueryKey key = QueryKey.of("server", id);

        return cache.fetchWithTransientMissRecovery(
                        key,
                        () -> fetchServerByIdFromController(id),
                        Objects::isNull,
                        TRANSIENT_MISS_RETRY_DELAY,
                        TRANSIENT_MISS_MAX_RETRIES)
                .thenApply(Optional::ofNullable);
    }

    @Override
    public CompletableFuture<Server> getServerByNumericalId(String groupName, int numericalId) {
        return findServerByNumericalId(groupName, numericalId)
                .thenApply(server -> server.orElseThrow(
                        () -> ApiExceptions.notFound("Server '" + groupName + "-" + numericalId + "' not found")));
    }

    @Override
    public CompletableFuture<Optional<Server>> findServerByNumericalId(String groupName, int numericalId) {
        QueryKey key = QueryKey.of("server", "numerical", groupName, numericalId);

        return cache.fetchWithTransientMissRecovery(
                        key,
                        () -> fetchServerByNumericalIdFromController(groupName, numericalId),
                        Objects::isNull,
                        TRANSIENT_MISS_RETRY_DELAY,
                        TRANSIENT_MISS_MAX_RETRIES)
                .thenApply(Optional::ofNullable);
    }

    @Override
    public CompletableFuture<List<Server>> getServersByGroup(String groupName) {
        QueryKey key = QueryKey.of("servers", "group", groupName);

        return cache.fetch(
                key,
                () -> ApiCalls.supplyAsync(
                        executor,
                        () -> listServers(ServerQuery.create().filterByServerGroupName(groupName), null).stream()
                                .filter(server -> server.getServerGroupId() != null
                                        && !server.getServerGroupId().isEmpty())
                                .<Server>map(ServerImpl::new)
                                .toList()));
    }

    @Override
    public CompletableFuture<List<Server>> getAllServers(@Nullable ServerQuery requestedQuery) {
        ServerQuery query = requestedQuery == null ? null : requestedQuery.copy();
        QueryKey key = buildQueryKey(query);

        if (isExactServerLookup(query)) {
            return cache.fetchWithTransientMissRecovery(
                    key,
                    () -> fetchServersFromController(query),
                    List::isEmpty,
                    TRANSIENT_MISS_RETRY_DELAY,
                    TRANSIENT_MISS_MAX_RETRIES);
        }
        return cache.fetch(key, () -> fetchServersFromController(query));
    }

    private CompletableFuture<List<Server>> fetchServersFromController(@Nullable ServerQuery query) {
        return ApiCalls.supplyAsync(
                executor,
                () -> listServers(query, null).stream()
                        .<Server>map(ServerImpl::new)
                        .toList());
    }

    private CompletableFuture<Server> fetchServerByIdFromController(String id) {
        return ApiCalls.supplyAsync(
                executor,
                () -> listServers(null, id).stream()
                        .filter(summary -> id.equals(summary.getServerId()))
                        .findFirst()
                        .<Server>map(ServerImpl::new)
                        .orElse(null));
    }

    private CompletableFuture<Server> fetchServerByNumericalIdFromController(String groupName, int numericalId) {
        ServerQuery query =
                ServerQuery.create().filterByServerGroupName(groupName).filterByNumericalId(numericalId);

        return fetchServersFromController(query)
                .thenApply(servers -> servers.stream()
                        .filter(server -> server.getServerGroupId() != null
                                && !server.getServerGroupId().isEmpty()
                                && server.getNumericalId() == numericalId)
                        .findFirst()
                        .orElse(null));
    }

    private boolean isExactServerLookup(@Nullable ServerQuery query) {
        return query != null
                && hasExactlyOneValue(query.getServerGroupNames())
                && hasExactlyOneValue(query.getNumericalIds())
                && isNullOrEmpty(query.getServerGroupIds())
                && isNullOrEmpty(query.getStates())
                && isNullOrEmpty(query.getServerhostIds())
                && isNullOrEmpty(query.getPersistentServerIds())
                && isNullOrEmpty(query.getServerGroupTypes())
                && isNullOrEmpty(query.getServerGroupTags())
                && query.getSortBy() == null
                && query.getSortOrder() == null;
    }

    private boolean hasExactlyOneValue(@Nullable List<?> values) {
        return values != null && values.size() == 1 && values.getFirst() != null;
    }

    private boolean isNullOrEmpty(@Nullable List<?> values) {
        return values == null || values.isEmpty();
    }

    private QueryKey buildQueryKey(@Nullable ServerQuery query) {
        if (query == null) {
            return QueryKey.of("servers");
        }
        return QueryKey.of(
                "servers",
                "query",
                snapshot(query.getServerGroupIds()),
                snapshot(query.getStates()),
                snapshot(query.getServerhostIds()),
                snapshot(query.getPersistentServerIds()),
                snapshot(query.getServerGroupTypes()),
                snapshot(query.getServerGroupNames()),
                snapshot(query.getServerGroupTags()),
                snapshot(query.getNumericalIds()),
                query.getSortBy(),
                query.getSortOrder(),
                query.getLimit(),
                query.getOffset());
    }

    private static <T> List<T> snapshot(@Nullable List<T> values) {
        return values == null ? null : List.copyOf(values);
    }

    private List<ModelsServerSummary> listServers(@Nullable ServerQuery query, @Nullable String serverId)
            throws ApiException {
        ServerQuery q = query == null ? ServerQuery.create() : query;
        ModelsListServersResponse response = serversApi.listServers(
                options.getNetworkId(),
                serverId,
                joinOrNull(q.getServerGroupIds()),
                joinOrNull(q.getStates()),
                joinOrNull(q.getServerhostIds()),
                joinOrNull(q.getPersistentServerIds()),
                joinOrNull(q.getServerGroupTypes()),
                joinOrNull(q.getServerGroupNames()),
                joinOrNull(q.getServerGroupTags()),
                joinOrNull(q.getNumericalIds()),
                q.getSortBy() != null ? q.getSortBy().getValue() : null,
                q.getSortOrder() != null ? q.getSortOrder().getValue() : null,
                q.getLimit(),
                q.getOffset());
        List<ModelsServerSummary> servers = response.getServers();
        return servers == null ? List.of() : servers;
    }

    @Override
    public CompletableFuture<Void> stopServer(String id) {
        return ApiCalls.runAsync(executor, () -> {
            serversApi.stopServer(options.getNetworkId(), id);
            invalidateServers(id);
        });
    }

    @Override
    public CompletableFuture<Void> sendCommand(String id, String command) {
        return ApiCalls.runAsync(executor, () -> {
            requireNotBlank(id, "id");
            ModelsSendCommandRequest request = new ModelsSendCommandRequest();
            request.setCommand(requireNotBlank(command, "command"));
            serversApi.sendCommand(options.getNetworkId(), id, request);
        });
    }

    @Override
    public CompletableFuture<WorkflowExecutionResult> executeWorkflow(String id, String workflowId) {
        return ApiCalls.supplyAsync(
                executor,
                () -> WorkflowResults.fromServer(
                        id,
                        workflowId,
                        serversApi.executeServerWorkflow(
                                options.getNetworkId(),
                                requireNotBlank(id, "id"),
                                WorkflowResults.request(workflowId))));
    }

    @Override
    public CompletableFuture<Server> updateServer(String id, UpdateServerRequest request) {
        return ApiCalls.supplyAsync(executor, () -> {
            ModelsPatchServerResponse response =
                    serversApi.patchServer(options.getNetworkId(), id, getModelsPatchServerRequest(request));
            if (response.getServer() == null) {
                throw new CloudApiException(
                        CloudApiException.Reason.UNKNOWN, "Server update response did not contain server data");
            }

            Server server = new ServerImpl(response.getServer());
            invalidateServers(null);
            cache.set(QueryKey.of("server", id), server);
            return server;
        });
    }

    private void invalidateServers(@Nullable String id) {
        if (id != null) {
            cache.invalidate(QueryKey.of("server", id));
        }
        cache.invalidateAll(QueryKey.of("servers"));
        cache.invalidateAll(QueryKey.of("server", "numerical"));
    }

    @NotNull
    private static ModelsPatchServerRequest getModelsPatchServerRequest(UpdateServerRequest request) {
        ModelsPatchServerRequest patchRequest = new ModelsPatchServerRequest();
        patchRequest.setMaxMemory(request.getMaxMemory());
        patchRequest.setMaxPlayers(request.getMaxPlayers());
        patchRequest.setMinMemory(request.getMinMemory());
        patchRequest.setProperties(request.getProperties());
        if (request.getState() == ServerState.UNKNOWN_STATE) {
            throw new IllegalArgumentException("UNKNOWN_STATE cannot be set as server state");
        }
        if (request.getState() != null) {
            patchRequest.setState(ModelsPatchServerRequest.StateEnum.valueOf(
                    request.getState().name()));
        }
        return patchRequest;
    }

    @Override
    public CompletableFuture<Server> getCurrentServer() {
        String serverId = SimpleCloudRuntime.serverId();
        if (serverId.isEmpty()) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("SIMPLECLOUD_UNIQUE_ID environment variable is not set"));
        }
        return getServerById(serverId);
    }

    @Override
    public CompletableFuture<Map<String, Object>> setServerProperties(String id, Map<String, Object> properties) {
        return ApiCalls.supplyAsync(executor, () -> {
            ModelsUpdatePropertiesRequest request = new ModelsUpdatePropertiesRequest();
            request.setProperties(properties);
            Map<String, Object> result = serversApi
                    .updateServerProperties(options.getNetworkId(), id, request)
                    .getProperties();
            invalidateServers(id);
            return result != null ? result : new HashMap<>();
        });
    }

    @Override
    public CompletableFuture<Map<String, Object>> updateServerProperties(String id, Map<String, Object> properties) {
        return ApiCalls.supplyAsync(executor, () -> {
            ModelsPatchPropertiesRequest request = new ModelsPatchPropertiesRequest();
            request.setProperties(properties);
            Map<String, Object> result = serversApi
                    .patchServerProperties(options.getNetworkId(), id, request)
                    .getProperties();
            invalidateServers(id);
            return result != null ? result : new HashMap<>();
        });
    }

    @Override
    public CompletableFuture<Map<String, Object>> deleteServerProperties(String id, List<String> keys) {
        return ApiCalls.supplyAsync(executor, () -> {
            ModelsDeletePropertiesRequest request = new ModelsDeletePropertiesRequest();
            request.setKeys(keys);
            Map<String, Object> result = serversApi
                    .deleteServerProperties(options.getNetworkId(), id, request)
                    .getProperties();
            invalidateServers(id);
            return result != null ? result : new HashMap<>();
        });
    }
}
