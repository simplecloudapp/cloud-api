package app.simplecloud.api.internal.persistentserver;

import app.simplecloud.api.CloudApiOptions;
import app.simplecloud.api.cache.QueryCache;
import app.simplecloud.api.cache.QueryKey;
import app.simplecloud.api.internal.web.ApiClients;
import app.simplecloud.api.group.SourceConfig;
import app.simplecloud.api.group.WorkflowsConfig;
import app.simplecloud.api.persistentserver.*;
import app.simplecloud.api.web.ApiException;
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
    private final QueryCache cache;

    public PersistentServerApiImpl(CloudApiOptions options, QueryCache cache) {
        this.options = options;
        this.cache = cache;
        this.persistentServersApi = new PersistentServersApi();
        this.persistentServersApi.setCustomBaseUrl(options.getControllerUrl());
        ApiClients.applyTimeouts(this.persistentServersApi.getApiClient(), options);
        if (options.getComponent() != null && !options.getComponent().isBlank()) {
            this.persistentServersApi.getApiClient().addDefaultHeader("X-SC-Component", options.getComponent());
        }
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
            try {
                ModelsCreatePersistentServerRequest apiRequest = new ModelsCreatePersistentServerRequest();
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

                ModelsCreatePersistentServerResponse response = persistentServersApi.v0PersistentServersPost(
                        options.getNetworkId(),
                        options.getNetworkSecret(),
                        new V0PersistentServersPostRequest(apiRequest)
                );

                // Invalidate list caches (new persistent server created)
                cache.invalidateAll(QueryKey.of("persistentServers"));

                // Re-fetch to get the full object
                return getPersistentServerById(response.getPersistentServerId()).join();
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
                        new V0PersistentServersPutRequest(apiRequest)
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
                        new V0PersistentServersPropertiesPatchRequest(request)
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
                        new V0PersistentServersPropertiesDeleteRequest(request)
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
}
