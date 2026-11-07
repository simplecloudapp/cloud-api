package app.simplecloud.api.internal.blueprint;

import app.simplecloud.api.CloudApiOptions;
import app.simplecloud.api.blueprint.Blueprint;
import app.simplecloud.api.blueprint.BlueprintApi;
import app.simplecloud.api.blueprint.CreateBlueprintRequest;
import app.simplecloud.api.blueprint.UpdateBlueprintRequest;
import app.simplecloud.api.internal.web.ApiExceptions;
import app.simplecloud.api.web.ApiClient;
import app.simplecloud.api.web.ApiException;
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
    private final CloudApiOptions options;
    private final BlueprintsApi blueprintsApi;
    private final InlineBlueprintSupport inlineBlueprintSupport;
    private final Executor executor;

    public BlueprintApiImpl(CloudApiOptions options, ApiClient httpClient, Executor executor) {
        this.options = options;
        this.blueprintsApi = new BlueprintsApi(httpClient);
        this.blueprintsApi.setCustomBaseUrl(options.getControllerUrl());
        this.inlineBlueprintSupport = new InlineBlueprintSupport(options, blueprintsApi);
        this.executor = executor;
    }

    @Override
    public CompletableFuture<List<Blueprint>> getAllBlueprints() {
        return CompletableFuture.supplyAsync(
                () -> listBlueprints().stream().<Blueprint>map(BlueprintImpl::from).toList(), executor);
    }

    @Override
    public CompletableFuture<Blueprint> getBlueprintById(String id) {
        return findBlueprintById(id).thenApply(blueprint -> blueprint.orElseThrow(
                () -> ApiExceptions.notFound("Blueprint with ID '" + id + "' not found")));
    }

    @Override
    public CompletableFuture<Optional<Blueprint>> findBlueprintById(String id) {
        return findFirst(summary -> Objects.equals(id, summary.getBlueprintId()));
    }

    @Override
    public CompletableFuture<Blueprint> getBlueprintByName(String name) {
        return findBlueprintByName(name).thenApply(blueprint -> blueprint.orElseThrow(
                () -> ApiExceptions.notFound("Blueprint '" + name + "' not found")));
    }

    @Override
    public CompletableFuture<Optional<Blueprint>> findBlueprintByName(String name) {
        return findFirst(summary -> Objects.equals(name, summary.getName()));
    }

    @Override
    public CompletableFuture<Blueprint> createBlueprint(String name, CreateBlueprintRequest request) {
        return CompletableFuture.supplyAsync(
                () -> {
                    Objects.requireNonNull(name, "name");
                    Objects.requireNonNull(request, "request");
                    try {
                        return BlueprintImpl.from(inlineBlueprintSupport.create(name, request));
                    } catch (ApiException e) {
                        throw ApiExceptions.from(e);
                    }
                },
                executor);
    }

    @Override
    public CompletableFuture<Blueprint> updateBlueprint(String id, UpdateBlueprintRequest request) {
        return CompletableFuture.supplyAsync(
                () -> {
                    ModelsPatchBlueprintRequest apiRequest = new ModelsPatchBlueprintRequest();
                    apiRequest.setName(request.getName());
                    apiRequest.setConfigurator(request.getConfigurator());
                    apiRequest.setMinecraftVersion(request.getMinecraftVersion());
                    apiRequest.setServerSoftware(request.getServerSoftware());
                    apiRequest.setServerUrl(request.getServerUrl());
                    apiRequest.setSoftwareVersion(request.getSoftwareVersion());
                    apiRequest.setWorkflowSteps(request.getWorkflowSteps());
                    if (request.getRuntimeConfig() != null) {
                        apiRequest.setRuntimeConfig(InlineBlueprintSupport.toModel(request.getRuntimeConfig()));
                    }
                    try {
                        return BlueprintImpl.from(blueprintsApi.patchBlueprint(options.getNetworkId(), id, apiRequest));
                    } catch (ApiException e) {
                        throw ApiExceptions.from(e);
                    }
                },
                executor);
    }

    @Override
    public CompletableFuture<Void> deleteBlueprint(String id) {
        return CompletableFuture.runAsync(
                () -> {
                    try {
                        blueprintsApi.deleteBlueprint(options.getNetworkId(), id);
                    } catch (ApiException e) {
                        throw ApiExceptions.from(e);
                    }
                },
                executor);
    }

    private CompletableFuture<Optional<Blueprint>> findFirst(Predicate<ModelsBlueprintSummary> filter) {
        return CompletableFuture.supplyAsync(
                () -> listBlueprints().stream().filter(filter).findFirst().<Blueprint>map(BlueprintImpl::from),
                executor);
    }

    private List<ModelsBlueprintSummary> listBlueprints() {
        try {
            List<ModelsBlueprintSummary> blueprints =
                    blueprintsApi.listBlueprints(options.getNetworkId()).getBlueprints();
            return blueprints == null ? List.of() : blueprints;
        } catch (ApiException e) {
            throw ApiExceptions.from(e);
        }
    }
}
