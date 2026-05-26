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
import app.simplecloud.api.internal.web.ApiClients;
import app.simplecloud.api.persistentserver.*;
import app.simplecloud.api.web.ApiException;
import app.simplecloud.api.web.apis.BlueprintsApi;
import app.simplecloud.api.web.apis.PersistentServersApi;
import app.simplecloud.api.web.models.*;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class PersistentServerApiImpl implements PersistentServerApi {
    private static final Duration TRANSIENT_MISS_RETRY_DELAY = Duration.ofMillis(200);
    private static final int TRANSIENT_MISS_MAX_RETRIES = 2;

    private final CloudApiOptions options;
    private final PersistentServersApi persistentServersApi;
    private final InlineBlueprintSupport inlineBlueprintSupport;
    private final CreateRequestDefaults createRequestDefaults;
    private final QueryCache cache;

    public PersistentServerApiImpl(CloudApiOptions options, QueryCache cache) {
        this(options, cache, createPersistentServersApi(options), createBlueprintsApi(options));
    }

    PersistentServerApiImpl(CloudApiOptions options,
                            QueryCache cache,
                            PersistentServersApi persistentServersApi,
                            BlueprintsApi blueprintsApi) {
        this.options = options;
        this.cache = cache;
        this.persistentServersApi = persistentServersApi;
        this.inlineBlueprintSupport = new InlineBlueprintSupport(options, blueprintsApi);
        this.createRequestDefaults = new CreateRequestDefaults();
    }

    PersistentServerApiImpl(CloudApiOptions options,
                            QueryCache cache,
                            PersistentServersApi persistentServersApi,
                            BlueprintsApi blueprintsApi,
                            InlineBlueprintSupport.ServerUrlResolver serverUrlResolver) {
        this.options = options;
        this.cache = cache;
        this.persistentServersApi = persistentServersApi;
        this.inlineBlueprintSupport = new InlineBlueprintSupport(options, blueprintsApi, serverUrlResolver);
        this.createRequestDefaults = new CreateRequestDefaults();
    }

    @Override
    public CompletableFuture<PersistentServer> getPersistentServerById(String id) {
        QueryKey key = QueryKey.of("persistentServer", id);

        return cache.fetchWithTransientMissRecovery(
                key,
                () -> fetchPersistentServerByIdFromController(id),
                persistentServer -> persistentServer == null,
                TRANSIENT_MISS_RETRY_DELAY,
                TRANSIENT_MISS_MAX_RETRIES
        ).thenApply(persistentServer -> {
            if (persistentServer == null) {
                throw new RuntimeException("Persistent server not found: " + id);
            }
            return persistentServer;
        });
    }

    @Override
    public CompletableFuture<PersistentServer> getPersistentServerByName(String name) {
        QueryKey key = QueryKey.of("persistentServer", "name", name);

        return cache.fetchWithTransientMissRecovery(
                key,
                () -> fetchPersistentServerByNameFromController(name),
                persistentServer -> persistentServer == null,
                TRANSIENT_MISS_RETRY_DELAY,
                TRANSIENT_MISS_MAX_RETRIES
        ).thenApply(persistentServer -> {
            if (persistentServer == null) {
                throw new RuntimeException("Persistent server not found: " + name);
            }
            return persistentServer;
        });
    }

    @Override
    public CompletableFuture<List<PersistentServer>> getAllPersistentServers(@Nullable PersistentServerQuery query) {
        QueryKey key = buildQueryKey(query);

        return cache.fetch(key, () -> CompletableFuture.supplyAsync(() -> {
            try {
                ModelsListPersistentServersResponse response = persistentServersApi.v0PersistentServersGet(
                        options.getNetworkId(),
                        options.getNetworkSecret()
                );
                List<ModelsPersistentServerSummary> servers = response.getPersistentServers();
                if (servers == null) {
                    return List.of();
                }

                List<PersistentServer> result = servers.stream()
                        .<PersistentServer>map(PersistentServerImpl::new)
                        .toList();

                if (query != null) {
                    result = result.stream()
                            .filter(ps -> query.getActive() == null || query.getActive().equals(ps.isActive()))
                            .filter(ps -> query.getServerhostId() == null || query.getServerhostId().equals(ps.getServerhostId()))
                            .filter(ps -> query.getTags() == null || query.getTags().isEmpty() ||
                                    (ps.getTags() != null && query.getTags().stream().anyMatch(ps.getTags()::contains)))
                            .toList();

                    if (query.getLimit() != null && result.size() > query.getLimit()) {
                        result = result.subList(0, query.getLimit());
                    }
                }

                return result;
            } catch (ApiException e) {
                throw new RuntimeException(e);
            }
        }));
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
            ModelsListPersistentServersResponse response = persistentServersApi.v0PersistentServersGet(
                    options.getNetworkId(),
                    options.getNetworkSecret()
            );
            List<ModelsPersistentServerSummary> servers = response.getPersistentServers();
            if (servers == null) {
                return false;
            }

            return servers.stream()
                    .anyMatch(summary -> name.equals(summary.getName()) && sourceReferencesBlueprint(summary.getSource(), blueprintId));
        } catch (ApiException e) {
            throw new RuntimeException(e);
        }
    }

    private CompletableFuture<PersistentServer> fetchPersistentServerByIdFromController(String id) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ModelsListPersistentServersResponse response = persistentServersApi.v0PersistentServersGet(
                        options.getNetworkId(),
                        options.getNetworkSecret()
                );
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
                throw new RuntimeException(e);
            }
        });
    }

    private CompletableFuture<PersistentServer> fetchPersistentServerByNameFromController(String name) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ModelsListPersistentServersResponse response = persistentServersApi.v0PersistentServersGet(
                        options.getNetworkId(),
                        options.getNetworkSecret()
                );
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
                throw new RuntimeException(e);
            }
        });
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
            String createdBlueprintId = null;
            try {
                createdBlueprintId = inlineBlueprintSupport.createBlueprint(request.getName(), effectiveCreateBlueprint);
                SourceConfig effectiveSource = createdBlueprintId != null
                        ? inlineBlueprintSupport.buildInlineBlueprintSource(request.getSource(), createdBlueprintId)
                        : request.getSource();

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
                apiRequest.setType(effectiveType.name());
                if (effectiveSource != null) {
                    apiRequest.setSource(convertSourceConfig(effectiveSource));
                }
                apiRequest.setWorkflows(convertWorkflowsConfig(effectiveWorkflows));

                ModelsCreatePersistentServerResponse response;
                try {
                    response = persistentServersApi.v0PersistentServersPost(
                            options.getNetworkId(),
                            options.getNetworkSecret(),
                            apiRequest
                    );
                } catch (ApiException e) {
                    String blueprintId = createdBlueprintId;
                    ApiException rollbackFailure = inlineBlueprintSupport.rollbackBlueprintAfterCreateFailure(
                            blueprintId,
                            e,
                            () -> persistentServerExistsWithBlueprint(request.getName(), blueprintId)
                    );
                    if (rollbackFailure != null) {
                        RuntimeException failure = new RuntimeException(
                                "Failed to create persistent server and rollback inline blueprint " + blueprintId,
                                e
                        );
                        failure.addSuppressed(rollbackFailure);
                        throw failure;
                    }
                    throw e;
                }

                // Invalidate list caches (new persistent server created)
                cache.invalidateAll(QueryKey.of("persistentServers"));

                return new PersistentServerImpl(toSummary(response));
            } catch (ApiException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public CompletableFuture<PersistentServer> updatePersistentServer(String id, UpdatePersistentServerRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ModelsUpdatePersistentServerRequest apiRequest = new ModelsUpdatePersistentServerRequest();
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
                    apiRequest.setType(request.getType().name());
                }
                if (request.getSource() != null) {
                    apiRequest.setSource(convertSourceConfig(request.getSource()));
                }
                if (request.getWorkflows() != null) {
                    apiRequest.setWorkflows(convertWorkflowsConfig(request.getWorkflows()));
                }

                persistentServersApi.v0PersistentServersPut(
                        options.getNetworkId(),
                        options.getNetworkSecret(),
                        id,
                        apiRequest
                );

                // Invalidate cache before re-fetching
                cache.invalidate(QueryKey.of("persistentServer", id));
                cache.invalidateAll(QueryKey.of("persistentServers"));

                // Re-fetch to get the updated object
                return getPersistentServerById(id).join();
            } catch (ApiException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> deletePersistentServer(String id) {
        return CompletableFuture.runAsync(() -> {
            try {
                persistentServersApi.v0PersistentServersDelete(
                        options.getNetworkId(),
                        options.getNetworkSecret(),
                        id
                );

                // Invalidate this persistent server and list caches
                cache.invalidate(QueryKey.of("persistentServer", id));
                cache.invalidateAll(QueryKey.of("persistentServers"));
            } catch (ApiException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public CompletableFuture<Map<String, Object>> updatePersistentServerProperties(String id, Map<String, Object> properties) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ModelsPatchPropertiesRequest request = new ModelsPatchPropertiesRequest();
                request.setProperties(properties);

                ModelsPatchPropertiesResponse response = persistentServersApi.v0PersistentServersPropertiesPatch(
                        options.getNetworkId(),
                        options.getNetworkSecret(),
                        id,
                        request
                );

                // Invalidate persistent server cache (properties changed)
                cache.invalidate(QueryKey.of("persistentServer", id));
                cache.invalidateAll(QueryKey.of("persistentServers"));

                Map<String, Object> result = response.getProperties();
                return result != null ? result : new HashMap<>();
            } catch (ApiException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public CompletableFuture<Map<String, Object>> deletePersistentServerProperties(String id, List<String> keys) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ModelsDeletePropertiesRequest request = new ModelsDeletePropertiesRequest();
                request.setKeys(keys);

                ModelsDeletePropertiesResponse response = persistentServersApi.v0PersistentServersPropertiesDelete(
                        options.getNetworkId(),
                        options.getNetworkSecret(),
                        id,
                        request
                );

                // Invalidate persistent server cache (properties changed)
                cache.invalidate(QueryKey.of("persistentServer", id));
                cache.invalidateAll(QueryKey.of("persistentServers"));

                Map<String, Object> result = response.getProperties();
                return result != null ? result : new HashMap<>();
            } catch (ApiException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private ModelsSourceConfig convertSourceConfig(SourceConfig config) {
        ModelsSourceConfig result = new ModelsSourceConfig();
        if (config.getType() != null) {
            result.setType(config.getType().name().toLowerCase());
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

    private static PersistentServersApi createPersistentServersApi(CloudApiOptions options) {
        PersistentServersApi api = new PersistentServersApi();
        api.setCustomBaseUrl(options.getControllerUrl());
        ApiClients.applyTimeouts(api.getApiClient(), options);
        if (options.getComponent() != null && !options.getComponent().isBlank()) {
            api.getApiClient().addDefaultHeader("X-SC-Component", options.getComponent());
        }
        return api;
    }

    private static BlueprintsApi createBlueprintsApi(CloudApiOptions options) {
        BlueprintsApi api = new BlueprintsApi();
        api.setCustomBaseUrl(options.getControllerUrl());
        ApiClients.applyTimeouts(api.getApiClient(), options);
        if (options.getComponent() != null && !options.getComponent().isBlank()) {
            api.getApiClient().addDefaultHeader("X-SC-Component", options.getComponent());
        }
        return api;
    }
}
