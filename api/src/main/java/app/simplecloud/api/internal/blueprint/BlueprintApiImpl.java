package app.simplecloud.api.internal.blueprint;

import app.simplecloud.api.CloudApiOptions;
import app.simplecloud.api.blueprint.Blueprint;
import app.simplecloud.api.blueprint.BlueprintApi;
import app.simplecloud.api.blueprint.CreateBlueprintRequest;
import app.simplecloud.api.blueprint.UpdateBlueprintRequest;
import app.simplecloud.api.cache.QueryCache;
import app.simplecloud.api.cache.QueryKey;
import app.simplecloud.api.internal.util.RestModelMapper;
import app.simplecloud.api.internal.web.ApiCalls;
import app.simplecloud.api.internal.web.ApiExceptions;
import app.simplecloud.api.web.ApiClient;
import app.simplecloud.api.web.apis.BlueprintsApi;
import app.simplecloud.api.web.models.ModelsBlueprintSummary;
import app.simplecloud.api.web.models.ModelsPatchBlueprintRequest;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Predicate;

public class BlueprintApiImpl implements BlueprintApi {
    private static final QueryKey ALL_BLUEPRINTS = QueryKey.of("blueprints");

    private final CloudApiOptions options;
    private final BlueprintsApi blueprintsApi;
    private final InlineBlueprintSupport inlineBlueprintSupport;
    private final QueryCache cache;
    private final Executor executor;

    public BlueprintApiImpl(CloudApiOptions options, QueryCache cache, ApiClient httpClient, Executor executor) {
        this.options = options;
        this.cache = cache;
        this.blueprintsApi = new BlueprintsApi(httpClient);
        this.inlineBlueprintSupport = new InlineBlueprintSupport(options, blueprintsApi);
        this.executor = executor;
    }

    @Override
    public CompletableFuture<List<Blueprint>> getAllBlueprints() {
        return cache.fetch(
                ALL_BLUEPRINTS,
                () -> ApiCalls.supplyAsync(executor, () -> {
                    List<ModelsBlueprintSummary> blueprints =
                            blueprintsApi.listBlueprints(options.getNetworkId()).getBlueprints();
                    return blueprints == null
                            ? List.<Blueprint>of()
                            : blueprints.stream()
                                    .<Blueprint>map(BlueprintImpl::new)
                                    .toList();
                }));
    }

    @Override
    public CompletableFuture<Blueprint> getBlueprintById(String id) {
        return findBlueprintById(id)
                .thenApply(blueprint -> blueprint.orElseThrow(
                        () -> ApiExceptions.notFound("Blueprint with ID '" + id + "' not found")));
    }

    @Override
    public CompletableFuture<Optional<Blueprint>> findBlueprintById(String id) {
        return findFirst(blueprint -> Objects.equals(id, blueprint.getBlueprintId()));
    }

    @Override
    public CompletableFuture<Blueprint> getBlueprintByName(String name) {
        return findBlueprintByName(name)
                .thenApply(blueprint ->
                        blueprint.orElseThrow(() -> ApiExceptions.notFound("Blueprint '" + name + "' not found")));
    }

    @Override
    public CompletableFuture<Optional<Blueprint>> findBlueprintByName(String name) {
        return findFirst(blueprint -> Objects.equals(name, blueprint.getName()));
    }

    @Override
    public CompletableFuture<Blueprint> createBlueprint(String name, CreateBlueprintRequest request) {
        return ApiCalls.supplyAsync(executor, () -> {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(request, "request");
            Blueprint blueprint =
                    new BlueprintImpl(RestModelMapper.toSummary(inlineBlueprintSupport.create(name, request)));
            cache.invalidateAll(ALL_BLUEPRINTS);
            return blueprint;
        });
    }

    @Override
    public CompletableFuture<Blueprint> updateBlueprint(String id, UpdateBlueprintRequest request) {
        return ApiCalls.supplyAsync(executor, () -> {
            ModelsPatchBlueprintRequest apiRequest = new ModelsPatchBlueprintRequest();
            apiRequest.setName(request.getName());
            apiRequest.setConfigurator(request.getConfigurator());
            apiRequest.setMinecraftVersion(request.getMinecraftVersion());
            apiRequest.setServerSoftware(request.getServerSoftware());
            apiRequest.setServerUrl(request.getServerUrl());
            apiRequest.setSoftwareVersion(request.getSoftwareVersion());
            apiRequest.setWorkflowSteps(request.getWorkflowSteps());
            if (request.getRuntimeConfig() != null) {
                apiRequest.setRuntimeConfig(RestModelMapper.toModel(request.getRuntimeConfig()));
            }
            Blueprint blueprint = new BlueprintImpl(
                    RestModelMapper.toSummary(blueprintsApi.patchBlueprint(options.getNetworkId(), id, apiRequest)));
            cache.invalidateAll(ALL_BLUEPRINTS);
            return blueprint;
        });
    }

    @Override
    public CompletableFuture<Void> deleteBlueprint(String id) {
        return ApiCalls.runAsync(executor, () -> {
            blueprintsApi.deleteBlueprint(options.getNetworkId(), id);
            cache.invalidateAll(ALL_BLUEPRINTS);
        });
    }

    private CompletableFuture<Optional<Blueprint>> findFirst(Predicate<Blueprint> filter) {
        return getAllBlueprints()
                .thenApply(blueprints -> blueprints.stream().filter(filter).findFirst());
    }
}
