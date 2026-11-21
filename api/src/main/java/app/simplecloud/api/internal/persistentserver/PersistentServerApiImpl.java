package app.simplecloud.api.internal.persistentserver;

import app.simplecloud.api.blueprint.CreateBlueprintRequest;
import app.simplecloud.api.cache.QueryKey;
import app.simplecloud.api.group.GroupServerType;
import app.simplecloud.api.group.WorkflowsConfig;
import app.simplecloud.api.internal.ApiContext;
import app.simplecloud.api.internal.blueprint.InlineBlueprintSupport;
import app.simplecloud.api.internal.create.CreateRequestDefaults;
import app.simplecloud.api.internal.util.ModelConversionUtil;
import app.simplecloud.api.internal.util.ValidationUtil;
import app.simplecloud.api.internal.web.ApiExceptions;
import app.simplecloud.api.internal.web.ApiCalls;
import app.simplecloud.api.persistentserver.*;
import app.simplecloud.api.web.ApiException;
import app.simplecloud.api.web.apis.BlueprintsApi;
import app.simplecloud.api.web.apis.PersistentServersApi;
import app.simplecloud.api.web.models.*;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

public class PersistentServerApiImpl implements PersistentServerApi {
    private final ApiContext context;
    private final PersistentServersApi persistentServersApi;
    private final InlineBlueprintSupport inlineBlueprintSupport;
    private final CreateRequestDefaults createRequestDefaults = new CreateRequestDefaults();

    public PersistentServerApiImpl(ApiContext context) {
        this(context, context.api(PersistentServersApi::new), new InlineBlueprintSupport(context.options(), context.api(BlueprintsApi::new)));
    }

    PersistentServerApiImpl(ApiContext context, PersistentServersApi persistentServersApi, InlineBlueprintSupport inlineBlueprintSupport) {
        this.context = context;
        this.persistentServersApi = persistentServersApi;
        this.inlineBlueprintSupport = inlineBlueprintSupport;
    }

    @Override
    public CompletableFuture<PersistentServer> getPersistentServerById(String id) {
        return findPersistentServerById(id).thenApply(ApiExceptions.orNotFound("Persistent server with ID '" + id + "' not found"));
    }

    @Override
    public CompletableFuture<Optional<PersistentServer>> findPersistentServerById(String id) {
        if (!ValidationUtil.isUuid(id)) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return findFirst(QueryKey.of("persistentServer", id), id, null, summary -> id.equals(summary.getPersistentServerId()));
    }

    @Override
    public CompletableFuture<PersistentServer> getPersistentServerByName(String name) {
        return findPersistentServerByName(name).thenApply(ApiExceptions.orNotFound("Persistent server '" + name + "' not found"));
    }

    @Override
    public CompletableFuture<Optional<PersistentServer>> findPersistentServerByName(String name) {
        return findFirst(QueryKey.of("persistentServer", "name", name), null, name, summary -> name.equals(summary.getName()));
    }

    @Override
    public CompletableFuture<List<PersistentServer>> getAllPersistentServers(@Nullable PersistentServerQuery query) {
        return context.cache().fetch(buildQueryKey(query), () -> ApiCalls.supplyAsync(
                () -> listPersistentServers(query, null, null).stream().<PersistentServer>map(PersistentServerImpl::from).toList(),
                context.executor()
        ));
    }

    @Override
    public CompletableFuture<PersistentServer> createPersistentServer(CreatePersistentServerRequest request) {
        return ApiCalls.supplyAsync(() -> {
            WorkflowsConfig workflows = createRequestDefaults.defaultWorkflows(request.getWorkflows());
            CreateBlueprintRequest createBlueprint = createRequestDefaults.defaultInlineBlueprint(request.getCreateBlueprint(), workflows);
            GroupServerType type = createRequestDefaults.defaultType(request.getType());

            ModelsCreatePersistentServerRequest apiRequest = new ModelsCreatePersistentServerRequest();
            apiRequest.setName(request.getName());
            apiRequest.setType(ModelsCreatePersistentServerRequest.TypeEnum.valueOf(type.name()));
            apiRequest.setMinMemory(request.getMinMemory());
            apiRequest.setMaxMemory(request.getMaxMemory());
            apiRequest.setMaxPlayers(createRequestDefaults.defaultMaxPlayers(request.getMaxPlayers()));
            apiRequest.setActive(createRequestDefaults.defaultActive(request.getActive()));
            apiRequest.setPriority(request.getPriority());
            apiRequest.setServerhostId(request.getServerhostId());
            apiRequest.setProperties(createRequestDefaults.defaultProperties(request.getProperties()));
            apiRequest.setTags(createRequestDefaults.defaultTags(request.getName(), request.getTags()));
            apiRequest.setWorkflows(ModelConversionUtil.toModel(workflows));

            inlineBlueprintSupport.validateInlineBlueprintSource(createBlueprint, request.getSource());
            String blueprintId = inlineBlueprintSupport.createBlueprint(request.getName(), createBlueprint);
            if (blueprintId != null) {
                InlineBlueprintSupport.invalidateCache(context.cache(), null);
            }
            apiRequest.setSource(ModelConversionUtil.toModel(inlineBlueprintSupport.resolveSource(request.getSource(), blueprintId)));

            ModelsCreatePersistentServerResponse response;
            try {
                response = persistentServersApi.createPersistentServer(context.networkId(), apiRequest);
            } catch (ApiException e) {
                throw inlineBlueprintSupport.rollBackAfterCreateFailure(blueprintId, e);
            }

            invalidateLists();
            return PersistentServerImpl.from(response);
        }, context.executor());
    }

    @Override
    public CompletableFuture<PersistentServer> updatePersistentServer(String id, UpdatePersistentServerRequest request) {
        return ApiCalls.runAsync(() -> {
            ModelsPatchPersistentServerRequest apiRequest = new ModelsPatchPersistentServerRequest();
            apiRequest.setName(request.getName());
            if (request.getType() != null) {
                apiRequest.setType(ModelsPatchPersistentServerRequest.TypeEnum.valueOf(request.getType().name()));
            }
            apiRequest.setMinMemory(request.getMinMemory());
            apiRequest.setMaxMemory(request.getMaxMemory());
            apiRequest.setMaxPlayers(request.getMaxPlayers());
            apiRequest.setActive(request.getActive());
            apiRequest.setPriority(request.getPriority());
            apiRequest.setServerhostId(request.getServerhostId());
            apiRequest.setProperties(request.getProperties());
            apiRequest.setTags(request.getTags());
            apiRequest.setSource(ModelConversionUtil.toModel(request.getSource()));
            apiRequest.setWorkflows(ModelConversionUtil.toModel(request.getWorkflows()));

            persistentServersApi.patchPersistentServer(context.networkId(), id, apiRequest);
            invalidate(id);
        }, context.executor()).thenCompose(ignored -> getPersistentServerById(id));
    }

    @Override
    public CompletableFuture<Void> deletePersistentServer(String id) {
        return ApiCalls.runAsync(() -> {
            String blueprintId = findAttachedBlueprintId(id);
            persistentServersApi.deletePersistentServer(context.networkId(), id);
            invalidate(id);

            inlineBlueprintSupport.deleteBlueprintOfDeletedResource(blueprintId);
            InlineBlueprintSupport.invalidateCache(context.cache(), blueprintId);
        }, context.executor());
    }

    @Override
    public CompletableFuture<Map<String, Object>> updatePersistentServerProperties(String id, Map<String, Object> properties) {
        return ApiCalls.supplyAsync(() -> {
            ModelsPatchPropertiesRequest request = new ModelsPatchPropertiesRequest();
            request.setProperties(properties);
            ModelsPatchPropertiesResponse response = persistentServersApi.patchPersistentServerProperties(context.networkId(), id, request);
            invalidate(id);
            return Objects.requireNonNullElseGet(response.getProperties(), HashMap::new);
        }, context.executor());
    }

    @Override
    public CompletableFuture<Map<String, Object>> deletePersistentServerProperties(String id, List<String> keys) {
        return ApiCalls.supplyAsync(() -> {
            ModelsDeletePropertiesRequest request = new ModelsDeletePropertiesRequest();
            request.setKeys(keys);
            ModelsDeletePropertiesResponse response = persistentServersApi.deletePersistentServerProperties(context.networkId(), id, request);
            invalidate(id);
            return Objects.requireNonNullElseGet(response.getProperties(), HashMap::new);
        }, context.executor());
    }

    private CompletableFuture<Optional<PersistentServer>> findFirst(QueryKey key,
                                                                   @Nullable String persistentServerId,
                                                                   @Nullable String name,
                                                                   Predicate<ModelsPersistentServerSummary> filter) {
        return context.fetchWithTransientMissRecovery(
                key,
                () -> ApiCalls.supplyAsync(() -> listPersistentServers(null, persistentServerId, name).stream()
                        .filter(filter)
                        .findFirst()
                        .<PersistentServer>map(PersistentServerImpl::from)
                        .orElse(null), context.executor()),
                Objects::isNull
        ).thenApply(Optional::ofNullable);
    }

    private List<ModelsPersistentServerSummary> listPersistentServers(@Nullable PersistentServerQuery query,
                                                                      @Nullable String persistentServerId,
                                                                      @Nullable String name) throws ApiException {
        String tags = query == null || query.getTags() == null || query.getTags().isEmpty()
                ? null
                : String.join(",", query.getTags());
        ModelsListPersistentServersResponse response = persistentServersApi.listPersistentServers(
                context.networkId(),
                persistentServerId,
                name,
                null,
                tags,
                query != null ? query.getActive() : null,
                null,
                null,
                query != null ? query.getServerhostId() : null,
                query != null ? query.getLimit() : null,
                null
        );
        return Objects.requireNonNullElse(response.getPersistentServers(), List.of());
    }

    private QueryKey buildQueryKey(@Nullable PersistentServerQuery query) {
        if (query == null) {
            return QueryKey.of("persistentServers");
        }
        return QueryKey.of("persistentServers", "query",
                query.getActive(),
                query.getServerhostId(),
                query.getTags() == null ? null : List.copyOf(query.getTags()),
                query.getLimit()
        );
    }

    private @Nullable String findAttachedBlueprintId(String persistentServerId) throws ApiException {
        return listPersistentServers(null, persistentServerId, null).stream()
                .filter(server -> persistentServerId.equals(server.getPersistentServerId()))
                .map(ModelsPersistentServerSummary::getSource)
                .map(inlineBlueprintSupport::getReferencedBlueprintId)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private void invalidate(String id) {
        context.cache().invalidate(QueryKey.of("persistentServer", id));
        invalidateLists();
    }

    private void invalidateLists() {
        context.cache().invalidateAll(QueryKey.of("persistentServers"));
        context.cache().invalidateAll(QueryKey.of("persistentServer", "name"));
    }
}
