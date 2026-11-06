package app.simplecloud.api.internal.group;

import app.simplecloud.api.CloudApiOptions;
import app.simplecloud.api.blueprint.CreateBlueprintRequest;
import app.simplecloud.api.cache.QueryCache;
import app.simplecloud.api.cache.QueryKey;
import app.simplecloud.api.group.CreateGroupRequest;
import app.simplecloud.api.group.DeploymentConfig;
import app.simplecloud.api.group.DeploymentHost;
import app.simplecloud.api.group.DeploymentStrategy;
import app.simplecloud.api.group.Group;
import app.simplecloud.api.group.GroupApi;
import app.simplecloud.api.group.GroupQuery;
import app.simplecloud.api.group.GroupServerType;
import app.simplecloud.api.group.GroupStartQueue;
import app.simplecloud.api.group.GroupStartQueueEntry;
import app.simplecloud.api.group.GroupStartQueueItem;
import app.simplecloud.api.group.GroupStartQueueItemStatus;
import app.simplecloud.api.group.RollingRestartStatus;
import app.simplecloud.api.group.ScalingConfig;
import app.simplecloud.api.group.ScalingMode;
import app.simplecloud.api.group.SourceConfig;
import app.simplecloud.api.group.UpdateGroupRequest;
import app.simplecloud.api.group.WorkflowsConfig;
import app.simplecloud.api.internal.blueprint.InlineBlueprintSupport;
import app.simplecloud.api.internal.create.CreateRequestDefaults;
import app.simplecloud.api.internal.util.ValidationUtil;
import app.simplecloud.api.internal.web.ApiClientFactory;
import app.simplecloud.api.internal.web.ApiExceptions;
import app.simplecloud.api.internal.workflow.WorkflowResults;
import app.simplecloud.api.web.ApiClient;
import app.simplecloud.api.web.ApiException;
import app.simplecloud.api.web.apis.BlueprintsApi;
import app.simplecloud.api.web.apis.ServerGroupsApi;
import app.simplecloud.api.web.models.ModelsCreateServerGroupRequest;
import app.simplecloud.api.web.models.ModelsCreateServerGroupResponse;
import app.simplecloud.api.web.models.ModelsDeletePropertiesRequest;
import app.simplecloud.api.web.models.ModelsDeletePropertiesResponse;
import app.simplecloud.api.web.models.ModelsDeploymentConfig;
import app.simplecloud.api.web.models.ModelsDeploymentHost;
import app.simplecloud.api.web.models.ModelsExecuteServerGroupWorkflowResponse;
import app.simplecloud.api.web.models.ModelsListServerGroupStartQueueResponse;
import app.simplecloud.api.web.models.ModelsListServerGroupsResponse;
import app.simplecloud.api.web.models.ModelsManualServerStartQueueItem;
import app.simplecloud.api.web.models.ModelsPatchPropertiesRequest;
import app.simplecloud.api.web.models.ModelsPatchPropertiesResponse;
import app.simplecloud.api.web.models.ModelsPatchServerGroupRequest;
import app.simplecloud.api.web.models.ModelsQueueServerGroupStartRequest;
import app.simplecloud.api.web.models.ModelsRollingRestartRequest;
import app.simplecloud.api.web.models.ModelsRollingRestartStatusResponse;
import app.simplecloud.api.web.models.ModelsScaleDownConfig;
import app.simplecloud.api.web.models.ModelsScalingConfig;
import app.simplecloud.api.web.models.ModelsScalingMode;
import app.simplecloud.api.web.models.ModelsServerGroupStartQueueEntry;
import app.simplecloud.api.web.models.ModelsServerGroupSummary;
import app.simplecloud.api.web.models.ModelsSourceConfig;
import app.simplecloud.api.web.models.ModelsStopAllServersResponse;
import app.simplecloud.api.web.models.ModelsUpdateServerGroupResponse;
import app.simplecloud.api.web.models.ModelsWorkflowWhen;
import app.simplecloud.api.web.models.ModelsWorkflowsConfig;
import app.simplecloud.api.workflow.WorkflowExecutionResult;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

public class GroupApiImpl implements GroupApi {
    private static final Duration TRANSIENT_MISS_RETRY_DELAY = Duration.ofMillis(200);
    private static final int TRANSIENT_MISS_MAX_RETRIES = 2;

    private final CloudApiOptions options;
    private final ServerGroupsApi serverGroupsApi;
    private final InlineBlueprintSupport inlineBlueprintSupport;
    private final CreateRequestDefaults createRequestDefaults;
    private final QueryCache cache;
    private final Executor executor;

    public GroupApiImpl(CloudApiOptions options, QueryCache cache) {
        this(options, cache, ApiClientFactory.create(options));
    }

    public GroupApiImpl(CloudApiOptions options, QueryCache cache, ApiClient httpClient) {
        this(options, cache, httpClient, ForkJoinPool.commonPool());
    }

    public GroupApiImpl(CloudApiOptions options, QueryCache cache, ApiClient httpClient, Executor executor) {
        this(
                options,
                cache,
                createServerGroupsApi(options, httpClient),
                new InlineBlueprintSupport(options, createBlueprintsApi(options, httpClient)),
                executor);
    }

    GroupApiImpl(
            CloudApiOptions options, QueryCache cache, ServerGroupsApi serverGroupsApi, BlueprintsApi blueprintsApi) {
        this(
                options,
                cache,
                serverGroupsApi,
                new InlineBlueprintSupport(options, blueprintsApi),
                ForkJoinPool.commonPool());
    }

    GroupApiImpl(
            CloudApiOptions options,
            QueryCache cache,
            ServerGroupsApi serverGroupsApi,
            BlueprintsApi blueprintsApi,
            InlineBlueprintSupport.ServerUrlResolver serverUrlResolver) {
        this(
                options,
                cache,
                serverGroupsApi,
                new InlineBlueprintSupport(options, blueprintsApi, serverUrlResolver),
                ForkJoinPool.commonPool());
    }

    private GroupApiImpl(
            CloudApiOptions options,
            QueryCache cache,
            ServerGroupsApi serverGroupsApi,
            InlineBlueprintSupport inlineBlueprintSupport,
            Executor executor) {
        this.options = options;
        this.cache = cache;
        this.serverGroupsApi = serverGroupsApi;
        this.inlineBlueprintSupport = inlineBlueprintSupport;
        this.createRequestDefaults = new CreateRequestDefaults();
        this.executor = executor;
    }

    @Override
    public CompletableFuture<GroupStartQueue> getServerStartQueue() {
        return CompletableFuture.supplyAsync(
                () -> {
                    try {
                        ModelsListServerGroupStartQueueResponse response =
                                serverGroupsApi.listServerGroupStartQueue(options.getNetworkId());

                        List<GroupStartQueueEntry> items = response.getItems() == null
                                ? List.of()
                                : response.getItems().stream()
                                        .map(this::convertStartQueueEntry)
                                        .toList();

                        return new GroupStartQueue(
                                intValue(response.getCount()),
                                intValue(response.getFailedStarts()),
                                items,
                                intValue(response.getQueuedStarts()),
                                intValue(response.getTotalStarts()));
                    } catch (ApiException e) {
                        throw ApiExceptions.from(e);
                    }
                },
                executor);
    }

    @Override
    public CompletableFuture<Void> requestServerStart(String serverGroupId) {
        return CompletableFuture.runAsync(
                () -> {
                    String normalizedServerGroupId = normalize(serverGroupId);
                    if (normalizedServerGroupId == null) {
                        throw new IllegalArgumentException("serverGroupId must not be blank");
                    }

                    try {
                        ModelsQueueServerGroupStartRequest request = new ModelsQueueServerGroupStartRequest();
                        request.setServerGroupId(normalizedServerGroupId);

                        serverGroupsApi.queueServerGroupStart(options.getNetworkId(), request);
                    } catch (ApiException e) {
                        throw ApiExceptions.from(e);
                    }
                },
                executor);
    }

    @Override
    public CompletableFuture<Void> clearServerStartQueue(String serverGroupId) {
        return CompletableFuture.runAsync(
                () -> {
                    String normalizedServerGroupId = normalize(serverGroupId);
                    if (normalizedServerGroupId == null) {
                        throw new IllegalArgumentException("serverGroupId must not be blank");
                    }

                    try {
                        serverGroupsApi.clearServerGroupStartQueue(options.getNetworkId(), normalizedServerGroupId);
                    } catch (ApiException e) {
                        throw ApiExceptions.from(e);
                    }
                },
                executor);
    }

    @Override
    public CompletableFuture<Integer> stopAllServers(String serverGroupId) {
        return CompletableFuture.supplyAsync(
                () -> {
                    String normalizedServerGroupId = requireServerGroupId(serverGroupId);

                    try {
                        ModelsStopAllServersResponse response =
                                serverGroupsApi.stopAllServers(options.getNetworkId(), normalizedServerGroupId, null);
                        return intValue(response.getServersStopped());
                    } catch (ApiException e) {
                        throw ApiExceptions.from(e);
                    }
                },
                executor);
    }

    @Override
    public CompletableFuture<Void> startRollingRestart(String serverGroupId) {
        return CompletableFuture.runAsync(
                () -> {
                    String normalizedServerGroupId = requireServerGroupId(serverGroupId);

                    try {
                        ModelsRollingRestartRequest request = new ModelsRollingRestartRequest();
                        request.setServerGroupId(normalizedServerGroupId);

                        serverGroupsApi.startRollingRestart(options.getNetworkId(), request);
                    } catch (ApiException e) {
                        throw ApiExceptions.from(e);
                    }
                },
                executor);
    }

    @Override
    public CompletableFuture<RollingRestartStatus> getRollingRestartStatus(String serverGroupId) {
        return CompletableFuture.supplyAsync(
                () -> {
                    String normalizedServerGroupId = requireServerGroupId(serverGroupId);

                    try {
                        ModelsRollingRestartStatusResponse response = serverGroupsApi.getRollingRestartStatus(
                                options.getNetworkId(), normalizedServerGroupId);
                        return new RollingRestartStatus(
                                normalizedServerGroupId,
                                Boolean.TRUE.equals(response.getActive()),
                                normalize(response.getTimestamp()));
                    } catch (ApiException e) {
                        throw ApiExceptions.from(e);
                    }
                },
                executor);
    }

    @Override
    public CompletableFuture<Void> cancelRollingRestart(String serverGroupId) {
        return CompletableFuture.runAsync(
                () -> {
                    String normalizedServerGroupId = requireServerGroupId(serverGroupId);

                    try {
                        serverGroupsApi.cancelRollingRestart(options.getNetworkId(), normalizedServerGroupId);
                    } catch (ApiException e) {
                        throw ApiExceptions.from(e);
                    }
                },
                executor);
    }

    @Override
    public CompletableFuture<WorkflowExecutionResult> executeWorkflow(String serverGroupId, String workflowId) {
        return CompletableFuture.supplyAsync(
                () -> {
                    String normalizedServerGroupId = requireServerGroupId(serverGroupId);

                    try {
                        ModelsExecuteServerGroupWorkflowResponse response = serverGroupsApi.executeServerGroupWorkflow(
                                options.getNetworkId(), normalizedServerGroupId, WorkflowResults.request(workflowId));
                        return WorkflowResults.fromServerGroup(workflowId, response);
                    } catch (ApiException e) {
                        throw ApiExceptions.from(e);
                    }
                },
                executor);
    }

    private String requireServerGroupId(String serverGroupId) {
        String normalizedServerGroupId = normalize(serverGroupId);
        if (normalizedServerGroupId == null) {
            throw new IllegalArgumentException("serverGroupId must not be blank");
        }
        return normalizedServerGroupId;
    }

    @Override
    public CompletableFuture<Group> getGroupByName(String name) {
        return findGroupByName(name)
                .thenApply(group -> group.orElseThrow(() -> ApiExceptions.notFound("Group '" + name + "' not found")));
    }

    @Override
    public CompletableFuture<Optional<Group>> findGroupByName(String name) {
        QueryKey key = QueryKey.of("group", "name", name);

        return cache.fetchWithTransientMissRecovery(
                        key,
                        () -> fetchGroupByNameFromController(name),
                        Objects::isNull,
                        TRANSIENT_MISS_RETRY_DELAY,
                        TRANSIENT_MISS_MAX_RETRIES)
                .thenApply(Optional::ofNullable);
    }

    @Override
    public CompletableFuture<Group> getGroupById(String id) {
        return findGroupById(id)
                .thenApply(group ->
                        group.orElseThrow(() -> ApiExceptions.notFound("Group with ID '" + id + "' not found")));
    }

    @Override
    public CompletableFuture<Optional<Group>> findGroupById(String id) {
        if (!ValidationUtil.isUuid(id)) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        QueryKey key = QueryKey.of("group", id);

        return cache.fetchWithTransientMissRecovery(
                        key,
                        () -> fetchGroupByIdFromController(id),
                        Objects::isNull,
                        TRANSIENT_MISS_RETRY_DELAY,
                        TRANSIENT_MISS_MAX_RETRIES)
                .thenApply(Optional::ofNullable);
    }

    @Override
    public CompletableFuture<List<Group>> getAllGroups(
            @org.jetbrains.annotations.Nullable GroupQuery requestedQuery) {
        GroupQuery query = requestedQuery == null ? null : requestedQuery.copy();
        QueryKey key = buildQueryKey(query);

        return cache.fetch(
                key,
                () -> CompletableFuture.supplyAsync(
                        () -> {
                            try {
                                ModelsListServerGroupsResponse response = executeQuery(query);
                                List<ModelsServerGroupSummary> groups = response.getServerGroups();
                                if (groups == null) {
                                    return List.of();
                                }

                                return groups.stream()
                                        .<Group>map(GroupImpl::new)
                                        .toList();
                            } catch (ApiException e) {
                                throw ApiExceptions.from(e);
                            }
                        },
                        executor));
    }

    private QueryKey buildQueryKey(@org.jetbrains.annotations.Nullable GroupQuery query) {
        if (query == null) {
            return QueryKey.of("groups");
        }
        return QueryKey.of("groups", "query", query.getType(), query.getTag(), query.getLimit());
    }

    private ModelsListServerGroupsResponse executeQuery(@org.jetbrains.annotations.Nullable GroupQuery query)
            throws ApiException {
        return executeQuery(query, null, null);
    }

    private ModelsListServerGroupsResponse executeQuery(
            @Nullable GroupQuery query, @Nullable String serverGroupId, @Nullable String name) throws ApiException {
        return serverGroupsApi.listServerGroups(
                this.options.getNetworkId(),
                serverGroupId,
                name,
                query != null && query.getType() != null ? ValidationUtil.serverTypeName(query.getType()) : null,
                query != null ? query.getTag() : null,
                null,
                null,
                null,
                query != null ? query.getLimit() : null,
                null);
    }

    private boolean groupExistsWithBlueprint(String name, @Nullable String blueprintId) {
        if (blueprintId == null) {
            return false;
        }

        try {
            ModelsListServerGroupsResponse response = executeQuery(null, null, name);
            List<ModelsServerGroupSummary> groups = response.getServerGroups();
            if (groups == null) {
                return false;
            }

            return groups.stream()
                    .anyMatch(summary -> name.equals(summary.getName())
                            && sourceReferencesBlueprint(summary.getSource(), blueprintId));
        } catch (ApiException e) {
            throw ApiExceptions.from(e);
        }
    }

    private CompletableFuture<Group> fetchGroupByNameFromController(String name) {
        return CompletableFuture.supplyAsync(
                () -> {
                    try {
                        ModelsListServerGroupsResponse response = executeQuery(null, null, name);
                        List<ModelsServerGroupSummary> groups = response.getServerGroups();
                        if (groups == null) {
                            return null;
                        }

                        return groups.stream()
                                .filter(summary -> name.equals(summary.getName()))
                                .findFirst()
                                .<Group>map(GroupImpl::new)
                                .orElse(null);
                    } catch (ApiException e) {
                        throw ApiExceptions.from(e);
                    }
                },
                executor);
    }

    private CompletableFuture<Group> fetchGroupByIdFromController(String id) {
        return CompletableFuture.supplyAsync(
                () -> {
                    try {
                        ModelsListServerGroupsResponse response = executeQuery(null, id, null);
                        List<ModelsServerGroupSummary> groups = response.getServerGroups();
                        if (groups == null) {
                            return null;
                        }

                        return groups.stream()
                                .filter(summary -> id.equals(summary.getServerGroupId()))
                                .findFirst()
                                .<Group>map(GroupImpl::new)
                                .orElse(null);
                    } catch (ApiException e) {
                        throw ApiExceptions.from(e);
                    }
                },
                executor);
    }

    @Override
    public CompletableFuture<Group> createGroup(CreateGroupRequest request) {
        return CompletableFuture.supplyAsync(
                () -> {
                    WorkflowsConfig effectiveWorkflows = createRequestDefaults.defaultWorkflows(request.getWorkflows());
                    CreateBlueprintRequest effectiveCreateBlueprint = createRequestDefaults.defaultInlineBlueprint(
                            request.getCreateBlueprint(), effectiveWorkflows);
                    GroupServerType effectiveType = createRequestDefaults.defaultType(request.getType());
                    int effectiveMaxPlayers = createRequestDefaults.defaultMaxPlayers(request.getMaxPlayers());
                    boolean effectiveActive = createRequestDefaults.defaultActive(request.getActive());
                    Map<String, Object> effectiveProperties =
                            createRequestDefaults.defaultProperties(request.getProperties());
                    List<String> effectiveTags =
                            createRequestDefaults.defaultTags(request.getName(), request.getTags());
                    DeploymentConfig effectiveDeployment =
                            createRequestDefaults.defaultDeployment(request.getDeployment());
                    ScalingConfig effectiveScaling = createRequestDefaults.defaultGroupScaling(request.getScaling());

                    inlineBlueprintSupport.validateInlineBlueprintSource(effectiveCreateBlueprint, request.getSource());

                    ModelsCreateServerGroupRequest apiRequest = new ModelsCreateServerGroupRequest();
                    apiRequest.setName(request.getName());
                    apiRequest.setType(ModelsCreateServerGroupRequest.TypeEnum.valueOf(
                            ValidationUtil.serverTypeName(effectiveType)));
                    apiRequest.setMinMemory(request.getMinMemory());
                    apiRequest.setMaxMemory(request.getMaxMemory());
                    apiRequest.setMaxPlayers(effectiveMaxPlayers);
                    apiRequest.setActive(effectiveActive);
                    apiRequest.setPriority(request.getPriority());
                    apiRequest.setProperties(effectiveProperties);
                    apiRequest.setTags(effectiveTags);
                    apiRequest.setDeployment(convertDeploymentConfig(effectiveDeployment));
                    apiRequest.setScaling(convertScalingConfig(effectiveScaling));
                    apiRequest.setWorkflows(convertWorkflowsConfig(effectiveWorkflows));

                    String createdBlueprintId;
                    try {
                        createdBlueprintId =
                                inlineBlueprintSupport.createBlueprint(request.getName(), effectiveCreateBlueprint);
                        SourceConfig effectiveSource = createdBlueprintId != null
                                ? inlineBlueprintSupport.buildInlineBlueprintSource(
                                        request.getSource(), createdBlueprintId)
                                : request.getSource();
                        if (effectiveSource != null) {
                            apiRequest.setSource(convertSourceConfig(effectiveSource));
                        }

                        ModelsCreateServerGroupResponse response;
                        try {
                            response = serverGroupsApi.createServerGroup(this.options.getNetworkId(), apiRequest);
                        } catch (ApiException e) {
                            String blueprintId = createdBlueprintId;
                            throw inlineBlueprintSupport.rollBackAfterCreateFailure(
                                    "group",
                                    blueprintId,
                                    e,
                                    () -> groupExistsWithBlueprint(request.getName(), blueprintId));
                        }

                        // Invalidate group list caches (new group created)
                        cache.invalidateAll(QueryKey.of("groups"));
                        cache.invalidateAll(QueryKey.of("group", "name"));

                        ModelsServerGroupSummary summary = new ModelsServerGroupSummary();
                        summary.setServerGroupId(response.getServerGroupId());
                        summary.setName(response.getName());
                        summary.setType(response.getType());
                        summary.setMinMemory(response.getMinMemory());
                        summary.setMaxMemory(response.getMaxMemory());
                        summary.setMaxPlayers(response.getMaxPlayers());
                        summary.setActive(response.getActive());
                        summary.setPriority(response.getPriority());
                        summary.setDeployment(response.getDeployment());
                        summary.setScaling(response.getScaling());
                        summary.setSource(response.getSource());
                        summary.setWorkflows(response.getWorkflows());
                        summary.setProperties(response.getProperties());
                        summary.setTags(response.getTags());
                        summary.setCreatedAt(response.getCreatedAt());
                        summary.setUpdatedAt(response.getUpdatedAt());

                        return new GroupImpl(summary);
                    } catch (ApiException e) {
                        throw ApiExceptions.from(e);
                    }
                },
                executor);
    }

    @Override
    public CompletableFuture<Group> updateGroup(String id, UpdateGroupRequest request) {
        return CompletableFuture.supplyAsync(
                () -> {
                    try {
                        ModelsPatchServerGroupRequest apiRequest = new ModelsPatchServerGroupRequest();
                        apiRequest.setName(request.getName());
                        if (request.getType() != null) {
                            apiRequest.setType(ModelsPatchServerGroupRequest.TypeEnum.valueOf(
                                    ValidationUtil.serverTypeName(request.getType())));
                        }
                        apiRequest.setMinMemory(request.getMinMemory());
                        apiRequest.setMaxMemory(request.getMaxMemory());
                        apiRequest.setMaxPlayers(request.getMaxPlayers());
                        apiRequest.setActive(request.getActive());
                        apiRequest.setPriority(request.getPriority());
                        apiRequest.setProperties(request.getProperties());
                        apiRequest.setTags(request.getTags());

                        if (request.getDeployment() != null) {
                            apiRequest.setDeployment(convertDeploymentConfig(request.getDeployment()));
                        }
                        if (request.getScaling() != null) {
                            apiRequest.setScaling(convertScalingConfig(request.getScaling()));
                        }
                        if (request.getSource() != null) {
                            apiRequest.setSource(convertSourceConfig(request.getSource()));
                        }
                        if (request.getWorkflows() != null) {
                            apiRequest.setWorkflows(convertWorkflowsConfig(request.getWorkflows()));
                        }

                        ModelsUpdateServerGroupResponse response =
                                serverGroupsApi.patchServerGroup(this.options.getNetworkId(), id, apiRequest);

                        ModelsServerGroupSummary summary = new ModelsServerGroupSummary();
                        summary.setServerGroupId(response.getServerGroupId());
                        summary.setName(response.getName());
                        summary.setType(response.getType());
                        summary.setMinMemory(response.getMinMemory());
                        summary.setMaxMemory(response.getMaxMemory());
                        summary.setMaxPlayers(response.getMaxPlayers());
                        summary.setActive(response.getActive());
                        summary.setPriority(response.getPriority());
                        summary.setDeployment(response.getDeployment());
                        summary.setScaling(response.getScaling());
                        summary.setSource(response.getSource());
                        summary.setWorkflows(response.getWorkflows());
                        summary.setProperties(response.getProperties());
                        summary.setTags(response.getTags());
                        summary.setCreatedAt(response.getCreatedAt());
                        summary.setUpdatedAt(response.getUpdatedAt());

                        Group group = new GroupImpl(summary);

                        // Update cache with new data and invalidate list caches
                        cache.set(QueryKey.of("group", id), group);
                        cache.invalidateAll(QueryKey.of("groups"));
                        cache.invalidateAll(QueryKey.of("group", "name"));

                        return group;
                    } catch (ApiException e) {
                        throw ApiExceptions.from(e);
                    }
                },
                executor);
    }

    private ModelsDeploymentConfig convertDeploymentConfig(DeploymentConfig config) {
        ModelsDeploymentConfig result = new ModelsDeploymentConfig();
        if (config.getStrategy() != null) {
            result.setStrategy(convertDeploymentStrategy(config.getStrategy()));
        }

        if (config.getHosts() != null) {
            for (DeploymentHost host : config.getHosts()) {
                ModelsDeploymentHost modelHost = new ModelsDeploymentHost();
                modelHost.setName(host.getName());
                modelHost.setPriority(host.getPriority());
                result.addHostsItem(modelHost);
            }
        }

        return result;
    }

    private String convertDeploymentStrategy(DeploymentStrategy strategy) {
        return switch (strategy) {
            case WHITELIST -> "whitelist";
            case BLACKLIST -> "blacklist";
        };
    }

    private ModelsScalingConfig convertScalingConfig(ScalingConfig config) {
        ModelsScalingConfig result = new ModelsScalingConfig();
        result.setAvailableSlots(config.getAvailableSlots());
        result.setMaxServers(config.getMaxServers());
        result.setMinServers(config.getMinServers());
        result.setPlayerThreshold(java.math.BigDecimal.valueOf(config.getPlayerThreshold()));
        if (config.getScalingMode() != null) {
            result.setScalingMode(ModelsScalingMode.fromValue(convertScalingMode(config.getScalingMode())));
        }

        if (config.getScaleDown() != null) {
            ModelsScaleDownConfig scaleDown = new ModelsScaleDownConfig();
            scaleDown.setIdleTime(config.getScaleDown().getIdleTime());
            scaleDown.setIgnorePlayers(config.getScaleDown().isIgnorePlayers());
            result.setScaleDown(scaleDown);
        }

        return result;
    }

    private String convertScalingMode(ScalingMode scalingMode) {
        return switch (scalingMode) {
            case SLOTS -> "SLOTS";
            case SERVERS -> "SERVERS";
        };
    }

    private ModelsSourceConfig convertSourceConfig(SourceConfig config) {
        ModelsSourceConfig result = new ModelsSourceConfig();
        if (config.getType() != null) {
            result.setType(ModelsSourceConfig.TypeEnum.valueOf(config.getType().name()));
        }
        result.setBlueprint(config.getBlueprint());
        result.setImage(config.getImage());
        return result;
    }

    private ModelsWorkflowsConfig convertWorkflowsConfig(WorkflowsConfig config) {
        ModelsWorkflowsConfig result = new ModelsWorkflowsConfig();
        result.setManual(config.getManual());

        if (config.getWhen() != null) {
            ModelsWorkflowWhen when = new ModelsWorkflowWhen();
            when.setStart(config.getWhen().getStart());
            when.setStop(config.getWhen().getStop());
            result.setWhen(when);
        }

        return result;
    }

    private boolean sourceReferencesBlueprint(@Nullable ModelsSourceConfig source, String blueprintId) {
        return source != null && blueprintId.equals(source.getBlueprint());
    }

    @Nullable
    private String findAttachedBlueprintId(String groupId) throws ApiException {
        ModelsListServerGroupsResponse response = executeQuery(null, groupId, null);
        List<ModelsServerGroupSummary> groups = response.getServerGroups();
        if (groups == null) {
            return null;
        }

        return groups.stream()
                .filter(group -> groupId.equals(group.getServerGroupId()))
                .map(ModelsServerGroupSummary::getSource)
                .map(inlineBlueprintSupport::getReferencedBlueprintId)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    @Override
    public CompletableFuture<Void> deleteGroup(String id) {
        return CompletableFuture.runAsync(
                () -> {
                    try {
                        String blueprintId = findAttachedBlueprintId(id);

                        serverGroupsApi.deleteServerGroup(this.options.getNetworkId(), id);

                        // Invalidate this group and list caches
                        cache.invalidate(QueryKey.of("group", id));
                        cache.invalidateAll(QueryKey.of("groups"));
                        cache.invalidateAll(QueryKey.of("group", "name"));

                        inlineBlueprintSupport.deleteBlueprint(blueprintId);
                    } catch (ApiException e) {
                        throw ApiExceptions.from(e);
                    }
                },
                executor);
    }

    @Override
    public CompletableFuture<Map<String, Object>> updateGroupProperties(String id, Map<String, Object> properties) {
        return CompletableFuture.supplyAsync(
                () -> {
                    try {
                        ModelsPatchPropertiesRequest request = new ModelsPatchPropertiesRequest();
                        request.setProperties(properties);

                        ModelsPatchPropertiesResponse response =
                                serverGroupsApi.patchServerGroupProperties(this.options.getNetworkId(), id, request);

                        // Invalidate group cache (properties changed)
                        cache.invalidate(QueryKey.of("group", id));
                        cache.invalidateAll(QueryKey.of("groups"));
                        cache.invalidateAll(QueryKey.of("group", "name"));

                        Map<String, Object> result = response.getProperties();
                        return result != null ? result : new HashMap<>();
                    } catch (ApiException e) {
                        throw ApiExceptions.from(e);
                    }
                },
                executor);
    }

    @Override
    public CompletableFuture<Map<String, Object>> deleteGroupProperties(String id, List<String> keys) {
        return CompletableFuture.supplyAsync(
                () -> {
                    try {
                        ModelsDeletePropertiesRequest request = new ModelsDeletePropertiesRequest();
                        request.setKeys(keys);

                        ModelsDeletePropertiesResponse response =
                                serverGroupsApi.deleteServerGroupProperties(this.options.getNetworkId(), id, request);

                        // Invalidate group cache (properties changed)
                        cache.invalidate(QueryKey.of("group", id));
                        cache.invalidateAll(QueryKey.of("groups"));
                        cache.invalidateAll(QueryKey.of("group", "name"));

                        Map<String, Object> result = response.getProperties();
                        return result != null ? result : new HashMap<>();
                    } catch (ApiException e) {
                        throw ApiExceptions.from(e);
                    }
                },
                executor);
    }

    private GroupStartQueueEntry convertStartQueueEntry(ModelsServerGroupStartQueueEntry entry) {
        List<GroupStartQueueItem> starts = entry.getStarts() == null
                ? List.of()
                : entry.getStarts().stream().map(this::convertStartQueueItem).toList();

        return new GroupStartQueueEntry(
                intValue(entry.getFailedStarts()),
                intValue(entry.getQueuedStarts()),
                entry.getServerGroupId(),
                entry.getServerGroupName(),
                starts,
                intValue(entry.getTotalStarts()));
    }

    private GroupStartQueueItem convertStartQueueItem(ModelsManualServerStartQueueItem item) {
        String statusValue = item.getStatus() == null ? null : item.getStatus().getValue();
        return new GroupStartQueueItem(
                item.getCreatedAt(),
                item.getFailureReason(),
                item.getId(),
                GroupStartQueueItemStatus.fromApiValue(statusValue));
    }

    private int intValue(Integer value) {
        return value != null ? value : 0;
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }

    private static ServerGroupsApi createServerGroupsApi(CloudApiOptions options, ApiClient httpClient) {
        ServerGroupsApi api = new ServerGroupsApi(httpClient);
        api.setCustomBaseUrl(options.getControllerUrl());
        return api;
    }

    private static BlueprintsApi createBlueprintsApi(CloudApiOptions options, ApiClient httpClient) {
        BlueprintsApi api = new BlueprintsApi(httpClient);
        api.setCustomBaseUrl(options.getControllerUrl());
        return api;
    }
}
