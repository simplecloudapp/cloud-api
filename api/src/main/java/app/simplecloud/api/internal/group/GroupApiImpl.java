package app.simplecloud.api.internal.group;

import app.simplecloud.api.blueprint.CreateBlueprintRequest;
import app.simplecloud.api.cache.QueryKey;
import app.simplecloud.api.group.*;
import app.simplecloud.api.internal.ApiContext;
import app.simplecloud.api.internal.blueprint.InlineBlueprintSupport;
import app.simplecloud.api.internal.create.CreateRequestDefaults;
import app.simplecloud.api.internal.update.ConfigMerger;
import app.simplecloud.api.internal.persistentserver.PersistentServerImpl;
import app.simplecloud.api.internal.workflow.WorkflowResults;
import app.simplecloud.api.persistentserver.PersistentServer;
import app.simplecloud.api.workflow.WorkflowExecutionResult;
import app.simplecloud.api.internal.util.ModelConversionUtil;
import app.simplecloud.api.internal.util.ValidationUtil;
import app.simplecloud.api.internal.web.ApiExceptions;
import app.simplecloud.api.internal.web.ApiCalls;
import app.simplecloud.api.web.ApiException;
import app.simplecloud.api.web.apis.BlueprintsApi;
import app.simplecloud.api.web.apis.ServerGroupsApi;
import app.simplecloud.api.web.models.*;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

public class GroupApiImpl implements GroupApi {
    private final ApiContext context;
    private final ServerGroupsApi serverGroupsApi;
    private final InlineBlueprintSupport inlineBlueprintSupport;
    private final CreateRequestDefaults createRequestDefaults = new CreateRequestDefaults();

    public GroupApiImpl(ApiContext context) {
        this(context, context.api(ServerGroupsApi::new), new InlineBlueprintSupport(context.options(), context.api(BlueprintsApi::new)));
    }

    GroupApiImpl(ApiContext context, ServerGroupsApi serverGroupsApi, InlineBlueprintSupport inlineBlueprintSupport) {
        this.context = context;
        this.serverGroupsApi = serverGroupsApi;
        this.inlineBlueprintSupport = inlineBlueprintSupport;
    }

    @Override
    public CompletableFuture<GroupStartQueue> getServerStartQueue() {
        return ApiCalls.supplyAsync(() -> {
            ModelsListServerGroupStartQueueResponse response = serverGroupsApi.listServerGroupStartQueue(context.networkId());
            List<GroupStartQueueEntry> items = Objects.requireNonNullElse(response.getItems(), List.<ModelsServerGroupStartQueueEntry>of())
                    .stream()
                    .map(this::convertStartQueueEntry)
                    .toList();

            return new GroupStartQueue(
                    intValue(response.getCount()),
                    intValue(response.getFailedStarts()),
                    items,
                    intValue(response.getQueuedStarts()),
                    intValue(response.getTotalStarts())
            );
        }, context.executor());
    }

    @Override
    public CompletableFuture<Void> requestServerStart(String serverGroupId) {
        return ApiCalls.runAsync(() -> {
            ModelsQueueServerGroupStartRequest request = new ModelsQueueServerGroupStartRequest();
            request.setServerGroupId(requireServerGroupId(serverGroupId));
            serverGroupsApi.queueServerGroupStart(context.networkId(), request);
        }, context.executor());
    }

    @Override
    public CompletableFuture<Void> clearServerStartQueue(String serverGroupId) {
        return ApiCalls.runAsync(
                () -> serverGroupsApi.clearServerGroupStartQueue(context.networkId(), requireServerGroupId(serverGroupId)),
                context.executor()
        );
    }

    @Override
    public CompletableFuture<Group> getGroupByName(String name) {
        return findGroupByName(name).thenApply(ApiExceptions.orNotFound("Group '" + name + "' not found"));
    }

    @Override
    public CompletableFuture<Optional<Group>> findGroupByName(String name) {
        return findFirst(QueryKey.of("group", "name", name), null, name, summary -> name.equals(summary.getName()));
    }

    @Override
    public CompletableFuture<Group> getGroupById(String id) {
        return findGroupById(id).thenApply(ApiExceptions.orNotFound("Group with ID '" + id + "' not found"));
    }

    @Override
    public CompletableFuture<Optional<Group>> findGroupById(String id) {
        if (!ValidationUtil.isUuid(id)) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return findFirst(QueryKey.of("group", id), id, null, summary -> id.equals(summary.getServerGroupId()));
    }

    @Override
    public CompletableFuture<List<Group>> getAllGroups(@Nullable GroupQuery query) {
        return context.cache().fetch(buildQueryKey(query), () -> ApiCalls.supplyAsync(
                () -> listGroups(query, null, null).stream().<Group>map(GroupImpl::from).toList(),
                context.executor()
        ));
    }

    @Override
    public CompletableFuture<Group> createGroup(CreateGroupRequest request) {
        return ApiCalls.supplyAsync(() -> {
            WorkflowsConfig workflows = createRequestDefaults.defaultWorkflows(request.getWorkflows());
            CreateBlueprintRequest createBlueprint = createRequestDefaults.defaultInlineBlueprint(request.getCreateBlueprint(), workflows);
            GroupServerType type = createRequestDefaults.defaultType(request.getType());

            ModelsCreateServerGroupRequest apiRequest = new ModelsCreateServerGroupRequest();
            apiRequest.setName(request.getName());
            apiRequest.setType(ModelsCreateServerGroupRequest.TypeEnum.valueOf(type.name()));
            apiRequest.setMinMemory(request.getMinMemory());
            apiRequest.setMaxMemory(request.getMaxMemory());
            apiRequest.setMaxPlayers(createRequestDefaults.defaultMaxPlayers(request.getMaxPlayers()));
            apiRequest.setActive(createRequestDefaults.defaultActive(request.getActive()));
            apiRequest.setPriority(request.getPriority());
            apiRequest.setProperties(createRequestDefaults.defaultProperties(request.getProperties()));
            apiRequest.setTags(createRequestDefaults.defaultTags(request.getName(), request.getTags()));
            apiRequest.setDeployment(ModelConversionUtil.toModel(createRequestDefaults.defaultDeployment(request.getDeployment())));
            apiRequest.setScaling(ModelConversionUtil.toModel(createRequestDefaults.defaultGroupScaling(request.getScaling())));
            apiRequest.setWorkflows(ModelConversionUtil.toModel(workflows));

            inlineBlueprintSupport.validateInlineBlueprintSource(createBlueprint, request.getSource());
            String blueprintId = inlineBlueprintSupport.createBlueprint(request.getName(), createBlueprint);
            if (blueprintId != null) {
                InlineBlueprintSupport.invalidateCache(context.cache(), null);
            }
            apiRequest.setSource(ModelConversionUtil.toModel(inlineBlueprintSupport.resolveSource(request.getSource(), blueprintId)));

            ModelsCreateServerGroupResponse response;
            try {
                response = serverGroupsApi.createServerGroup(context.networkId(), apiRequest);
            } catch (ApiException e) {
                throw inlineBlueprintSupport.rollBackAfterCreateFailure(blueprintId, e);
            }

            invalidateLists();
            return GroupImpl.from(response);
        }, context.executor());
    }

    @Override
    public CompletableFuture<Group> updateGroup(String id, UpdateGroupRequest request) {
        return ApiCalls.supplyAsync(() -> {
            ModelsPatchServerGroupRequest apiRequest = new ModelsPatchServerGroupRequest();
            apiRequest.setName(request.getName());
            if (request.getType() != null) {
                apiRequest.setType(ModelsPatchServerGroupRequest.TypeEnum.valueOf(request.getType().name()));
            }
            apiRequest.setMinMemory(request.getMinMemory());
            apiRequest.setMaxMemory(request.getMaxMemory());
            apiRequest.setMaxPlayers(request.getMaxPlayers());
            apiRequest.setActive(request.getActive());
            apiRequest.setPriority(request.getPriority());
            apiRequest.setProperties(request.getProperties());
            apiRequest.setTags(request.getTags());
            Group current = request.getDeployment() != null || request.getScaling() != null || request.getWorkflows() != null
                    ? findCurrentGroup(id)
                    : null;
            apiRequest.setDeployment(ModelConversionUtil.toModel(
                    ConfigMerger.merge(current != null ? current.getDeployment() : null, request.getDeployment())
            ));
            ScalingConfig scaling = ConfigMerger.merge(current != null ? current.getScaling() : null, request.getScaling());
            if (scaling != null) {
                apiRequest.setScaling(ModelConversionUtil.toModel(createRequestDefaults.defaultGroupScaling(scaling)));
            }
            apiRequest.setSource(ModelConversionUtil.toModel(request.getSource()));
            apiRequest.setWorkflows(ModelConversionUtil.toModel(
                    ConfigMerger.merge(current != null ? current.getWorkflows() : null, request.getWorkflows())
            ));

            Group group = GroupImpl.from(serverGroupsApi.patchServerGroup(context.networkId(), id, apiRequest));
            context.cache().set(QueryKey.of("group", id), group);
            invalidateLists();
            invalidateServers();
            return group;
        }, context.executor());
    }

    @Override
    public CompletableFuture<Group> replaceGroup(String id, CreateGroupRequest request) {
        return ApiCalls.supplyAsync(() -> {
            if (request.getCreateBlueprint() != null) {
                throw new IllegalArgumentException("createBlueprint is not supported when replacing a group");
            }
            GroupServerType type = createRequestDefaults.defaultType(request.getType());

            ModelsUpdateServerGroupRequest apiRequest = new ModelsUpdateServerGroupRequest();
            apiRequest.setName(request.getName());
            apiRequest.setType(ModelsUpdateServerGroupRequest.TypeEnum.valueOf(type.name()));
            apiRequest.setMinMemory(request.getMinMemory());
            apiRequest.setMaxMemory(request.getMaxMemory());
            apiRequest.setMaxPlayers(createRequestDefaults.defaultMaxPlayers(request.getMaxPlayers()));
            apiRequest.setActive(createRequestDefaults.defaultActive(request.getActive()));
            apiRequest.setPriority(request.getPriority());
            apiRequest.setProperties(createRequestDefaults.defaultProperties(request.getProperties()));
            apiRequest.setTags(createRequestDefaults.defaultTags(request.getName(), request.getTags()));
            apiRequest.setDeployment(ModelConversionUtil.toModel(createRequestDefaults.defaultDeployment(request.getDeployment())));
            apiRequest.setScaling(ModelConversionUtil.toModel(createRequestDefaults.defaultGroupScaling(request.getScaling())));
            apiRequest.setWorkflows(ModelConversionUtil.toModel(createRequestDefaults.defaultWorkflows(request.getWorkflows())));
            apiRequest.setSource(ModelConversionUtil.toModel(request.getSource()));

            Group group = GroupImpl.from(serverGroupsApi.updateServerGroup(context.networkId(), id, apiRequest));
            context.cache().set(QueryKey.of("group", id), group);
            invalidateLists();
            invalidateServers();
            return group;
        }, context.executor());
    }

    @Override
    public CompletableFuture<Void> deleteGroup(String id) {
        return ApiCalls.runAsync(() -> {
            String blueprintId = findAttachedBlueprintId(id);
            serverGroupsApi.deleteServerGroup(context.networkId(), id);
            invalidate(id);

            inlineBlueprintSupport.deleteBlueprintOfDeletedResource(blueprintId);
            InlineBlueprintSupport.invalidateCache(context.cache(), blueprintId);
        }, context.executor());
    }

    @Override
    public CompletableFuture<Map<String, Object>> updateGroupProperties(String id, Map<String, Object> properties) {
        return ApiCalls.supplyAsync(() -> {
            ModelsPatchPropertiesRequest request = new ModelsPatchPropertiesRequest();
            request.setProperties(properties);
            ModelsPatchPropertiesResponse response = serverGroupsApi.patchServerGroupProperties(context.networkId(), id, request);
            invalidate(id);
            return Objects.requireNonNullElseGet(response.getProperties(), HashMap::new);
        }, context.executor());
    }

    @Override
    public CompletableFuture<Map<String, Object>> setGroupProperties(String id, Map<String, Object> properties) {
        return ApiCalls.supplyAsync(() -> {
            ModelsUpdatePropertiesRequest request = new ModelsUpdatePropertiesRequest();
            request.setProperties(properties);
            ModelsUpdatePropertiesResponse response = serverGroupsApi.updateServerGroupProperties(context.networkId(), id, request);
            invalidate(id);
            return Objects.requireNonNullElseGet(response.getProperties(), HashMap::new);
        }, context.executor());
    }

    @Override
    public CompletableFuture<Void> startRollingRestart(String id) {
        return ApiCalls.runAsync(() -> {
            ModelsRollingRestartRequest request = new ModelsRollingRestartRequest();
            request.setServerGroupId(id);
            serverGroupsApi.startRollingRestart(context.networkId(), request);
        }, context.executor());
    }

    @Override
    public CompletableFuture<RollingRestartStatus> getRollingRestartStatus(String id) {
        return ApiCalls.supplyAsync(() -> {
            ModelsRollingRestartStatusResponse response = serverGroupsApi.getRollingRestartStatus(context.networkId(), id);
            String timestamp = response.getTimestamp();
            return new RollingRestartStatus(
                    Objects.requireNonNullElse(response.getGroupId(), id),
                    Boolean.TRUE.equals(response.getActive()),
                    timestamp == null || timestamp.isEmpty() ? null : timestamp
            );
        }, context.executor());
    }

    @Override
    public CompletableFuture<Void> cancelRollingRestart(String id) {
        return ApiCalls.runAsync(
                () -> serverGroupsApi.cancelRollingRestart(context.networkId(), id),
                context.executor()
        );
    }

    @Override
    public CompletableFuture<Integer> stopAllServers(String id) {
        return ApiCalls.supplyAsync(() -> {
            ModelsStopAllServersResponse response = serverGroupsApi.stopAllServers(context.networkId(), id, null);
            invalidateServers();
            return intValue(response.getServersStopped());
        }, context.executor());
    }

    @Override
    public CompletableFuture<WorkflowExecutionResult> executeWorkflow(String id, String workflowId) {
        return ApiCalls.supplyAsync(() -> {
            ModelsExecuteManualWorkflowRequest request = new ModelsExecuteManualWorkflowRequest();
            request.setWorkflowId(workflowId);
            ModelsExecuteServerGroupWorkflowResponse response = serverGroupsApi.executeServerGroupWorkflow(context.networkId(), id, request);
            return WorkflowResults.of(
                    workflowId,
                    response.getWorkflowId(),
                    response.getTotalServers(),
                    response.getSucceededServerIds(),
                    response.getSkippedServerIds(),
                    response.getFailedServers()
            );
        }, context.executor());
    }

    @Override
    public CompletableFuture<PersistentServer> convertToPersistentServer(String id, String serverhostId) {
        return ApiCalls.supplyAsync(() -> {
            ModelsConvertGroupToPersistentRequest request = new ModelsConvertGroupToPersistentRequest();
            request.setServerhostId(serverhostId);
            PersistentServer persistentServer = PersistentServerImpl.from(
                    serverGroupsApi.convertGroupToPersistent(context.networkId(), id, request)
            );
            invalidate(id);
            context.cache().invalidateAll(QueryKey.of("persistentServers"));
            context.cache().invalidateAll(QueryKey.of("persistentServer"));
            return persistentServer;
        }, context.executor());
    }

    @Override
    public CompletableFuture<Map<String, Object>> deleteGroupProperties(String id, List<String> keys) {
        return ApiCalls.supplyAsync(() -> {
            ModelsDeletePropertiesRequest request = new ModelsDeletePropertiesRequest();
            request.setKeys(keys);
            ModelsDeletePropertiesResponse response = serverGroupsApi.deleteServerGroupProperties(context.networkId(), id, request);
            invalidate(id);
            return Objects.requireNonNullElseGet(response.getProperties(), HashMap::new);
        }, context.executor());
    }

    private CompletableFuture<Optional<Group>> findFirst(QueryKey key,
                                                        @Nullable String serverGroupId,
                                                        @Nullable String name,
                                                        Predicate<ModelsServerGroupSummary> filter) {
        return context.fetchWithTransientMissRecovery(
                key,
                () -> ApiCalls.supplyAsync(() -> listGroups(null, serverGroupId, name).stream()
                        .filter(filter)
                        .findFirst()
                        .<Group>map(GroupImpl::from)
                        .orElse(null), context.executor()),
                Objects::isNull
        ).thenApply(Optional::ofNullable);
    }

    private List<ModelsServerGroupSummary> listGroups(@Nullable GroupQuery query,
                                                      @Nullable String serverGroupId,
                                                      @Nullable String name) throws ApiException {
        ModelsListServerGroupsResponse response = serverGroupsApi.listServerGroups(
                context.networkId(),
                serverGroupId,
                name,
                query != null && query.getType() != null ? query.getType().name() : null,
                query != null ? query.getTag() : null,
                null,
                null,
                null,
                query != null ? query.getLimit() : null,
                null
        );
        return Objects.requireNonNullElse(response.getServerGroups(), List.of());
    }

    private QueryKey buildQueryKey(@Nullable GroupQuery query) {
        if (query == null) {
            return QueryKey.of("groups");
        }
        return QueryKey.of("groups", "query", query.getType(), query.getTag(), query.getLimit());
    }

    private @Nullable Group findCurrentGroup(String groupId) throws ApiException {
        return listGroups(null, groupId, null).stream()
                .filter(group -> groupId.equals(group.getServerGroupId()))
                .findFirst()
                .<Group>map(GroupImpl::from)
                .orElse(null);
    }

    private @Nullable String findAttachedBlueprintId(String groupId) throws ApiException {
        return listGroups(null, groupId, null).stream()
                .filter(group -> groupId.equals(group.getServerGroupId()))
                .map(ModelsServerGroupSummary::getSource)
                .map(inlineBlueprintSupport::getReferencedBlueprintId)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private void invalidate(String id) {
        context.cache().invalidate(QueryKey.of("group", id));
        invalidateLists();
        invalidateServers();
    }

    private void invalidateServers() {
        context.cache().invalidateAll(QueryKey.of("servers"));
        context.cache().invalidateAll(QueryKey.of("server"));
    }

    private void invalidateLists() {
        context.cache().invalidateAll(QueryKey.of("groups"));
        context.cache().invalidateAll(QueryKey.of("group", "name"));
    }

    private GroupStartQueueEntry convertStartQueueEntry(ModelsServerGroupStartQueueEntry entry) {
        List<GroupStartQueueItem> starts = Objects.requireNonNullElse(entry.getStarts(), List.<ModelsManualServerStartQueueItem>of())
                .stream()
                .map(this::convertStartQueueItem)
                .toList();

        return new GroupStartQueueEntry(
                intValue(entry.getFailedStarts()),
                intValue(entry.getQueuedStarts()),
                entry.getServerGroupId(),
                entry.getServerGroupName(),
                starts,
                intValue(entry.getTotalStarts())
        );
    }

    private GroupStartQueueItem convertStartQueueItem(ModelsManualServerStartQueueItem item) {
        String statusValue = item.getStatus() == null ? null : item.getStatus().getValue();
        return new GroupStartQueueItem(
                item.getCreatedAt(),
                item.getFailureReason(),
                item.getId(),
                GroupStartQueueItemStatus.fromApiValue(statusValue)
        );
    }

    private static int intValue(@Nullable Integer value) {
        return value != null ? value : 0;
    }

    private static String requireServerGroupId(@Nullable String serverGroupId) {
        if (serverGroupId == null || serverGroupId.isBlank()) {
            throw new IllegalArgumentException("serverGroupId must not be blank");
        }
        return serverGroupId;
    }
}
