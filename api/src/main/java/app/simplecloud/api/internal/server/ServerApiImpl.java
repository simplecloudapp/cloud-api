package app.simplecloud.api.internal.server;

import app.simplecloud.api.CloudApiException;
import app.simplecloud.api.cache.QueryKey;
import app.simplecloud.api.internal.ApiContext;
import app.simplecloud.api.internal.util.ValidationUtil;
import app.simplecloud.api.internal.web.ApiCalls;
import app.simplecloud.api.internal.web.ApiExceptions;
import app.simplecloud.api.server.Server;
import app.simplecloud.api.server.ServerApi;
import app.simplecloud.api.server.ServerQuery;
import app.simplecloud.api.server.UpdateServerRequest;
import app.simplecloud.api.web.ApiException;
import app.simplecloud.api.web.apis.ServersApi;
import app.simplecloud.api.web.models.ModelsDeletePropertiesRequest;
import app.simplecloud.api.web.models.ModelsExecuteManualWorkflowRequest;
import app.simplecloud.api.web.models.ModelsExecuteServerWorkflowResponse;
import app.simplecloud.api.web.models.ModelsListServersResponse;
import app.simplecloud.api.web.models.ModelsPatchPropertiesRequest;
import app.simplecloud.api.web.models.ModelsPatchServerRequest;
import app.simplecloud.api.web.models.ModelsPatchServerResponse;
import app.simplecloud.api.web.models.ModelsSendCommandRequest;
import app.simplecloud.api.web.models.ModelsServerSummary;
import app.simplecloud.api.web.models.ModelsUpdatePropertiesRequest;
import app.simplecloud.api.workflow.ServerWorkflowResult;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.jetbrains.annotations.Nullable;

public class ServerApiImpl implements ServerApi {
    private static final Logger LOGGER = Logger.getLogger(ServerApiImpl.class.getName());

    private final ApiContext context;
    private final ServersApi serversApi;

    public ServerApiImpl(ApiContext context) {
        this.context = context;
        this.serversApi = context.api(ServersApi::new);
    }

    @Override
    public CompletableFuture<Server> getServerById(String id) {
        return findServerById(id).thenApply(ApiExceptions.orNotFound("Server with ID '" + id + "' not found"));
    }

    @Override
    public CompletableFuture<Optional<Server>> findServerById(String id) {
        if (!ValidationUtil.isUuid(id)) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return context.fetchWithTransientMissRecovery(
                        QueryKey.of("server", id),
                        () -> ApiCalls.supplyAsync(
                                () -> listServers(null, id).stream()
                                        .filter(summary -> id.equals(summary.getServerId()))
                                        .findFirst()
                                        .<Server>map(ServerImpl::new)
                                        .orElse(null),
                                context.executor()),
                        Objects::isNull)
                .thenApply(Optional::ofNullable);
    }

    @Override
    public CompletableFuture<Server> getServerByNumericalId(String groupName, int numericalId) {
        return findServerByNumericalId(groupName, numericalId)
                .thenApply(ApiExceptions.orNotFound("Server '" + groupName + "-" + numericalId + "' not found"));
    }

    @Override
    public CompletableFuture<Optional<Server>> findServerByNumericalId(String groupName, int numericalId) {
        ServerQuery query =
                ServerQuery.create().filterByServerGroupName(groupName).filterByNumericalId(numericalId);

        return context.fetchWithTransientMissRecovery(
                        QueryKey.of("server", "numerical", groupName, numericalId),
                        () -> fetchServers(query)
                                .thenApply(servers -> servers.stream()
                                        .filter(server -> server.getNumericalId() == numericalId)
                                        .findFirst()
                                        .orElse(null)),
                        Objects::isNull)
                .thenApply(Optional::ofNullable);
    }

    @Override
    public CompletableFuture<List<Server>> getServersByGroup(String groupName) {
        ServerQuery query = ServerQuery.create().filterByServerGroupName(groupName);
        return context.cache().fetch(QueryKey.of("servers", "group", groupName), () -> fetchServers(query));
    }

    @Override
    public CompletableFuture<List<Server>> getAllServers(@Nullable ServerQuery query) {
        QueryKey key = buildQueryKey(query);

        CompletableFuture<List<Server>> future;
        if (isExactServerLookup(query)) {
            future = context.fetchWithTransientMissRecovery(key, () -> fetchServers(query), List::isEmpty);
        } else {
            future = context.cache().fetch(key, () -> fetchServers(query));
        }

        return future.thenApply(servers -> {
            if (debugEmptyServerResultsEnabled() && servers.isEmpty()) {
                logEmptyServerResult(query, key);
            }
            return servers;
        });
    }

    @Override
    public CompletableFuture<Server> getCurrentServer() {
        String serverId = System.getenv("SIMPLECLOUD_UNIQUE_ID");
        if (serverId == null || serverId.isEmpty()) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("SIMPLECLOUD_UNIQUE_ID environment variable is not set"));
        }
        return getServerById(serverId);
    }

    @Override
    public CompletableFuture<Void> stopServer(String id) {
        return ApiCalls.runAsync(
                () -> {
                    serversApi.stopServer(context.networkId(), id);
                    invalidate(id);
                },
                context.executor());
    }

    @Override
    public CompletableFuture<Void> sendCommand(String id, String command) {
        return ApiCalls.runAsync(
                () -> {
                    ModelsSendCommandRequest request = new ModelsSendCommandRequest();
                    request.setCommand(command);
                    serversApi.sendCommand(context.networkId(), id, request);
                },
                context.executor());
    }

    @Override
    public CompletableFuture<ServerWorkflowResult> executeWorkflow(String id, String workflowId) {
        return ApiCalls.supplyAsync(
                () -> {
                    ModelsExecuteManualWorkflowRequest request = new ModelsExecuteManualWorkflowRequest();
                    request.setWorkflowId(workflowId);
                    ModelsExecuteServerWorkflowResponse response =
                            serversApi.executeServerWorkflow(context.networkId(), id, request);
                    return new ServerWorkflowResult(
                            Objects.requireNonNullElse(response.getServerId(), id),
                            Objects.requireNonNullElse(response.getWorkflowId(), workflowId),
                            Boolean.TRUE.equals(response.getSuccess()),
                            response.getErrorMessage());
                },
                context.executor());
    }

    @Override
    public CompletableFuture<Server> updateServer(String id, UpdateServerRequest request) {
        return ApiCalls.supplyAsync(
                () -> {
                    ModelsPatchServerRequest patchRequest = new ModelsPatchServerRequest();
                    patchRequest.setMaxMemory(request.getMaxMemory());
                    patchRequest.setMaxPlayers(request.getMaxPlayers());
                    patchRequest.setMinMemory(request.getMinMemory());
                    patchRequest.setProperties(request.getProperties());
                    if (request.getState() != null) {
                        patchRequest.setState(ModelsPatchServerRequest.StateEnum.valueOf(
                                request.getState().name()));
                    }

                    ModelsPatchServerResponse response = serversApi.patchServer(context.networkId(), id, patchRequest);
                    if (response.getServer() == null) {
                        throw new CloudApiException(
                                CloudApiException.Reason.UNKNOWN, "Server update response did not contain server data");
                    }

                    Server server = new ServerImpl(response.getServer());
                    context.cache().set(QueryKey.of("server", id), server);
                    invalidateLists();
                    return server;
                },
                context.executor());
    }

    @Override
    public CompletableFuture<Map<String, Object>> updateServerProperties(String id, Map<String, Object> properties) {
        return ApiCalls.supplyAsync(
                () -> {
                    ModelsPatchPropertiesRequest request = new ModelsPatchPropertiesRequest();
                    request.setProperties(properties);
                    Map<String, Object> result = serversApi
                            .patchServerProperties(context.networkId(), id, request)
                            .getProperties();
                    invalidate(id);
                    return Objects.requireNonNullElseGet(result, HashMap::new);
                },
                context.executor());
    }

    @Override
    public CompletableFuture<Map<String, Object>> setServerProperties(String id, Map<String, Object> properties) {
        return ApiCalls.supplyAsync(
                () -> {
                    ModelsUpdatePropertiesRequest request = new ModelsUpdatePropertiesRequest();
                    request.setProperties(properties);
                    Map<String, Object> result = serversApi
                            .updateServerProperties(context.networkId(), id, request)
                            .getProperties();
                    invalidate(id);
                    return Objects.requireNonNullElseGet(result, HashMap::new);
                },
                context.executor());
    }

    @Override
    public CompletableFuture<Map<String, Object>> deleteServerProperties(String id, List<String> keys) {
        return ApiCalls.supplyAsync(
                () -> {
                    ModelsDeletePropertiesRequest request = new ModelsDeletePropertiesRequest();
                    request.setKeys(keys);
                    Map<String, Object> result = serversApi
                            .deleteServerProperties(context.networkId(), id, request)
                            .getProperties();
                    invalidate(id);
                    return Objects.requireNonNullElseGet(result, HashMap::new);
                },
                context.executor());
    }

    private CompletableFuture<List<Server>> fetchServers(@Nullable ServerQuery query) {
        return ApiCalls.supplyAsync(
                () -> listServers(query, null).stream()
                        .<Server>map(ServerImpl::new)
                        .toList(),
                context.executor());
    }

    private List<ModelsServerSummary> listServers(@Nullable ServerQuery query, @Nullable String serverId)
            throws ApiException {
        ModelsListServersResponse response = serversApi.listServers(
                context.networkId(),
                serverId,
                query != null ? join(query.getServerGroupIds()) : null,
                query != null ? join(query.getStates()) : null,
                query != null ? query.getServerhostId() : null,
                query != null ? query.getPersistentServerId() : null,
                query != null ? join(query.getServerGroupTypes()) : null,
                query != null ? join(query.getServerGroupNames()) : null,
                query != null ? join(query.getServerGroupTags()) : null,
                query != null ? join(query.getNumericalIds()) : null,
                query != null ? query.getSortBy() : null,
                query != null ? query.getSortOrder() : null,
                null,
                null);
        return Objects.requireNonNullElse(response.getServers(), List.of());
    }

    private void invalidate(String id) {
        context.cache().invalidate(QueryKey.of("server", id));
        invalidateLists();
    }

    private void invalidateLists() {
        context.cache().invalidateAll(QueryKey.of("servers"));
        context.cache().invalidateAll(QueryKey.of("server", "numerical"));
    }

    private boolean isExactServerLookup(@Nullable ServerQuery query) {
        return query != null
                && hasExactlyOneValue(query.getServerGroupNames())
                && hasExactlyOneValue(query.getNumericalIds())
                && isNullOrEmpty(query.getServerGroupIds())
                && isNullOrEmpty(query.getStates())
                && isBlank(query.getServerhostId())
                && isBlank(query.getPersistentServerId())
                && isNullOrEmpty(query.getServerGroupTypes())
                && isNullOrEmpty(query.getServerGroupTags())
                && isBlank(query.getSortBy())
                && isBlank(query.getSortOrder());
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
                query.getServerhostId(),
                query.getPersistentServerId(),
                snapshot(query.getServerGroupTypes()),
                snapshot(query.getServerGroupNames()),
                snapshot(query.getServerGroupTags()),
                snapshot(query.getNumericalIds()),
                query.getSortBy(),
                query.getSortOrder());
    }

    private void logEmptyServerResult(@Nullable ServerQuery query, QueryKey key) {
        LOGGER.warning(() -> "getAllServers returned an empty result"
                + " [runtimeServer=" + runtimeServerName()
                + ", uniqueId=" + envValue("SIMPLECLOUD_UNIQUE_ID")
                + ", group=" + envValue("SIMPLECLOUD_GROUP")
                + ", numericalId=" + envValue("SIMPLECLOUD_NUMERICAL_ID")
                + ", component="
                + (context.options().getComponent() == null
                        ? ""
                        : context.options().getComponent())
                + ", controllerUrl=" + context.options().getControllerUrl()
                + ", cacheKey=" + key
                + ", query=" + describeQuery(query)
                + "]");
    }

    private static String describeQuery(@Nullable ServerQuery query) {
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

    private static boolean debugEmptyServerResultsEnabled() {
        return switch (envValue("SIMPLECLOUD_CLOUD_API_DEBUG").toLowerCase(Locale.ROOT)) {
            case "true", "1", "yes", "on" -> true;
            default -> false;
        };
    }

    private static String runtimeServerName() {
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

    private static String envValue(String key) {
        String value = System.getenv(key);
        return value == null ? "" : value.trim();
    }

    private static @Nullable String join(@Nullable List<?> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.stream()
                .map(value -> value instanceof Enum<?> constant ? constant.name() : String.valueOf(value))
                .collect(Collectors.joining(","));
    }

    private static <T> @Nullable List<T> snapshot(@Nullable List<T> values) {
        return values == null ? null : List.copyOf(values);
    }

    private static boolean hasExactlyOneValue(@Nullable List<?> values) {
        return values != null && values.size() == 1 && values.getFirst() != null;
    }

    private static boolean isNullOrEmpty(@Nullable List<?> values) {
        return values == null || values.isEmpty();
    }

    private static boolean isBlank(@Nullable String value) {
        return value == null || value.isBlank();
    }
}
