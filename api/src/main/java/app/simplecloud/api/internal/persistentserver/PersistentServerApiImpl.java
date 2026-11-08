package app.simplecloud.api.internal.persistentserver;

import app.simplecloud.api.CloudApiOptions;
import app.simplecloud.api.blueprint.CreateBlueprintRequest;
import app.simplecloud.api.cache.EntityType;
import app.simplecloud.api.cache.QueryCache;
import app.simplecloud.api.cache.QueryKey;
import app.simplecloud.api.group.DeploymentHost;
import app.simplecloud.api.group.Group;
import app.simplecloud.api.group.GroupServerType;
import app.simplecloud.api.group.ScalingConfig;
import app.simplecloud.api.group.SourceConfig;
import app.simplecloud.api.group.WorkflowsConfig;
import app.simplecloud.api.internal.blueprint.BlueprintProvisioner;
import app.simplecloud.api.internal.create.CreateRequestDefaults;
import app.simplecloud.api.internal.group.GroupImpl;
import app.simplecloud.api.internal.util.RestModelMapper;
import app.simplecloud.api.internal.util.ValidationUtil;
import app.simplecloud.api.internal.web.ApiCalls;
import app.simplecloud.api.internal.web.ApiExceptions;
import app.simplecloud.api.internal.workflow.WorkflowResults;
import app.simplecloud.api.persistentserver.CreatePersistentServerRequest;
import app.simplecloud.api.persistentserver.PersistentServer;
import app.simplecloud.api.persistentserver.PersistentServerApi;
import app.simplecloud.api.persistentserver.PersistentServerQuery;
import app.simplecloud.api.persistentserver.UpdatePersistentServerRequest;
import app.simplecloud.api.web.ApiClient;
import app.simplecloud.api.web.ApiException;
import app.simplecloud.api.web.apis.BlueprintsApi;
import app.simplecloud.api.web.apis.PersistentServersApi;
import app.simplecloud.api.web.models.ModelsConversionDeployment;
import app.simplecloud.api.web.models.ModelsConvertPersistentToGroupRequest;
import app.simplecloud.api.web.models.ModelsCreatePersistentServerRequest;
import app.simplecloud.api.web.models.ModelsCreatePersistentServerResponse;
import app.simplecloud.api.web.models.ModelsDeletePropertiesRequest;
import app.simplecloud.api.web.models.ModelsListPersistentServersResponse;
import app.simplecloud.api.web.models.ModelsPatchPersistentServerRequest;
import app.simplecloud.api.web.models.ModelsPatchPropertiesRequest;
import app.simplecloud.api.web.models.ModelsPersistentServerSummary;
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

import static app.simplecloud.api.internal.util.QueryParams.joinOrNull;
import static app.simplecloud.api.internal.util.RestModelMapper.toModel;
import static app.simplecloud.api.internal.util.RestModelMapper.toSummary;
import static app.simplecloud.api.internal.util.ValidationUtil.requireNotBlank;

public class PersistentServerApiImpl implements PersistentServerApi {
    private static final Duration TRANSIENT_MISS_RETRY_DELAY = Duration.ofMillis(200);
    private static final int TRANSIENT_MISS_MAX_RETRIES = 2;

    private final CloudApiOptions options;
    private final PersistentServersApi persistentServersApi;
    private final BlueprintProvisioner blueprintProvisioner;
    private final CreateRequestDefaults createRequestDefaults;
    private final QueryCache cache;
    private final Executor executor;

    public PersistentServerApiImpl(CloudApiOptions options, QueryCache cache, ApiClient httpClient, Executor executor) {
        this.options = options;
        this.cache = cache;
        this.persistentServersApi = new PersistentServersApi(httpClient);
        this.blueprintProvisioner = new BlueprintProvisioner(options, cache, new BlueprintsApi(httpClient));
        this.createRequestDefaults = new CreateRequestDefaults();
        this.executor = executor;
    }

    @Override
    public CompletableFuture<PersistentServer> getPersistentServerById(String id) {
        return findPersistentServerById(id)
                .thenApply(persistentServer -> persistentServer.orElseThrow(
                        () -> ApiExceptions.notFound("Persistent server with ID '" + id + "' not found")));
    }

    @Override
    public CompletableFuture<Optional<PersistentServer>> findPersistentServerById(String requestedId) {
        if (!ValidationUtil.isUuid(requestedId)) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        String id = ValidationUtil.normalizeUuid(requestedId);

        return cache.fetchWithTransientMissRecovery(
                        QueryKey.of("persistentServer", id),
                        () -> fetchSinglePersistentServer(
                                id, null, summary -> id.equals(summary.getPersistentServerId())),
                        Objects::isNull,
                        TRANSIENT_MISS_RETRY_DELAY,
                        TRANSIENT_MISS_MAX_RETRIES)
                .thenApply(Optional::ofNullable);
    }

    @Override
    public CompletableFuture<PersistentServer> getPersistentServerByName(String name) {
        return findPersistentServerByName(name)
                .thenApply(persistentServer -> persistentServer.orElseThrow(
                        () -> ApiExceptions.notFound("Persistent server '" + name + "' not found")));
    }

    @Override
    public CompletableFuture<Optional<PersistentServer>> findPersistentServerByName(String name) {
        return cache.fetchWithTransientMissRecovery(
                        QueryKey.of("persistentServer", "name", name),
                        () -> fetchSinglePersistentServer(null, name, summary -> name.equals(summary.getName())),
                        Objects::isNull,
                        TRANSIENT_MISS_RETRY_DELAY,
                        TRANSIENT_MISS_MAX_RETRIES)
                .thenApply(Optional::ofNullable);
    }

    @Override
    public CompletableFuture<List<PersistentServer>> getAllPersistentServers(
            @Nullable PersistentServerQuery requestedQuery) {
        PersistentServerQuery query = requestedQuery == null ? null : requestedQuery.copy();

        return cache.fetch(
                buildQueryKey(query),
                () -> ApiCalls.supplyAsync(
                        executor,
                        () -> listPersistentServers(query, null, null).stream()
                                .<PersistentServer>map(PersistentServerImpl::new)
                                .toList()));
    }

    private QueryKey buildQueryKey(@Nullable PersistentServerQuery query) {
        if (query == null) {
            return QueryKey.of("persistentServers");
        }
        return QueryKey.of(
                "persistentServers",
                "query",
                query.getActive(),
                query.getServerhostId(),
                query.getTags() == null ? null : List.copyOf(query.getTags()),
                query.getType(),
                query.getSourceType(),
                query.getBlueprintId(),
                query.getLimit(),
                query.getOffset());
    }

    private List<ModelsPersistentServerSummary> listPersistentServers(
            @Nullable PersistentServerQuery query, @Nullable String persistentServerId, @Nullable String name)
            throws ApiException {
        PersistentServerQuery q = query == null ? PersistentServerQuery.create() : query;
        ModelsListPersistentServersResponse response = persistentServersApi.listPersistentServers(
                options.getNetworkId(),
                persistentServerId,
                name,
                q.getType() != null ? ValidationUtil.serverTypeName(q.getType()) : null,
                joinOrNull(q.getTags()),
                q.getActive(),
                q.getSourceType() != null ? q.getSourceType().name().toLowerCase(Locale.ROOT) : null,
                q.getBlueprintId(),
                q.getServerhostId(),
                q.getLimit(),
                q.getOffset());
        List<ModelsPersistentServerSummary> servers = response.getPersistentServers();
        return servers == null ? List.of() : servers;
    }

    private CompletableFuture<PersistentServer> fetchSinglePersistentServer(
            @Nullable String id, @Nullable String name, Predicate<ModelsPersistentServerSummary> filter) {
        return ApiCalls.supplyAsync(
                executor,
                () -> listPersistentServers(null, id, name).stream()
                        .filter(filter)
                        .findFirst()
                        .<PersistentServer>map(PersistentServerImpl::new)
                        .orElse(null));
    }

    private boolean persistentServerExistsWithBlueprint(String name, @Nullable String blueprintId) {
        if (blueprintId == null) {
            return false;
        }
        return ApiCalls.execute(() -> listPersistentServers(null, null, name).stream()
                .anyMatch(summary ->
                        name.equals(summary.getName()) && sourceReferencesBlueprint(summary.getSource(), blueprintId)));
    }

    @Override
    public CompletableFuture<PersistentServer> createPersistentServer(CreatePersistentServerRequest request) {
        return ApiCalls.supplyAsync(executor, () -> {
            WorkflowsConfig effectiveWorkflows = createRequestDefaults.defaultWorkflows(request.getWorkflows());
            CreateBlueprintRequest effectiveCreateBlueprint =
                    createRequestDefaults.defaultInlineBlueprint(request.getCreateBlueprint(), effectiveWorkflows);
            GroupServerType effectiveType = createRequestDefaults.defaultType(request.getType());

            blueprintProvisioner.validateInlineBlueprintSource(effectiveCreateBlueprint, request.getSource());

            ModelsCreatePersistentServerRequest apiRequest = new ModelsCreatePersistentServerRequest();
            apiRequest.setName(request.getName());
            apiRequest.setMinMemory(request.getMinMemory());
            apiRequest.setMaxMemory(request.getMaxMemory());
            apiRequest.setMaxPlayers(createRequestDefaults.defaultMaxPlayers(request.getMaxPlayers()));
            apiRequest.setActive(createRequestDefaults.defaultActive(request.getActive()));
            apiRequest.setPriority(request.getPriority());
            apiRequest.setServerhostId(request.getServerhostId());
            apiRequest.setProperties(createRequestDefaults.defaultProperties(request.getProperties()));
            apiRequest.setTags(createRequestDefaults.defaultTags(request.getName(), request.getTags()));
            apiRequest.setType(
                    ModelsCreatePersistentServerRequest.TypeEnum.valueOf(ValidationUtil.serverTypeName(effectiveType)));
            apiRequest.setWorkflows(toModel(effectiveWorkflows));

            String blueprintId = blueprintProvisioner.createBlueprint(request.getName(), effectiveCreateBlueprint);
            SourceConfig effectiveSource = blueprintId != null
                    ? blueprintProvisioner.buildInlineBlueprintSource(request.getSource(), blueprintId)
                    : request.getSource();
            if (effectiveSource != null) {
                apiRequest.setSource(toModel(effectiveSource));
            }

            ModelsCreatePersistentServerResponse response;
            try {
                response = persistentServersApi.createPersistentServer(options.getNetworkId(), apiRequest);
            } catch (ApiException e) {
                throw blueprintProvisioner.rollBackAfterCreateFailure(
                        "persistent server",
                        blueprintId,
                        e,
                        () -> persistentServerExistsWithBlueprint(request.getName(), blueprintId));
            }

            invalidatePersistentServers(null);
            return new PersistentServerImpl(toSummary(response));
        });
    }

    @Override
    public CompletableFuture<PersistentServer> updatePersistentServer(
            String id, UpdatePersistentServerRequest request) {
        return ApiCalls.supplyAsync(executor, () -> {
            ModelsPatchPersistentServerRequest apiRequest = new ModelsPatchPersistentServerRequest();
            apiRequest.setName(request.getName());
            apiRequest.setMinMemory(request.getMinMemory());
            apiRequest.setMaxMemory(request.getMaxMemory());
            apiRequest.setMaxPlayers(request.getMaxPlayers());
            apiRequest.setActive(request.getActive());
            apiRequest.setPriority(request.getPriority());
            apiRequest.setServerhostId(request.getServerhostId());
            apiRequest.setProperties(request.getProperties());
            apiRequest.setTags(request.getTags());
            if (request.getType() != null) {
                apiRequest.setType(ModelsPatchPersistentServerRequest.TypeEnum.valueOf(
                        ValidationUtil.serverTypeName(request.getType())));
            }
            if (request.getSource() != null) {
                apiRequest.setSource(toModel(request.getSource()));
            }
            if (request.getWorkflows() != null) {
                apiRequest.setWorkflows(toModel(request.getWorkflows()));
            }

            PersistentServer persistentServer = new PersistentServerImpl(
                    toSummary(persistentServersApi.patchPersistentServer(options.getNetworkId(), id, apiRequest)));
            invalidatePersistentServerAndServers(id);
            return persistentServer;
        });
    }

    @Override
    public CompletableFuture<Void> deletePersistentServer(String id) {
        return ApiCalls.runAsync(executor, () -> {
            String blueprintId = findAttachedBlueprintId(id);
            persistentServersApi.deletePersistentServer(options.getNetworkId(), id);
            invalidatePersistentServerAndServers(id);
            blueprintProvisioner.deleteBlueprint(blueprintId);
        });
    }

    @Override
    public CompletableFuture<WorkflowExecutionResult> executeWorkflow(String id, String workflowId) {
        return ApiCalls.supplyAsync(
                executor,
                () -> WorkflowResults.fromPersistentServer(
                        workflowId,
                        persistentServersApi.executePersistentServerWorkflow(
                                options.getNetworkId(),
                                requireNotBlank(id, "id"),
                                WorkflowResults.request(workflowId))));
    }

    @Override
    public CompletableFuture<Group> convertToGroup(
            String persistentServerId, List<DeploymentHost> hosts, ScalingConfig scaling) {
        return ApiCalls.supplyAsync(executor, () -> {
            String id = requireNotBlank(persistentServerId, "persistentServerId");
            Objects.requireNonNull(hosts, "hosts");
            ValidationUtil.requireCompleteScaling(Objects.requireNonNull(scaling, "scaling"));

            ModelsConversionDeployment deployment = new ModelsConversionDeployment();
            deployment.setHosts(hosts.stream().map(RestModelMapper::toModel).toList());

            ModelsConvertPersistentToGroupRequest request = new ModelsConvertPersistentToGroupRequest();
            request.setDeployment(deployment);
            request.setScaling(toModel(scaling));

            Group group = new GroupImpl(
                    toSummary(persistentServersApi.convertPersistentToGroup(options.getNetworkId(), id, request)));

            invalidatePersistentServers(id);
            cache.invalidateAll(QueryKey.of("groups"));
            cache.invalidateAll(QueryKey.of("group", "name"));
            return group;
        });
    }

    @Override
    public CompletableFuture<Map<String, Object>> setPersistentServerProperties(
            String id, Map<String, Object> properties) {
        return ApiCalls.supplyAsync(executor, () -> {
            ModelsUpdatePropertiesRequest request = new ModelsUpdatePropertiesRequest();
            request.setProperties(properties);
            Map<String, Object> result = persistentServersApi
                    .updatePersistentServerProperties(options.getNetworkId(), id, request)
                    .getProperties();
            invalidatePersistentServerAndServers(id);
            return result != null ? result : new HashMap<>();
        });
    }

    @Override
    public CompletableFuture<Map<String, Object>> updatePersistentServerProperties(
            String id, Map<String, Object> properties) {
        return ApiCalls.supplyAsync(executor, () -> {
            ModelsPatchPropertiesRequest request = new ModelsPatchPropertiesRequest();
            request.setProperties(properties);
            Map<String, Object> result = persistentServersApi
                    .patchPersistentServerProperties(options.getNetworkId(), id, request)
                    .getProperties();
            invalidatePersistentServerAndServers(id);
            return result != null ? result : new HashMap<>();
        });
    }

    @Override
    public CompletableFuture<Map<String, Object>> deletePersistentServerProperties(String id, List<String> keys) {
        return ApiCalls.supplyAsync(executor, () -> {
            ModelsDeletePropertiesRequest request = new ModelsDeletePropertiesRequest();
            request.setKeys(keys);
            Map<String, Object> result = persistentServersApi
                    .deletePersistentServerProperties(options.getNetworkId(), id, request)
                    .getProperties();
            invalidatePersistentServerAndServers(id);
            return result != null ? result : new HashMap<>();
        });
    }

    private void invalidatePersistentServerAndServers(String id) {
        invalidatePersistentServers(id);
        cache.invalidateByType(EntityType.SERVER);
    }

    private void invalidatePersistentServers(@Nullable String id) {
        if (id != null) {
            cache.invalidate(QueryKey.of("persistentServer", id));
        }
        cache.invalidateAll(QueryKey.of("persistentServers"));
        cache.invalidateAll(QueryKey.of("persistentServer", "name"));
    }

    private boolean sourceReferencesBlueprint(@Nullable ModelsSourceConfig source, String blueprintId) {
        return source != null && blueprintId.equals(source.getBlueprint());
    }

    @Nullable
    private String findAttachedBlueprintId(String persistentServerId) throws ApiException {
        return listPersistentServers(null, persistentServerId, null).stream()
                .filter(server -> persistentServerId.equalsIgnoreCase(server.getPersistentServerId()))
                .map(ModelsPersistentServerSummary::getSource)
                .map(blueprintProvisioner::getReferencedBlueprintId)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }
}
