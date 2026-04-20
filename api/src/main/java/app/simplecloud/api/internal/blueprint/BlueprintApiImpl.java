package app.simplecloud.api.internal.blueprint;

import app.simplecloud.api.CloudApiOptions;
import app.simplecloud.api.blueprint.Blueprint;
import app.simplecloud.api.blueprint.BlueprintApi;
import app.simplecloud.api.blueprint.CreateBlueprintRequest;
import app.simplecloud.api.blueprint.RuntimeConfig;
import app.simplecloud.api.blueprint.UpdateBlueprintRequest;
import app.simplecloud.api.cache.QueryCache;
import app.simplecloud.api.cache.QueryKey;
import app.simplecloud.api.web.ApiException;
import app.simplecloud.api.web.apis.BlueprintsApi;
import app.simplecloud.api.web.models.ModelsBlueprintSummary;
import app.simplecloud.api.web.models.ModelsCreateBlueprintRequest;
import app.simplecloud.api.web.models.ModelsCreateBlueprintResponse;
import app.simplecloud.api.web.models.ModelsListBlueprintsResponse;
import app.simplecloud.api.web.models.ModelsRuntimeConfig;
import app.simplecloud.api.web.models.ModelsUpdateBlueprintRequest;
import app.simplecloud.api.web.models.V0BlueprintsPostRequest;
import app.simplecloud.api.web.models.V0BlueprintsPutRequest;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class BlueprintApiImpl implements BlueprintApi {

    private final CloudApiOptions options;
    private final BlueprintsApi blueprintsApi;
    private final QueryCache cache;

    public BlueprintApiImpl(CloudApiOptions options, QueryCache cache) {
        this.options = options;
        this.cache = cache;
        this.blueprintsApi = new BlueprintsApi();
        this.blueprintsApi.setCustomBaseUrl(options.getControllerUrl());
        if (options.getComponent() != null && !options.getComponent().isBlank()) {
            this.blueprintsApi.getApiClient().addDefaultHeader("X-SC-Component", options.getComponent());
        }
    }

    @Override
    public CompletableFuture<Blueprint> getBlueprintById(String id) {
        QueryKey key = QueryKey.of("blueprint", id);

        return cache.fetch(key, () -> CompletableFuture.supplyAsync(() -> {
            try {
                ModelsListBlueprintsResponse response = listBlueprints();
                List<ModelsBlueprintSummary> blueprints = response.getBlueprints();
                if (blueprints != null) {
                    for (ModelsBlueprintSummary summary : blueprints) {
                        if (id.equals(summary.getBlueprintId())) {
                            return new BlueprintImpl(summary);
                        }
                    }
                }
                throw new RuntimeException("Blueprint not found: " + id);
            } catch (ApiException e) {
                throw new RuntimeException(e);
            }
        }));
    }

    @Override
    public CompletableFuture<Blueprint> getBlueprintByName(String name) {
        QueryKey key = QueryKey.of("blueprint", "name", name);

        return cache.fetch(key, () -> CompletableFuture.supplyAsync(() -> {
            try {
                ModelsListBlueprintsResponse response = listBlueprints();
                List<ModelsBlueprintSummary> blueprints = response.getBlueprints();
                if (blueprints != null) {
                    for (ModelsBlueprintSummary summary : blueprints) {
                        if (name.equals(summary.getName())) {
                            return new BlueprintImpl(summary);
                        }
                    }
                }
                throw new RuntimeException("Blueprint not found: " + name);
            } catch (ApiException e) {
                throw new RuntimeException(e);
            }
        }));
    }

    @Override
    public CompletableFuture<List<Blueprint>> getAllBlueprints() {
        QueryKey key = QueryKey.of("blueprints");

        return cache.fetch(key, () -> CompletableFuture.supplyAsync(() -> {
            try {
                ModelsListBlueprintsResponse response = listBlueprints();
                List<ModelsBlueprintSummary> blueprints = response.getBlueprints();
                if (blueprints == null) {
                    return List.of();
                }

                return blueprints.stream()
                        .<Blueprint>map(BlueprintImpl::new)
                        .toList();
            } catch (ApiException e) {
                throw new RuntimeException(e);
            }
        }));
    }

    @Override
    public CompletableFuture<Blueprint> createBlueprint(CreateBlueprintRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ModelsCreateBlueprintRequest apiRequest = new ModelsCreateBlueprintRequest();
                apiRequest.setName(request.getName());
                apiRequest.setConfigurator(request.getConfigurator());
                apiRequest.setMinecraftVersion(request.getMinecraftVersion());
                apiRequest.setServerSoftware(request.getServerSoftware());
                apiRequest.setServerUrl(request.getServerUrl());
                apiRequest.setSoftwareVersion(request.getSoftwareVersion());
                apiRequest.setWorkflowSteps(request.getWorkflowSteps());
                apiRequest.setRuntimeConfig(convertRuntimeConfig(request.getRuntimeConfig()));

                ModelsCreateBlueprintResponse response = blueprintsApi.v0BlueprintsPost(
                        options.getNetworkId(),
                        options.getNetworkSecret(),
                        new V0BlueprintsPostRequest(apiRequest)
                );

                cache.invalidateAll(QueryKey.of("blueprint"));
                cache.invalidateAll(QueryKey.of("blueprints"));

                return getBlueprintById(response.getBlueprintId()).join();
            } catch (ApiException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public CompletableFuture<Blueprint> updateBlueprint(String id, UpdateBlueprintRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ModelsUpdateBlueprintRequest apiRequest = new ModelsUpdateBlueprintRequest();
                apiRequest.setName(request.getName());
                apiRequest.setConfigurator(request.getConfigurator());
                apiRequest.setMinecraftVersion(request.getMinecraftVersion());
                apiRequest.setServerSoftware(request.getServerSoftware());
                apiRequest.setServerUrl(request.getServerUrl());
                apiRequest.setSoftwareVersion(request.getSoftwareVersion());
                apiRequest.setWorkflowSteps(request.getWorkflowSteps());
                apiRequest.setRuntimeConfig(convertRuntimeConfig(request.getRuntimeConfig()));

                blueprintsApi.v0BlueprintsPut(
                        options.getNetworkId(),
                        options.getNetworkSecret(),
                        id,
                        new V0BlueprintsPutRequest(apiRequest)
                );

                cache.invalidateAll(QueryKey.of("blueprint"));
                cache.invalidateAll(QueryKey.of("blueprints"));

                return getBlueprintById(id).join();
            } catch (ApiException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> deleteBlueprint(String id) {
        return CompletableFuture.runAsync(() -> {
            try {
                blueprintsApi.v0BlueprintsDelete(
                        options.getNetworkId(),
                        options.getNetworkSecret(),
                        id
                );

                cache.invalidateAll(QueryKey.of("blueprint"));
                cache.invalidateAll(QueryKey.of("blueprints"));
            } catch (ApiException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private ModelsListBlueprintsResponse listBlueprints() throws ApiException {
        return blueprintsApi.v0BlueprintsGet(
                options.getNetworkId(),
                options.getNetworkSecret()
        );
    }

    private ModelsRuntimeConfig convertRuntimeConfig(RuntimeConfig runtimeConfig) {
        if (runtimeConfig == null) {
            return null;
        }

        ModelsRuntimeConfig result = new ModelsRuntimeConfig();
        if (runtimeConfig.getType() != null) {
            result.setType(runtimeConfig.getType().name());
        }
        result.setWith(runtimeConfig.getWith());
        return result;
    }
}
