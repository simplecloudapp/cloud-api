package app.simplecloud.api.internal.persistentserver;

import app.simplecloud.api.CloudApiOptions;
import app.simplecloud.api.group.SourceConfig;
import app.simplecloud.api.group.WorkflowsConfig;
import app.simplecloud.api.persistentserver.*;
import app.simplecloud.api.web.ApiException;
import app.simplecloud.api.web.apis.PersistentServersApi;
import app.simplecloud.api.web.models.*;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class PersistentServerApiImpl implements PersistentServerApi {

    private final CloudApiOptions options;
    private final PersistentServersApi persistentServersApi;

    public PersistentServerApiImpl(CloudApiOptions options) {
        this.options = options;
        this.persistentServersApi = new PersistentServersApi();
        this.persistentServersApi.setCustomBaseUrl(options.getControllerUrl());
    }

    @Override
    public CompletableFuture<PersistentServer> getPersistentServerById(String id) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ModelsListPersistentServersResponse response = persistentServersApi.v0PersistentServersGet(
                        options.getNetworkId(),
                        options.getNetworkSecret()
                );
                List<ModelsPersistentServerSummary> servers = response.getPersistentServers();
                if (servers != null) {
                    for (ModelsPersistentServerSummary summary : servers) {
                        if (id.equals(summary.getPersistentServerId())) {
                            return new PersistentServerImpl(summary);
                        }
                    }
                }
                throw new RuntimeException("Persistent server not found: " + id);
            } catch (ApiException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public CompletableFuture<PersistentServer> getPersistentServerByName(String name) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ModelsListPersistentServersResponse response = persistentServersApi.v0PersistentServersGet(
                        options.getNetworkId(),
                        options.getNetworkSecret()
                );
                List<ModelsPersistentServerSummary> servers = response.getPersistentServers();
                if (servers != null) {
                    for (ModelsPersistentServerSummary summary : servers) {
                        if (name.equals(summary.getName())) {
                            return new PersistentServerImpl(summary);
                        }
                    }
                }
                throw new RuntimeException("Persistent server not found: " + name);
            } catch (ApiException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public CompletableFuture<List<PersistentServer>> getAllPersistentServers(@Nullable PersistentServerQuery query) {
        return CompletableFuture.supplyAsync(() -> {
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
