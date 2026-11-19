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
import app.simplecloud.api.internal.networkstate.read.ReadSources;
import app.simplecloud.api.internal.networkstate.read.ReadableCopies;
import app.simplecloud.api.internal.networkstate.read.ReadableCopy;
import app.simplecloud.api.internal.web.ApiClients;
import app.simplecloud.api.internal.web.ApiErrors;
import app.simplecloud.api.internal.web.Identifiers;
import app.simplecloud.api.persistentserver.*;
import app.simplecloud.api.web.ApiException;
import app.simplecloud.api.web.ApiClient;
import app.simplecloud.api.web.apis.BlueprintsApi;
import app.simplecloud.api.web.apis.PersistentServersApi;
import app.simplecloud.api.web.models.*;
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
import java.util.function.Supplier;

public class PersistentServerApiImpl implements PersistentServerApi {
    private static final Duration TRANSIENT_MISS_RETRY_DELAY = Duration.ofMillis(200);
    private static final int TRANSIENT_MISS_MAX_RETRIES = 2;

    private final CloudApiOptions options;
    private final PersistentServersApi persistentServersApi;
    private final InlineBlueprintSupport inlineBlueprintSupport;
    private final CreateRequestDefaults createRequestDefaults;
    private final QueryCache cache;
    private final Executor executor;
    private final ReadSources<PersistentServerSource> sources;

    public PersistentServerApiImpl(CloudApiOptions options, QueryCache cache) {
        this(options, cache, ApiClients.create(options));
    }

    public PersistentServerApiImpl(CloudApiOptions options, QueryCache cache, ApiClient httpClient) {
        this(options, cache, httpClient, ForkJoinPool.commonPool(), ReadableCopies.none());
    }

    /**
     * Reads from the controller only, never from a copy.
     *
     * @deprecated kept because released versions have it; pass the runtime's {@link ReadableCopies}.
     */
    @Deprecated
    public PersistentServerApiImpl(CloudApiOptions options, QueryCache cache, ApiClient httpClient, Executor executor) {
        this(options, cache, httpClient, executor, ReadableCopies.none());
    }

    public PersistentServerApiImpl(CloudApiOptions options, QueryCache cache, ApiClient httpClient, Executor executor,
                                   ReadableCopies copies) {
        this(
                options,
                cache,
                createPersistentServersApi(options, httpClient),
                new InlineBlueprintSupport(options, createBlueprintsApi(options, httpClient)),
                executor,
                copies
        );
    }

    PersistentServerApiImpl(CloudApiOptions options,
                            QueryCache cache,
                            PersistentServersApi persistentServersApi,
                            BlueprintsApi blueprintsApi) {
        this(options, cache, persistentServersApi, new InlineBlueprintSupport(options, blueprintsApi), ForkJoinPool.commonPool(),
                ReadableCopies.none());
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
                ReadableCopies.none()
        );
    }

    private PersistentServerApiImpl(CloudApiOptions options,
                                    QueryCache cache,
                                    PersistentServersApi persistentServersApi,
                                    InlineBlueprintSupport inlineBlueprintSupport,
                                    Executor executor,
                                    ReadableCopies copies) {
        this.options = options;
        this.cache = cache;
        this.persistentServersApi = persistentServersApi;
        this.inlineBlueprintSupport = inlineBlueprintSupport;
        this.createRequestDefaults = new CreateRequestDefaults();
        this.executor = executor;
        this.sources = new ReadSources<>(copies, ReadableCopy::persistentServers,
                new ControllerPersistentServerSource());
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
        CompletableFuture<PersistentServer> persistentServer = sources.lookUp(
                source -> source.persistentServerWithId(id));
        return persistentServer.thenApply(Optional::ofNullable);
    }

    @Override
    public CompletableFuture<PersistentServer> getPersistentServerByName(String name) {
        return findPersistentServerByName(name).thenApply(persistentServer -> persistentServer.orElseThrow(
                () -> ApiErrors.notFound("Persistent server '" + name + "' not found")
        ));
    }

    @Override
    public CompletableFuture<Optional<PersistentServer>> findPersistentServerByName(String name) {
        CompletableFuture<PersistentServer> persistentServer = sources.lookUp(
                source -> source.persistentServerWithName(name));
        return persistentServer.thenApply(Optional::ofNullable);
    }

    @Override
    public CompletableFuture<List<PersistentServer>> getAllPersistentServers(@Nullable PersistentServerQuery query) {
        return sources.ask(source -> source.persistentServers(query));
    }

    /**
     * Answers through the query cache, which has its own recovery for a lookup the controller misses.
     */
    private final class ControllerPersistentServerSource implements PersistentServerSource {

        @Override
        public CompletableFuture<@Nullable PersistentServer> persistentServerWithId(String id) {
            QueryKey key = QueryKey.of("persistentServer", id);
            Supplier<CompletableFuture<PersistentServer>> fetch = () -> fetchPersistentServerByIdFromController(id);
            return cache.fetchWithTransientMissRecovery(
                    key,
                    fetch,
                    Objects::isNull,
                    TRANSIENT_MISS_RETRY_DELAY,
                    TRANSIENT_MISS_MAX_RETRIES
            );
        }

        @Override
        public CompletableFuture<@Nullable PersistentServer> persistentServerWithName(String name) {
            QueryKey key = QueryKey.of("persistentServer", "name", name);
            Supplier<CompletableFuture<PersistentServer>> fetch =
                    () -> fetchPersistentServerByNameFromController(name);
            return cache.fetchWithTransientMissRecovery(
                    key,
                    fetch,
                    Objects::isNull,
                    TRANSIENT_MISS_RETRY_DELAY,
                    TRANSIENT_MISS_MAX_RETRIES
            );
        }

        @Override
        public CompletableFuture<List<PersistentServer>> persistentServers(@Nullable PersistentServerQuery query) {
            QueryKey key = buildQueryKey(query);
            Supplier<CompletableFuture<List<PersistentServer>>> fetch =
                    () -> fetchPersistentServersFromController(query);
            return cache.fetch(key, fetch);
        }
    }

    private CompletableFuture<List<PersistentServer>> fetchPersistentServersFromController(
            @Nullable PersistentServerQuery query
    ) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ModelsListPersistentServersResponse response = executeQuery(query, null, null);
                List<ModelsPersistentServerSummary> servers = response.getPersistentServers();
                if (servers == null) {
                    return List.of();
                }

                return servers.stream()
                        .<PersistentServer>map(PersistentServerImpl::new)
                        .toList();
            } catch (ApiException e) {
                throw ApiErrors.from(e);
            }
        }, executor);
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

    private boolean persistentServerExistsWithBlueprint(String name, @Nullable String blueprintId) {
        if (blueprintId == null) {
            return false;
        }

        try {
            ModelsListPersistentServersResponse response = executeQuery(null, null, name);
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

    private CompletableFuture<PersistentServer> fetchPersistentServerByIdFromController(String id) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ModelsListPersistentServersResponse response = executeQuery(null, id, null);
                List<ModelsPersistentServerSummary> servers = response.getPersistentServers();
                if (servers == null) {
                    return null;
                }

                return servers.stream()
                        .filter(summary -> id.equals(summary.getPersistentServerId()))
                        .findFirst()
                        .<PersistentServer>map(PersistentServerImpl::new)
                        .orElse(null);
            } catch (ApiException e) {
                throw ApiErrors.from(e);
            }
        }, executor);
    }

    private CompletableFuture<PersistentServer> fetchPersistentServerByNameFromController(String name) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ModelsListPersistentServersResponse response = executeQuery(null, null, name);
                List<ModelsPersistentServerSummary> servers = response.getPersistentServers();
                if (servers == null) {
                    return null;
                }

                return servers.stream()
                        .filter(summary -> name.equals(summary.getName()))
                        .findFirst()
                        .<PersistentServer>map(PersistentServerImpl::new)
                        .orElse(null);
            } catch (ApiException e) {
                throw ApiErrors.from(e);
            }
        }, executor);
    }

    private ModelsListPersistentServersResponse executeQuery(@Nullable PersistentServerQuery query,
                                                              @Nullable String persistentServerId,
                                                              @Nullable String name) throws ApiException {
        String tags = query == null || query.getTags() == null || query.getTags().isEmpty()
                ? null
                : String.join(",", query.getTags());
        return persistentServersApi.listPersistentServers(
                options.getNetworkId(),
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

                ModelsCreatePersistentServerResponse response;
                try {
                    response = persistentServersApi.createPersistentServer(
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

                // Invalidate list caches (new persistent server created)
                cache.invalidateAll(QueryKey.of("persistentServers"));
                cache.invalidateAll(QueryKey.of("persistentServer", "name"));

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

                persistentServersApi.patchPersistentServer(
                        options.getNetworkId(),
                        id,
                        apiRequest
                );

                // Invalidate cache before re-fetching
                cache.invalidate(QueryKey.of("persistentServer", id));
                cache.invalidateAll(QueryKey.of("persistentServers"));
                cache.invalidateAll(QueryKey.of("persistentServer", "name"));

                // Re-fetch to get the updated object
                return getPersistentServerById(id).join();
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

                persistentServersApi.deletePersistentServer(
                        options.getNetworkId(),
                        id
                );

                // Invalidate this persistent server and list caches
                cache.invalidate(QueryKey.of("persistentServer", id));
                cache.invalidateAll(QueryKey.of("persistentServers"));
                cache.invalidateAll(QueryKey.of("persistentServer", "name"));

                inlineBlueprintSupport.deleteBlueprint(blueprintId);
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

                ModelsPatchPropertiesResponse response = persistentServersApi.patchPersistentServerProperties(
                        options.getNetworkId(),
                        id,
                        request
                );

                // Invalidate persistent server cache (properties changed)
                cache.invalidate(QueryKey.of("persistentServer", id));
                cache.invalidateAll(QueryKey.of("persistentServers"));
                cache.invalidateAll(QueryKey.of("persistentServer", "name"));

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

                ModelsDeletePropertiesResponse response = persistentServersApi.deletePersistentServerProperties(
                        options.getNetworkId(),
                        id,
                        request
                );

                // Invalidate persistent server cache (properties changed)
                cache.invalidate(QueryKey.of("persistentServer", id));
                cache.invalidateAll(QueryKey.of("persistentServers"));
                cache.invalidateAll(QueryKey.of("persistentServer", "name"));

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

    private boolean sourceReferencesBlueprint(@Nullable ModelsSourceConfig source, String blueprintId) {
        return source != null && blueprintId.equals(source.getBlueprint());
    }

    @Nullable
    private String findAttachedBlueprintId(String persistentServerId) throws ApiException {
        ModelsListPersistentServersResponse response = executeQuery(null, persistentServerId, null);
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
