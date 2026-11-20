package app.simplecloud.api.internal.persistentserver;

import app.simplecloud.api.CloudApiOptions;
import app.simplecloud.api.cache.QueryCache;
import app.simplecloud.api.cache.QueryKey;
import app.simplecloud.api.internal.web.ApiErrors;
import app.simplecloud.api.persistentserver.PersistentServer;
import app.simplecloud.api.persistentserver.PersistentServerQuery;
import app.simplecloud.api.web.ApiException;
import app.simplecloud.api.web.apis.PersistentServersApi;
import app.simplecloud.api.web.models.ModelsListPersistentServersResponse;
import app.simplecloud.api.web.models.ModelsPersistentServerSummary;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

final class HttpPersistentServerReadApi implements InternalPersistentServerReadApi {
    private static final Duration TRANSIENT_MISS_RETRY_DELAY = Duration.ofMillis(200);
    private static final int TRANSIENT_MISS_MAX_RETRIES = 2;

    private final CloudApiOptions options;
    private final PersistentServersApi persistentServersApi;
    private final QueryCache cache;
    private final Executor executor;

    HttpPersistentServerReadApi(CloudApiOptions options,
                                  PersistentServersApi persistentServersApi,
                                  QueryCache cache,
                                  Executor executor) {
        this.options = options;
        this.persistentServersApi = persistentServersApi;
        this.cache = cache;
        this.executor = executor;
    }

    @Override
    public CompletableFuture<Optional<PersistentServer>> findPersistentServerById(String id) {
        QueryKey key = QueryKey.of("persistentServer", id);

        return cache.fetchWithTransientMissRecovery(
                key,
                () -> fetchPersistentServerByIdFromController(id),
                Objects::isNull,
                TRANSIENT_MISS_RETRY_DELAY,
                TRANSIENT_MISS_MAX_RETRIES
        ).thenApply(Optional::ofNullable);
    }

    @Override
    public CompletableFuture<Optional<PersistentServer>> findPersistentServerByName(String name) {
        QueryKey key = QueryKey.of("persistentServer", "name", name);

        return cache.fetchWithTransientMissRecovery(
                key,
                () -> fetchPersistentServerByNameFromController(name),
                Objects::isNull,
                TRANSIENT_MISS_RETRY_DELAY,
                TRANSIENT_MISS_MAX_RETRIES
        ).thenApply(Optional::ofNullable);
    }

    @Override
    public CompletableFuture<List<PersistentServer>> getAllPersistentServers(@Nullable PersistentServerQuery query) {
        QueryKey key = buildQueryKey(query);

        return cache.fetch(key, () -> CompletableFuture.supplyAsync(() -> {
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
        }, executor));
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

    ModelsListPersistentServersResponse executeQuery(@Nullable PersistentServerQuery query,
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
}
