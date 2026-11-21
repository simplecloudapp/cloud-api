package app.simplecloud.api.internal.blueprint;

import app.simplecloud.api.CloudApiOptions;
import app.simplecloud.api.blueprint.Blueprint;
import app.simplecloud.api.blueprint.BlueprintApi;
import app.simplecloud.api.blueprint.CreateBlueprintRequest;
import app.simplecloud.api.blueprint.UpdateBlueprintRequest;
import app.simplecloud.api.cache.QueryCache;
import app.simplecloud.api.cache.QueryKey;
import app.simplecloud.api.internal.create.CreateRequestDefaults;
import app.simplecloud.api.internal.web.ApiErrors;
import app.simplecloud.api.internal.web.Identifiers;
import app.simplecloud.api.web.ApiClient;
import app.simplecloud.api.web.ApiException;
import app.simplecloud.api.web.apis.BlueprintsApi;
import app.simplecloud.api.web.models.ModelsBlueprintSummary;
import app.simplecloud.api.web.models.ModelsCreateBlueprintResponse;
import app.simplecloud.api.web.models.ModelsListBlueprintsResponse;
import app.simplecloud.api.web.models.ModelsUpdateBlueprintResponse;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Predicate;

public class BlueprintApiImpl implements BlueprintApi {
    private static final Duration TRANSIENT_MISS_RETRY_DELAY = Duration.ofMillis(200);
    private static final int TRANSIENT_MISS_MAX_RETRIES = 2;

    private final CloudApiOptions options;
    private final QueryCache cache;
    private final BlueprintsApi blueprintsApi;
    private final BlueprintRequestConverter converter;
    private final CreateRequestDefaults createRequestDefaults = new CreateRequestDefaults();
    private final Executor executor;

    public BlueprintApiImpl(CloudApiOptions options, QueryCache cache, ApiClient httpClient, Executor executor) {
        this(options, cache, createBlueprintsApi(options, httpClient), new ManifestServerUrlResolver(options), executor);
    }

    BlueprintApiImpl(CloudApiOptions options,
                     QueryCache cache,
                     BlueprintsApi blueprintsApi,
                     InlineBlueprintSupport.ServerUrlResolver serverUrlResolver) {
        this(options, cache, blueprintsApi, serverUrlResolver, ForkJoinPool.commonPool());
    }

    private BlueprintApiImpl(CloudApiOptions options,
                             QueryCache cache,
                             BlueprintsApi blueprintsApi,
                             InlineBlueprintSupport.ServerUrlResolver serverUrlResolver,
                             Executor executor) {
        this.options = options;
        this.cache = cache;
        this.blueprintsApi = blueprintsApi;
        this.converter = new BlueprintRequestConverter(serverUrlResolver);
        this.executor = executor;
    }

    @Override
    public CompletableFuture<Blueprint> getBlueprintById(String id) {
        return findBlueprintById(id).thenApply(blueprint -> blueprint.orElseThrow(
                () -> ApiErrors.notFound("Blueprint with ID '" + id + "' not found")
        ));
    }

    @Override
    public CompletableFuture<Optional<Blueprint>> findBlueprintById(String id) {
        if (!Identifiers.isUuid(id)) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return cache.fetchWithTransientMissRecovery(
                QueryKey.of("blueprint", id),
                () -> fetchFirst(summary -> id.equalsIgnoreCase(summary.getBlueprintId())),
                Objects::isNull,
                TRANSIENT_MISS_RETRY_DELAY,
                TRANSIENT_MISS_MAX_RETRIES
        ).thenApply(Optional::ofNullable);
    }

    @Override
    public CompletableFuture<Blueprint> getBlueprintByName(String name) {
        return findBlueprintByName(name).thenApply(blueprint -> blueprint.orElseThrow(
                () -> ApiErrors.notFound("Blueprint '" + name + "' not found")
        ));
    }

    @Override
    public CompletableFuture<Optional<Blueprint>> findBlueprintByName(String name) {
        return cache.fetchWithTransientMissRecovery(
                QueryKey.of("blueprint", "name", name),
                () -> fetchFirst(summary -> name.equals(summary.getName())),
                Objects::isNull,
                TRANSIENT_MISS_RETRY_DELAY,
                TRANSIENT_MISS_MAX_RETRIES
        ).thenApply(Optional::ofNullable);
    }

    @Override
    public CompletableFuture<List<Blueprint>> getAllBlueprints() {
        return cache.fetch(QueryKey.of("blueprints"), () -> CompletableFuture.supplyAsync(
                () -> listSummaries().stream().<Blueprint>map(BlueprintImpl::from).toList(),
                executor
        ));
    }

    @Override
    public CompletableFuture<Blueprint> createBlueprint(CreateBlueprintRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            String name = requireName(request);
            CreateBlueprintRequest effective = createRequestDefaults.defaultBlueprint(request);
            try {
                ModelsCreateBlueprintResponse response = blueprintsApi.createBlueprint(
                        options.getNetworkId(),
                        converter.toCreateRequest(name, effective)
                );
                invalidateLists();
                return BlueprintImpl.from(response);
            } catch (ApiException e) {
                throw ApiErrors.from(e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Blueprint> replaceBlueprint(String id, CreateBlueprintRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            String name = requireName(request);
            CreateBlueprintRequest effective = createRequestDefaults.defaultBlueprint(request);
            try {
                ModelsUpdateBlueprintResponse response = blueprintsApi.updateBlueprint(
                        options.getNetworkId(),
                        id,
                        converter.toReplaceRequest(name, effective)
                );
                invalidate(id);
                return BlueprintImpl.from(response);
            } catch (ApiException e) {
                throw ApiErrors.from(e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Blueprint> updateBlueprint(String id, UpdateBlueprintRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ModelsUpdateBlueprintResponse response = blueprintsApi.patchBlueprint(
                        options.getNetworkId(),
                        id,
                        converter.toPatchRequest(request)
                );
                invalidate(id);
                return BlueprintImpl.from(response);
            } catch (ApiException e) {
                throw ApiErrors.from(e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> deleteBlueprint(String id) {
        return CompletableFuture.runAsync(() -> {
            try {
                blueprintsApi.deleteBlueprint(options.getNetworkId(), id);
                invalidate(id);
            } catch (ApiException e) {
                throw ApiErrors.from(e);
            }
        }, executor);
    }

    private CompletableFuture<Blueprint> fetchFirst(Predicate<ModelsBlueprintSummary> filter) {
        return CompletableFuture.supplyAsync(() -> listSummaries().stream()
                .filter(filter)
                .findFirst()
                .<Blueprint>map(BlueprintImpl::from)
                .orElse(null), executor);
    }

    private List<ModelsBlueprintSummary> listSummaries() {
        try {
            ModelsListBlueprintsResponse response = blueprintsApi.listBlueprints(options.getNetworkId());
            List<ModelsBlueprintSummary> blueprints = response.getBlueprints();
            return blueprints != null ? blueprints : List.of();
        } catch (ApiException e) {
            throw ApiErrors.from(e);
        }
    }

    private String requireName(CreateBlueprintRequest request) {
        String name = BlueprintRequestConverter.normalize(request.getName());
        if (name == null) {
            throw new IllegalArgumentException("Blueprint name is required");
        }
        return name;
    }

    private void invalidate(String id) {
        InlineBlueprintSupport.invalidateCache(cache, id);
    }

    private void invalidateLists() {
        InlineBlueprintSupport.invalidateCache(cache, null);
    }

    private static BlueprintsApi createBlueprintsApi(CloudApiOptions options, ApiClient httpClient) {
        BlueprintsApi api = new BlueprintsApi(httpClient);
        api.setCustomBaseUrl(options.getControllerUrl());
        return api;
    }
}
