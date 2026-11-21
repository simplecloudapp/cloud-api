package app.simplecloud.api.internal.blueprint;

import app.simplecloud.api.blueprint.Blueprint;
import app.simplecloud.api.blueprint.BlueprintApi;
import app.simplecloud.api.blueprint.CreateBlueprintRequest;
import app.simplecloud.api.blueprint.UpdateBlueprintRequest;
import app.simplecloud.api.cache.QueryKey;
import app.simplecloud.api.internal.ApiContext;
import app.simplecloud.api.internal.create.CreateRequestDefaults;
import app.simplecloud.api.internal.web.ApiExceptions;
import app.simplecloud.api.internal.web.ApiCalls;
import app.simplecloud.api.internal.util.ValidationUtil;
import app.simplecloud.api.web.ApiException;
import app.simplecloud.api.web.apis.BlueprintsApi;
import app.simplecloud.api.web.models.ModelsBlueprintSummary;
import app.simplecloud.api.web.models.ModelsCreateBlueprintResponse;
import app.simplecloud.api.web.models.ModelsListBlueprintsResponse;
import app.simplecloud.api.web.models.ModelsUpdateBlueprintResponse;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

public class BlueprintApiImpl implements BlueprintApi {
    private final ApiContext context;
    private final BlueprintsApi blueprintsApi;
    private final BlueprintRequestConverter converter;
    private final CreateRequestDefaults createRequestDefaults = new CreateRequestDefaults();

    public BlueprintApiImpl(ApiContext context) {
        this(context, context.api(BlueprintsApi::new), new ManifestServerUrlResolver(context.options()));
    }

    BlueprintApiImpl(ApiContext context, BlueprintsApi blueprintsApi, InlineBlueprintSupport.ServerUrlResolver serverUrlResolver) {
        this.context = context;
        this.blueprintsApi = blueprintsApi;
        this.converter = new BlueprintRequestConverter(serverUrlResolver);
    }

    @Override
    public CompletableFuture<Blueprint> getBlueprintById(String id) {
        return findBlueprintById(id).thenApply(ApiExceptions.orNotFound("Blueprint with ID '" + id + "' not found"));
    }

    @Override
    public CompletableFuture<Optional<Blueprint>> findBlueprintById(String id) {
        if (!ValidationUtil.isUuid(id)) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return context.fetchWithTransientMissRecovery(
                QueryKey.of("blueprint", id),
                () -> fetchFirst(summary -> id.equalsIgnoreCase(summary.getBlueprintId())),
                Objects::isNull
        ).thenApply(Optional::ofNullable);
    }

    @Override
    public CompletableFuture<Blueprint> getBlueprintByName(String name) {
        return findBlueprintByName(name).thenApply(ApiExceptions.orNotFound("Blueprint '" + name + "' not found"));
    }

    @Override
    public CompletableFuture<Optional<Blueprint>> findBlueprintByName(String name) {
        return context.fetchWithTransientMissRecovery(
                QueryKey.of("blueprint", "name", name),
                () -> fetchFirst(summary -> name.equals(summary.getName())),
                Objects::isNull
        ).thenApply(Optional::ofNullable);
    }

    @Override
    public CompletableFuture<List<Blueprint>> getAllBlueprints() {
        return context.cache().fetch(QueryKey.of("blueprints"), () -> ApiCalls.supplyAsync(
                () -> listSummaries().stream().<Blueprint>map(BlueprintImpl::from).toList(),
                context.executor()
        ));
    }

    @Override
    public CompletableFuture<Blueprint> createBlueprint(CreateBlueprintRequest request) {
        return ApiCalls.supplyAsync(() -> {
            String name = requireName(request);
            CreateBlueprintRequest effective = createRequestDefaults.defaultBlueprint(request);
            ModelsCreateBlueprintResponse response = blueprintsApi.createBlueprint(
                    context.networkId(),
                    converter.toCreateRequest(name, effective)
            );
            invalidateLists();
            return BlueprintImpl.from(response);
        }, context.executor());
    }

    @Override
    public CompletableFuture<Blueprint> replaceBlueprint(String id, CreateBlueprintRequest request) {
        return ApiCalls.supplyAsync(() -> {
            String name = requireName(request);
            CreateBlueprintRequest effective = createRequestDefaults.defaultBlueprint(request);
            ModelsUpdateBlueprintResponse response = blueprintsApi.updateBlueprint(
                    context.networkId(),
                    id,
                    converter.toReplaceRequest(name, effective)
            );
            invalidate(id);
            return BlueprintImpl.from(response);
        }, context.executor());
    }

    @Override
    public CompletableFuture<Blueprint> updateBlueprint(String id, UpdateBlueprintRequest request) {
        return ApiCalls.supplyAsync(() -> {
            ModelsUpdateBlueprintResponse response = blueprintsApi.patchBlueprint(
                    context.networkId(),
                    id,
                    converter.toPatchRequest(request)
            );
            invalidate(id);
            return BlueprintImpl.from(response);
        }, context.executor());
    }

    @Override
    public CompletableFuture<Void> deleteBlueprint(String id) {
        return ApiCalls.runAsync(() -> {
            blueprintsApi.deleteBlueprint(context.networkId(), id);
            invalidate(id);
        }, context.executor());
    }

    private CompletableFuture<Blueprint> fetchFirst(Predicate<ModelsBlueprintSummary> filter) {
        return ApiCalls.supplyAsync(() -> listSummaries().stream()
                .filter(filter)
                .findFirst()
                .<Blueprint>map(BlueprintImpl::from)
                .orElse(null), context.executor());
    }

    private List<ModelsBlueprintSummary> listSummaries() throws ApiException {
        ModelsListBlueprintsResponse response = blueprintsApi.listBlueprints(context.networkId());
        return Objects.requireNonNullElse(response.getBlueprints(), List.of());
    }

    private String requireName(CreateBlueprintRequest request) {
        String name = BlueprintRequestConverter.normalize(request.getName());
        if (name == null) {
            throw new IllegalArgumentException("Blueprint name is required");
        }
        return name;
    }

    private void invalidate(String id) {
        InlineBlueprintSupport.invalidateCache(context.cache(), id);
        context.cache().invalidateAll(QueryKey.of("servers"));
        context.cache().invalidateAll(QueryKey.of("server"));
    }

    private void invalidateLists() {
        InlineBlueprintSupport.invalidateCache(context.cache(), null);
    }
}
