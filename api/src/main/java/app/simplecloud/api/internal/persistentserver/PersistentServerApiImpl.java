package app.simplecloud.api.internal.persistentserver;

import app.simplecloud.api.CloudApiOptions;
import app.simplecloud.api.blueprint.CreateBlueprintRequest;
import app.simplecloud.api.cache.QueryCache;
import app.simplecloud.api.cache.QueryKey;
import app.simplecloud.api.group.GroupServerType;
import app.simplecloud.api.group.SourceConfig;
import app.simplecloud.api.group.WorkflowsConfig;
import app.simplecloud.api.internal.blueprint.InlineBlueprintSupport;
import app.simplecloud.api.internal.create.CreateRequestDefaults;
import app.simplecloud.api.internal.networkcopy.NetworkContents;
import app.simplecloud.api.internal.networkcopy.NetworkCopySynchronizer;
import app.simplecloud.api.internal.web.ApiClients;
import app.simplecloud.api.internal.web.ApiErrors;
import app.simplecloud.api.internal.web.Identifiers;
import app.simplecloud.api.persistentserver.*;
import app.simplecloud.api.web.ApiException;
import app.simplecloud.api.web.ApiClient;
import app.simplecloud.api.web.ApiResponse;
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
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

public class PersistentServerApiImpl implements PersistentServerApi {
    private final CloudApiOptions options;
    private final PersistentServersApi persistentServersApi;
    private final InlineBlueprintSupport inlineBlueprintSupport;
    private final CreateRequestDefaults createRequestDefaults;
    private final QueryCache cache;
    private final Executor executor;
    private final NetworkCopySynchronizer networkCopySynchronizer;
    private final HttpPersistentServerReadApi httpReadApi;

    public PersistentServerApiImpl(CloudApiOptions options, QueryCache cache) {
        this(options, cache, ApiClients.create(options));
    }

    public PersistentServerApiImpl(CloudApiOptions options, QueryCache cache, ApiClient httpClient) {
        this(options, cache, httpClient, ForkJoinPool.commonPool());
    }

    public PersistentServerApiImpl(CloudApiOptions options, QueryCache cache, ApiClient httpClient, Executor executor) {
        this(options, cache, httpClient, executor, NetworkCopySynchronizer.createNeverStarted());
    }

    public PersistentServerApiImpl(CloudApiOptions options,
                                   QueryCache cache,
                                   ApiClient httpClient,
                                   Executor executor,
                                   NetworkCopySynchronizer networkCopySynchronizer) {
        this(
                options,
                cache,
                createPersistentServersApi(options, httpClient),
                new InlineBlueprintSupport(options, createBlueprintsApi(options, httpClient)),
                executor,
                networkCopySynchronizer
        );
    }

    PersistentServerApiImpl(CloudApiOptions options,
                            QueryCache cache,
                            PersistentServersApi persistentServersApi,
                            BlueprintsApi blueprintsApi) {
        this(
                options,
                cache,
                persistentServersApi,
                new InlineBlueprintSupport(options, blueprintsApi),
                ForkJoinPool.commonPool(),
                NetworkCopySynchronizer.createNeverStarted()
        );
    }

    PersistentServerApiImpl(CloudApiOptions options,
                            QueryCache cache,
                            PersistentServersApi persistentServersApi,
                            BlueprintsApi blueprintsApi,
                            InlineBlueprintSupport.ServerUrlResolver serverUrlResolver) {
        this(
                options,
                cache,
                persistentServersApi,
                new InlineBlueprintSupport(options, blueprintsApi, serverUrlResolver),
                ForkJoinPool.commonPool(),
                NetworkCopySynchronizer.createNeverStarted()
        );
    }

    private PersistentServerApiImpl(CloudApiOptions options,
                                    QueryCache cache,
                                    PersistentServersApi persistentServersApi,
                                    InlineBlueprintSupport inlineBlueprintSupport,
                                    Executor executor,
                                    NetworkCopySynchronizer networkCopySynchronizer) {
        this.options = options;
        this.cache = cache;
        this.persistentServersApi = persistentServersApi;
        this.inlineBlueprintSupport = inlineBlueprintSupport;
        this.createRequestDefaults = new CreateRequestDefaults();
        this.executor = executor;
        this.networkCopySynchronizer = networkCopySynchronizer;
        this.httpReadApi = new HttpPersistentServerReadApi(options, persistentServersApi, cache, executor);
    }

    @Override
    public CompletableFuture<PersistentServer> getPersistentServerById(String id) {
        return findPersistentServerById(id).thenApply(persistentServer -> persistentServer.orElseThrow(
                () -> ApiErrors.notFound("Persistent server with ID '" + id + "' not found")
        ));
    }

    @Override
    public CompletableFuture<Optional<PersistentServer>> findPersistentServerById(String id) {
        if (!Identifiers.isUuid(id)) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return selectPersistentServerReadApi().findPersistentServerById(id);
    }

    @Override
    public CompletableFuture<PersistentServer> getPersistentServerByName(String name) {
        return findPersistentServerByName(name).thenApply(persistentServer -> persistentServer.orElseThrow(
                () -> ApiErrors.notFound("Persistent server '" + name + "' not found")
        ));
    }

    @Override
    public CompletableFuture<Optional<PersistentServer>> findPersistentServerByName(String name) {
        return selectPersistentServerReadApi().findPersistentServerByName(name);
    }

    @Override
    public CompletableFuture<List<PersistentServer>> getAllPersistentServers(@Nullable PersistentServerQuery query) {
        return selectPersistentServerReadApi().getAllPersistentServers(query);
    }

    private InternalPersistentServerReadApi selectPersistentServerReadApi() {
        NetworkContents contents = networkCopySynchronizer.currentContents();
        if (contents == null) {
            return httpReadApi;
        }
        return new NetworkCopyPersistentServerReadApi(contents);
    }

    private boolean persistentServerExistsWithBlueprint(String name, @Nullable String blueprintId) {
        if (blueprintId == null) {
            return false;
        }

        try {
            ModelsListPersistentServersResponse response = httpReadApi.listPersistentServersOverHttp(null, null, name);
            List<ModelsPersistentServerSummary> servers = response.getPersistentServers();
            if (servers == null) {
                return false;
            }

            return servers.stream()
                    .anyMatch(summary -> name.equals(summary.getName()) && sourceReferencesBlueprint(summary.getSource(), blueprintId));
        } catch (ApiException e) {
            throw ApiErrors.from(e);
        }
    }

    @Override
    public CompletableFuture<PersistentServer> createPersistentServer(CreatePersistentServerRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            WorkflowsConfig effectiveWorkflows = createRequestDefaults.defaultWorkflows(request.getWorkflows());
            CreateBlueprintRequest effectiveCreateBlueprint = createRequestDefaults.defaultInlineBlueprint(
                    request.getCreateBlueprint(),
                    effectiveWorkflows
            );
            GroupServerType effectiveType = createRequestDefaults.defaultType(request.getType());
            int effectiveMaxPlayers = createRequestDefaults.defaultMaxPlayers(request.getMaxPlayers());
            boolean effectiveActive = createRequestDefaults.defaultActive(request.getActive());
            Map<String, Object> effectiveProperties = createRequestDefaults.defaultProperties(request.getProperties());
            List<String> effectiveTags = createRequestDefaults.defaultTags(request.getName(), request.getTags());

            inlineBlueprintSupport.validateInlineBlueprintSource(effectiveCreateBlueprint, request.getSource());

            ModelsCreatePersistentServerRequest apiRequest = new ModelsCreatePersistentServerRequest();
            apiRequest.setName(request.getName());
            apiRequest.setMinMemory(request.getMinMemory());
            apiRequest.setMaxMemory(request.getMaxMemory());
            apiRequest.setMaxPlayers(effectiveMaxPlayers);
            apiRequest.setActive(effectiveActive);
            apiRequest.setPriority(request.getPriority());
            apiRequest.setServerhostId(request.getServerhostId());
            apiRequest.setProperties(effectiveProperties);
            apiRequest.setTags(effectiveTags);
            apiRequest.setType(ModelsCreatePersistentServerRequest.TypeEnum.valueOf(effectiveType.name()));
            apiRequest.setWorkflows(convertWorkflowsConfig(effectiveWorkflows));

            String createdBlueprintId = null;
            try {
                createdBlueprintId = inlineBlueprintSupport.createBlueprint(request.getName(), effectiveCreateBlueprint);
                SourceConfig effectiveSource = createdBlueprintId != null
                        ? inlineBlueprintSupport.buildInlineBlueprintSource(request.getSource(), createdBlueprintId)
                        : request.getSource();
                if (effectiveSource != null) {
                    apiRequest.setSource(convertSourceConfig(effectiveSource));
                }

                ApiResponse<ModelsCreatePersistentServerResponse> httpResponse;
                try {
                    httpResponse = persistentServersApi.createPersistentServerWithHttpInfo(
                            options.getNetworkId(),
                            apiRequest
                    );
                } catch (ApiException e) {
                    String blueprintId = createdBlueprintId;
                    throw inlineBlueprintSupport.rollBackAfterCreateFailure(
                            "persistent server",
                            blueprintId,
                            e,
                            () -> persistentServerExistsWithBlueprint(request.getName(), blueprintId)
                    );
                }
                ModelsCreatePersistentServerResponse response = httpResponse.getData();

                // Invalidate list caches (new persistent server created)
                cache.invalidateAll(QueryKey.of("persistentServers"));
                cache.invalidateAll(QueryKey.of("persistentServer", "name"));

                networkCopySynchronizer.waitForOwnWrite(httpResponse.getHeaders());
                return new PersistentServerImpl(toSummary(response));
            } catch (ApiException e) {
                throw ApiErrors.from(e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<PersistentServer> updatePersistentServer(String id, UpdatePersistentServerRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
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
                    apiRequest.setType(ModelsPatchPersistentServerRequest.TypeEnum.valueOf(request.getType().name()));
                }
                if (request.getSource() != null) {
                    apiRequest.setSource(convertSourceConfig(request.getSource()));
                }
                if (request.getWorkflows() != null) {
                    apiRequest.setWorkflows(convertWorkflowsConfig(request.getWorkflows()));
                }

                ApiResponse<ModelsUpdatePersistentServerResponse> httpResponse = persistentServersApi.patchPersistentServerWithHttpInfo(
                        options.getNetworkId(),
                        id,
                        apiRequest
                );
                ModelsUpdatePersistentServerResponse response = httpResponse.getData();

                cache.invalidate(QueryKey.of("persistentServer", id));
                cache.invalidateAll(QueryKey.of("persistentServers"));
                cache.invalidateAll(QueryKey.of("persistentServer", "name"));

                networkCopySynchronizer.waitForOwnWrite(httpResponse.getHeaders());
                return new PersistentServerImpl(toSummary(response));
            } catch (ApiException e) {
                throw ApiErrors.from(e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> deletePersistentServer(String id) {
        return CompletableFuture.runAsync(() -> {
            try {
                String blueprintId = findAttachedBlueprintId(id);

                ApiResponse<ModelsDeletePersistentServerResponse> httpResponse = persistentServersApi.deletePersistentServerWithHttpInfo(
                        options.getNetworkId(),
                        id
                );

                // Invalidate this persistent server and list caches
                cache.invalidate(QueryKey.of("persistentServer", id));
                cache.invalidateAll(QueryKey.of("persistentServers"));
                cache.invalidateAll(QueryKey.of("persistentServer", "name"));

                inlineBlueprintSupport.deleteBlueprint(blueprintId);
                networkCopySynchronizer.waitForOwnWrite(httpResponse.getHeaders());
            } catch (ApiException e) {
                throw ApiErrors.from(e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Map<String, Object>> updatePersistentServerProperties(String id, Map<String, Object> properties) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ModelsPatchPropertiesRequest request = new ModelsPatchPropertiesRequest();
                request.setProperties(properties);

                ApiResponse<ModelsPatchPropertiesResponse> httpResponse = persistentServersApi.patchPersistentServerPropertiesWithHttpInfo(
                        options.getNetworkId(),
                        id,
                        request
                );
                ModelsPatchPropertiesResponse response = httpResponse.getData();

                // Invalidate persistent server cache (properties changed)
                cache.invalidate(QueryKey.of("persistentServer", id));
                cache.invalidateAll(QueryKey.of("persistentServers"));
                cache.invalidateAll(QueryKey.of("persistentServer", "name"));

                networkCopySynchronizer.waitForOwnWrite(httpResponse.getHeaders());
                Map<String, Object> result = response.getProperties();
                return result != null ? result : new HashMap<>();
            } catch (ApiException e) {
                throw ApiErrors.from(e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Map<String, Object>> deletePersistentServerProperties(String id, List<String> keys) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ModelsDeletePropertiesRequest request = new ModelsDeletePropertiesRequest();
                request.setKeys(keys);

                ApiResponse<ModelsDeletePropertiesResponse> httpResponse = persistentServersApi.deletePersistentServerPropertiesWithHttpInfo(
                        options.getNetworkId(),
                        id,
                        request
                );
                ModelsDeletePropertiesResponse response = httpResponse.getData();

                // Invalidate persistent server cache (properties changed)
                cache.invalidate(QueryKey.of("persistentServer", id));
                cache.invalidateAll(QueryKey.of("persistentServers"));
                cache.invalidateAll(QueryKey.of("persistentServer", "name"));

                networkCopySynchronizer.waitForOwnWrite(httpResponse.getHeaders());
                Map<String, Object> result = response.getProperties();
                return result != null ? result : new HashMap<>();
            } catch (ApiException e) {
                throw ApiErrors.from(e);
            }
        }, executor);
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

    private ModelsPersistentServerSummary toSummary(ModelsCreatePersistentServerResponse response) {
        ModelsPersistentServerSummary summary = new ModelsPersistentServerSummary();
        summary.setPersistentServerId(response.getPersistentServerId());
        summary.setName(response.getName());
        summary.setType(response.getType());
        summary.setMinMemory(response.getMinMemory());
        summary.setMaxMemory(response.getMaxMemory());
        summary.setMaxPlayers(response.getMaxPlayers());
        summary.setActive(response.getActive());
        summary.setPriority(response.getPriority());
        summary.setPlayerCount(response.getPlayerCount());
        summary.setServerhostId(response.getServerhostId());
        summary.setSource(response.getSource());
        summary.setWorkflows(response.getWorkflows());
        summary.setProperties(response.getProperties());
        summary.setTags(response.getTags());
        summary.setCreatedAt(response.getCreatedAt());
        summary.setUpdatedAt(response.getUpdatedAt());
        return summary;
    }

    private ModelsPersistentServerSummary toSummary(ModelsUpdatePersistentServerResponse response) {
        ModelsPersistentServerSummary summary = new ModelsPersistentServerSummary();
        summary.setPersistentServerId(response.getPersistentServerId());
        summary.setName(response.getName());
        summary.setType(response.getType());
        summary.setMinMemory(response.getMinMemory());
        summary.setMaxMemory(response.getMaxMemory());
        summary.setMaxPlayers(response.getMaxPlayers());
        summary.setActive(response.getActive());
        summary.setPriority(response.getPriority());
        summary.setPlayerCount(response.getPlayerCount());
        summary.setServerhostId(response.getServerhostId());
        summary.setSource(response.getSource());
        summary.setWorkflows(response.getWorkflows());
        summary.setProperties(response.getProperties());
        summary.setTags(response.getTags());
        summary.setCreatedAt(response.getCreatedAt());
        summary.setUpdatedAt(response.getUpdatedAt());
        return summary;
    }

    private boolean sourceReferencesBlueprint(@Nullable ModelsSourceConfig source, String blueprintId) {
        return source != null && blueprintId.equals(source.getBlueprint());
    }

    @Nullable
    private String findAttachedBlueprintId(String persistentServerId) throws ApiException {
        ModelsListPersistentServersResponse response = httpReadApi.listPersistentServersOverHttp(null, persistentServerId, null);
        List<ModelsPersistentServerSummary> servers = response.getPersistentServers();
        if (servers == null) {
            return null;
        }

        return servers.stream()
                .filter(server -> persistentServerId.equals(server.getPersistentServerId()))
                .map(ModelsPersistentServerSummary::getSource)
                .map(inlineBlueprintSupport::getReferencedBlueprintId)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
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

    private static PersistentServersApi createPersistentServersApi(CloudApiOptions options, ApiClient httpClient) {
        PersistentServersApi api = new PersistentServersApi(httpClient);
        api.setCustomBaseUrl(options.getControllerUrl());
        return api;
    }

    private static BlueprintsApi createBlueprintsApi(CloudApiOptions options, ApiClient httpClient) {
        BlueprintsApi api = new BlueprintsApi(httpClient);
        api.setCustomBaseUrl(options.getControllerUrl());
        return api;
    }
}
