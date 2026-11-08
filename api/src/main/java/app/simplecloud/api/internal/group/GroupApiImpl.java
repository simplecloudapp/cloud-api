package app.simplecloud.api.internal.group;

import app.simplecloud.api.CloudApiOptions;
import app.simplecloud.api.blueprint.CreateBlueprintRequest;
import app.simplecloud.api.cache.EntityType;
import app.simplecloud.api.cache.QueryCache;
import app.simplecloud.api.cache.QueryKey;
import app.simplecloud.api.group.CreateGroupRequest;
import app.simplecloud.api.group.Group;
import app.simplecloud.api.group.GroupApi;
import app.simplecloud.api.group.GroupQuery;
import app.simplecloud.api.group.GroupServerType;
import app.simplecloud.api.group.GroupStartQueue;
import app.simplecloud.api.group.GroupStartQueueEntry;
import app.simplecloud.api.group.GroupStartQueueItem;
import app.simplecloud.api.group.GroupStartQueueItemStatus;
import app.simplecloud.api.group.RollingRestartStatus;
import app.simplecloud.api.group.SourceConfig;
import app.simplecloud.api.group.UpdateGroupRequest;
import app.simplecloud.api.group.WorkflowsConfig;
import app.simplecloud.api.internal.blueprint.BlueprintProvisioner;
import app.simplecloud.api.internal.create.CreateRequestDefaults;
import app.simplecloud.api.internal.persistentserver.PersistentServerImpl;
import app.simplecloud.api.internal.util.ValidationUtil;
import app.simplecloud.api.internal.web.ApiCalls;
import app.simplecloud.api.internal.web.ApiExceptions;
import app.simplecloud.api.internal.workflow.WorkflowResults;
import app.simplecloud.api.persistentserver.PersistentServer;
import app.simplecloud.api.web.ApiClient;
import app.simplecloud.api.web.ApiException;
import app.simplecloud.api.web.apis.BlueprintsApi;
import app.simplecloud.api.web.apis.ServerGroupsApi;
import app.simplecloud.api.web.models.ModelsConvertGroupToPersistentRequest;
import app.simplecloud.api.web.models.ModelsCreateServerGroupRequest;
import app.simplecloud.api.web.models.ModelsCreateServerGroupResponse;
import app.simplecloud.api.web.models.ModelsDeletePropertiesRequest;
import app.simplecloud.api.web.models.ModelsListServerGroupStartQueueResponse;
import app.simplecloud.api.web.models.ModelsListServerGroupsResponse;
import app.simplecloud.api.web.models.ModelsManualServerStartQueueItem;
import app.simplecloud.api.web.models.ModelsPatchPropertiesRequest;
import app.simplecloud.api.web.models.ModelsPatchServerGroupRequest;
import app.simplecloud.api.web.models.ModelsQueueServerGroupStartRequest;
import app.simplecloud.api.web.models.ModelsRollingRestartRequest;
import app.simplecloud.api.web.models.ModelsRollingRestartStatusResponse;
import app.simplecloud.api.web.models.ModelsServerGroupStartQueueEntry;
import app.simplecloud.api.web.models.ModelsServerGroupSummary;
import app.simplecloud.api.web.models.ModelsSourceConfig;
import app.simplecloud.api.web.models.ModelsUpdatePropertiesRequest;
import app.simplecloud.api.workflow.WorkflowExecutionResult;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Predicate;

import static app.simplecloud.api.internal.util.RestModelMapper.orZero;
import static app.simplecloud.api.internal.util.RestModelMapper.toModel;
import static app.simplecloud.api.internal.util.RestModelMapper.toSummary;
import static app.simplecloud.api.internal.util.ValidationUtil.requireNotBlank;

public class GroupApiImpl implements GroupApi {
    private static final Duration TRANSIENT_MISS_RETRY_DELAY = Duration.ofMillis(200);
    private static final int TRANSIENT_MISS_MAX_RETRIES = 2;

    private final CloudApiOptions options;
    private final ServerGroupsApi serverGroupsApi;
    private final BlueprintProvisioner blueprintProvisioner;
    private final CreateRequestDefaults createRequestDefaults;
    private final QueryCache cache;
    private final Executor executor;

    public GroupApiImpl(CloudApiOptions options, QueryCache cache, ApiClient httpClient, Executor executor) {
        this.options = options;
        this.cache = cache;
        this.serverGroupsApi = new ServerGroupsApi(httpClient);
        this.blueprintProvisioner = new BlueprintProvisioner(options, cache, new BlueprintsApi(httpClient));
        this.createRequestDefaults = new CreateRequestDefaults();
        this.executor = executor;
    }

    @Override
    public CompletableFuture<GroupStartQueue> getServerStartQueue() {
        return ApiCalls.supplyAsync(executor, () -> {
            ModelsListServerGroupStartQueueResponse response =
                    serverGroupsApi.listServerGroupStartQueue(options.getNetworkId());
            List<GroupStartQueueEntry> items = response.getItems() == null
                    ? List.of()
                    : response.getItems().stream()
                            .map(this::convertStartQueueEntry)
                            .toList();

            return new GroupStartQueue(
                    orZero(response.getCount()),
                    orZero(response.getFailedStarts()),
                    items,
                    orZero(response.getQueuedStarts()),
                    orZero(response.getTotalStarts()));
        });
    }

    @Override
    public CompletableFuture<Void> requestServerStart(String serverGroupId) {
        return ApiCalls.runAsync(executor, () -> {
            ModelsQueueServerGroupStartRequest request = new ModelsQueueServerGroupStartRequest();
            request.setServerGroupId(requireNotBlank(serverGroupId, "serverGroupId"));
            serverGroupsApi.queueServerGroupStart(options.getNetworkId(), request);
        });
    }

    @Override
    public CompletableFuture<Void> clearServerStartQueue(String serverGroupId) {
        return ApiCalls.runAsync(
                executor,
                () -> serverGroupsApi.clearServerGroupStartQueue(
                        options.getNetworkId(), requireNotBlank(serverGroupId, "serverGroupId")));
    }

    @Override
    public CompletableFuture<Integer> stopAllServers(String serverGroupId) {
        return ApiCalls.supplyAsync(
                executor,
                () -> orZero(serverGroupsApi
                        .stopAllServers(options.getNetworkId(), requireNotBlank(serverGroupId, "serverGroupId"), null)
                        .getServersStopped()));
    }

    @Override
    public CompletableFuture<Void> startRollingRestart(String serverGroupId) {
        return ApiCalls.runAsync(executor, () -> {
            ModelsRollingRestartRequest request = new ModelsRollingRestartRequest();
            request.setServerGroupId(requireNotBlank(serverGroupId, "serverGroupId"));
            serverGroupsApi.startRollingRestart(options.getNetworkId(), request);
        });
    }

    @Override
    public CompletableFuture<RollingRestartStatus> getRollingRestartStatus(String serverGroupId) {
        return ApiCalls.supplyAsync(executor, () -> {
            String id = requireNotBlank(serverGroupId, "serverGroupId");
            ModelsRollingRestartStatusResponse response =
                    serverGroupsApi.getRollingRestartStatus(options.getNetworkId(), id);
            String timestamp = response.getTimestamp();
            return new RollingRestartStatus(
                    id,
                    Boolean.TRUE.equals(response.getActive()),
                    timestamp == null || timestamp.isBlank() ? null : timestamp);
        });
    }

    @Override
    public CompletableFuture<Void> cancelRollingRestart(String serverGroupId) {
        return ApiCalls.runAsync(
                executor,
                () -> serverGroupsApi.cancelRollingRestart(
                        options.getNetworkId(), requireNotBlank(serverGroupId, "serverGroupId")));
    }

    @Override
    public CompletableFuture<WorkflowExecutionResult> executeWorkflow(String serverGroupId, String workflowId) {
        return ApiCalls.supplyAsync(
                executor,
                () -> WorkflowResults.fromServerGroup(
                        workflowId,
                        serverGroupsApi.executeServerGroupWorkflow(
                                options.getNetworkId(),
                                requireNotBlank(serverGroupId, "serverGroupId"),
                                WorkflowResults.request(workflowId))));
    }

    @Override
    public CompletableFuture<Group> getGroupByName(String name) {
        return findGroupByName(name)
                .thenApply(group -> group.orElseThrow(() -> ApiExceptions.notFound("Group '" + name + "' not found")));
    }

    @Override
    public CompletableFuture<Optional<Group>> findGroupByName(String name) {
        return cache.fetchWithTransientMissRecovery(
                        QueryKey.of("group", "name", name),
                        () -> fetchSingleGroup(null, name, summary -> name.equals(summary.getName())),
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
    public CompletableFuture<Optional<Group>> findGroupById(String requestedId) {
        if (!ValidationUtil.isUuid(requestedId)) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        String id = ValidationUtil.normalizeUuid(requestedId);

        return cache.fetchWithTransientMissRecovery(
                        QueryKey.of("group", id),
                        () -> fetchSingleGroup(id, null, summary -> id.equals(summary.getServerGroupId())),
                        Objects::isNull,
                        TRANSIENT_MISS_RETRY_DELAY,
                        TRANSIENT_MISS_MAX_RETRIES)
                .thenApply(Optional::ofNullable);
    }

    @Override
    public CompletableFuture<List<Group>> getAllGroups(@Nullable GroupQuery requestedQuery) {
        GroupQuery query = requestedQuery == null ? null : requestedQuery.copy();

        return cache.fetch(
                buildQueryKey(query),
                () -> ApiCalls.supplyAsync(
                        executor,
                        () -> listGroups(query, null, null).stream()
                                .<Group>map(GroupImpl::new)
                                .toList()));
    }

    private QueryKey buildQueryKey(@Nullable GroupQuery query) {
        if (query == null) {
            return QueryKey.of("groups");
        }
        return QueryKey.of(
                "groups",
                "query",
                query.getType(),
                query.getTag(),
                query.getActive(),
                query.getSourceType(),
                query.getBlueprintId(),
                query.getLimit(),
                query.getOffset());
    }

    private List<ModelsServerGroupSummary> listGroups(
            @Nullable GroupQuery query, @Nullable String serverGroupId, @Nullable String name) throws ApiException {
        ModelsListServerGroupsResponse response = serverGroupsApi.listServerGroups(
                options.getNetworkId(),
                serverGroupId,
                name,
                query != null && query.getType() != null ? ValidationUtil.serverTypeName(query.getType()) : null,
                query != null ? query.getTag() : null,
                query != null ? query.getActive() : null,
                query != null && query.getSourceType() != null
                        ? query.getSourceType().name().toLowerCase(Locale.ROOT)
                        : null,
                query != null ? query.getBlueprintId() : null,
                query != null ? query.getLimit() : null,
                query != null ? query.getOffset() : null);
        List<ModelsServerGroupSummary> groups = response.getServerGroups();
        return groups == null ? List.of() : groups;
    }

    private CompletableFuture<Group> fetchSingleGroup(
            @Nullable String id, @Nullable String name, Predicate<ModelsServerGroupSummary> filter) {
        return ApiCalls.supplyAsync(
                executor,
                () -> listGroups(null, id, name).stream()
                        .filter(filter)
                        .findFirst()
                        .<Group>map(GroupImpl::new)
                        .orElse(null));
    }

    private boolean groupExistsWithBlueprint(String name, @Nullable String blueprintId) {
        if (blueprintId == null) {
            return false;
        }
        return ApiCalls.execute(() -> listGroups(null, null, name).stream()
                .anyMatch(summary ->
                        name.equals(summary.getName()) && sourceReferencesBlueprint(summary.getSource(), blueprintId)));
    }

    @Override
    public CompletableFuture<Group> createGroup(CreateGroupRequest request) {
        return ApiCalls.supplyAsync(executor, () -> {
            WorkflowsConfig effectiveWorkflows = createRequestDefaults.defaultWorkflows(request.getWorkflows());
            CreateBlueprintRequest effectiveCreateBlueprint =
                    createRequestDefaults.defaultInlineBlueprint(request.getCreateBlueprint(), effectiveWorkflows);
            GroupServerType effectiveType = createRequestDefaults.defaultType(request.getType());

            blueprintProvisioner.validateInlineBlueprintSource(effectiveCreateBlueprint, request.getSource());

            ModelsCreateServerGroupRequest apiRequest = new ModelsCreateServerGroupRequest();
            apiRequest.setName(request.getName());
            apiRequest.setType(
                    ModelsCreateServerGroupRequest.TypeEnum.valueOf(ValidationUtil.serverTypeName(effectiveType)));
            apiRequest.setMinMemory(request.getMinMemory());
            apiRequest.setMaxMemory(request.getMaxMemory());
            apiRequest.setMaxPlayers(createRequestDefaults.defaultMaxPlayers(request.getMaxPlayers()));
            apiRequest.setActive(createRequestDefaults.defaultActive(request.getActive()));
            apiRequest.setPriority(request.getPriority());
            apiRequest.setProperties(createRequestDefaults.defaultProperties(request.getProperties()));
            apiRequest.setTags(createRequestDefaults.defaultTags(request.getName(), request.getTags()));
            apiRequest.setDeployment(toModel(createRequestDefaults.defaultDeployment(request.getDeployment())));
            apiRequest.setScaling(toModel(createRequestDefaults.defaultGroupScaling(request.getScaling())));
            apiRequest.setWorkflows(toModel(effectiveWorkflows));

            String blueprintId = blueprintProvisioner.createBlueprint(request.getName(), effectiveCreateBlueprint);
            SourceConfig effectiveSource = blueprintId != null
                    ? blueprintProvisioner.buildInlineBlueprintSource(request.getSource(), blueprintId)
                    : request.getSource();
            if (effectiveSource != null) {
                apiRequest.setSource(toModel(effectiveSource));
            }

            ModelsCreateServerGroupResponse response;
            try {
                response = serverGroupsApi.createServerGroup(options.getNetworkId(), apiRequest);
            } catch (ApiException e) {
                throw blueprintProvisioner.rollBackAfterCreateFailure(
                        "group", blueprintId, e, () -> groupExistsWithBlueprint(request.getName(), blueprintId));
            }

            invalidateGroups(null);
            return new GroupImpl(toSummary(response));
        });
    }

    @Override
    public CompletableFuture<Group> updateGroup(String id, UpdateGroupRequest request) {
        return ApiCalls.supplyAsync(executor, () -> {
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
                apiRequest.setDeployment(toModel(request.getDeployment()));
            }
            if (request.getScaling() != null) {
                ValidationUtil.requireCompleteScaling(request.getScaling());
                apiRequest.setScaling(toModel(request.getScaling()));
            }
            if (request.getSource() != null) {
                apiRequest.setSource(toModel(request.getSource()));
            }
            if (request.getWorkflows() != null) {
                apiRequest.setWorkflows(toModel(request.getWorkflows()));
            }

            Group group =
                    new GroupImpl(toSummary(serverGroupsApi.patchServerGroup(options.getNetworkId(), id, apiRequest)));

            invalidateGroupAndServers(id);
            return group;
        });
    }

    private boolean sourceReferencesBlueprint(@Nullable ModelsSourceConfig source, String blueprintId) {
        return source != null && blueprintId.equals(source.getBlueprint());
    }

    @Nullable
    private String findAttachedBlueprintId(String groupId) throws ApiException {
        return listGroups(null, groupId, null).stream()
                .filter(group -> groupId.equalsIgnoreCase(group.getServerGroupId()))
                .map(ModelsServerGroupSummary::getSource)
                .map(blueprintProvisioner::getReferencedBlueprintId)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    @Override
    public CompletableFuture<Void> deleteGroup(String id) {
        return ApiCalls.runAsync(executor, () -> {
            String blueprintId = findAttachedBlueprintId(id);
            serverGroupsApi.deleteServerGroup(options.getNetworkId(), id);
            invalidateGroupAndServers(id);
            blueprintProvisioner.deleteBlueprint(blueprintId);
        });
    }

    @Override
    public CompletableFuture<PersistentServer> convertToPersistentServer(String serverGroupId, String serverhostId) {
        return ApiCalls.supplyAsync(executor, () -> {
            String id = requireNotBlank(serverGroupId, "serverGroupId");
            ModelsConvertGroupToPersistentRequest request = new ModelsConvertGroupToPersistentRequest();
            request.setServerhostId(serverhostId);

            PersistentServer persistentServer = new PersistentServerImpl(
                    toSummary(serverGroupsApi.convertGroupToPersistent(options.getNetworkId(), id, request)));

            invalidateGroups(id);
            cache.invalidateAll(QueryKey.of("persistentServers"));
            cache.invalidateAll(QueryKey.of("persistentServer", "name"));
            return persistentServer;
        });
    }

    @Override
    public CompletableFuture<Map<String, Object>> setGroupProperties(String id, Map<String, Object> properties) {
        return ApiCalls.supplyAsync(executor, () -> {
            ModelsUpdatePropertiesRequest request = new ModelsUpdatePropertiesRequest();
            request.setProperties(properties);
            Map<String, Object> result = serverGroupsApi
                    .updateServerGroupProperties(options.getNetworkId(), id, request)
                    .getProperties();
            invalidateGroupAndServers(id);
            return result != null ? result : new HashMap<>();
        });
    }

    @Override
    public CompletableFuture<Map<String, Object>> updateGroupProperties(String id, Map<String, Object> properties) {
        return ApiCalls.supplyAsync(executor, () -> {
            ModelsPatchPropertiesRequest request = new ModelsPatchPropertiesRequest();
            request.setProperties(properties);
            Map<String, Object> result = serverGroupsApi
                    .patchServerGroupProperties(options.getNetworkId(), id, request)
                    .getProperties();
            invalidateGroupAndServers(id);
            return result != null ? result : new HashMap<>();
        });
    }

    @Override
    public CompletableFuture<Map<String, Object>> deleteGroupProperties(String id, List<String> keys) {
        return ApiCalls.supplyAsync(executor, () -> {
            ModelsDeletePropertiesRequest request = new ModelsDeletePropertiesRequest();
            request.setKeys(keys);
            Map<String, Object> result = serverGroupsApi
                    .deleteServerGroupProperties(options.getNetworkId(), id, request)
                    .getProperties();
            invalidateGroupAndServers(id);
            return result != null ? result : new HashMap<>();
        });
    }

    private void invalidateGroupAndServers(String id) {
        invalidateGroups(id);
        cache.invalidateByType(EntityType.SERVER);
    }

    private void invalidateGroups(@Nullable String id) {
        if (id != null) {
            cache.invalidate(QueryKey.of("group", id));
        }
        cache.invalidateAll(QueryKey.of("groups"));
        cache.invalidateAll(QueryKey.of("group", "name"));
    }

    private GroupStartQueueEntry convertStartQueueEntry(ModelsServerGroupStartQueueEntry entry) {
        List<GroupStartQueueItem> starts = entry.getStarts() == null
                ? List.of()
                : entry.getStarts().stream().map(this::convertStartQueueItem).toList();

        return new GroupStartQueueEntry(
                orZero(entry.getFailedStarts()),
                orZero(entry.getQueuedStarts()),
                entry.getServerGroupId(),
                entry.getServerGroupName(),
                starts,
                orZero(entry.getTotalStarts()));
    }

    private GroupStartQueueItem convertStartQueueItem(ModelsManualServerStartQueueItem item) {
        String statusValue = item.getStatus() == null ? null : item.getStatus().getValue();
        return new GroupStartQueueItem(
                item.getCreatedAt(),
                item.getFailureReason(),
                item.getId(),
                GroupStartQueueItemStatus.fromApiValue(statusValue));
    }
}
