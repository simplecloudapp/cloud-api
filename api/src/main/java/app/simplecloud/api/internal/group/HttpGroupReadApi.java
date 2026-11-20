package app.simplecloud.api.internal.group;

import app.simplecloud.api.CloudApiOptions;
import app.simplecloud.api.cache.QueryCache;
import app.simplecloud.api.cache.QueryKey;
import app.simplecloud.api.group.Group;
import app.simplecloud.api.group.GroupQuery;
import app.simplecloud.api.internal.web.ApiErrors;
import app.simplecloud.api.web.ApiException;
import app.simplecloud.api.web.apis.ServerGroupsApi;
import app.simplecloud.api.web.models.ModelsListServerGroupsResponse;
import app.simplecloud.api.web.models.ModelsServerGroupSummary;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

final class HttpGroupReadApi implements InternalGroupReadApi {
    private static final Duration TRANSIENT_MISS_RETRY_DELAY = Duration.ofMillis(200);
    private static final int TRANSIENT_MISS_MAX_RETRIES = 2;

    private final CloudApiOptions options;
    private final ServerGroupsApi serverGroupsApi;
    private final QueryCache cache;
    private final Executor executor;

    HttpGroupReadApi(CloudApiOptions options, ServerGroupsApi serverGroupsApi, QueryCache cache, Executor executor) {
        this.options = options;
        this.serverGroupsApi = serverGroupsApi;
        this.cache = cache;
        this.executor = executor;
    }

    @Override
    public CompletableFuture<Optional<Group>> findGroupByName(String name) {
        QueryKey key = QueryKey.of("group", "name", name);

        return cache.fetchWithTransientMissRecovery(
                key,
                () -> fetchGroupByNameFromController(name),
                Objects::isNull,
                TRANSIENT_MISS_RETRY_DELAY,
                TRANSIENT_MISS_MAX_RETRIES
        ).thenApply(Optional::ofNullable);
    }

    @Override
    public CompletableFuture<Optional<Group>> findGroupById(String id) {
        QueryKey key = QueryKey.of("group", id);

        return cache.fetchWithTransientMissRecovery(
                key,
                () -> fetchGroupByIdFromController(id),
                Objects::isNull,
                TRANSIENT_MISS_RETRY_DELAY,
                TRANSIENT_MISS_MAX_RETRIES
        ).thenApply(Optional::ofNullable);
    }

    @Override
    public CompletableFuture<List<Group>> getAllGroups(@Nullable GroupQuery query) {
        QueryKey key = buildQueryKey(query);

        return cache.fetch(key, () -> CompletableFuture.supplyAsync(() -> {
            try {
                ModelsListServerGroupsResponse response = executeQuery(query);
                List<ModelsServerGroupSummary> groups = response.getServerGroups();
                if (groups == null) {
                    return List.of();
                }

                return groups.stream()
                        .<Group>map(GroupImpl::new)
                        .toList();
            } catch (ApiException e) {
                throw ApiErrors.from(e);
            }
        }, executor));
    }

    private CompletableFuture<Group> fetchGroupByNameFromController(String name) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ModelsListServerGroupsResponse response = executeQuery(null, null, name);
                List<ModelsServerGroupSummary> groups = response.getServerGroups();
                if (groups == null) {
                    return null;
                }

                return groups.stream()
                        .filter(summary -> name.equals(summary.getName()))
                        .findFirst()
                        .<Group>map(GroupImpl::new)
                        .orElse(null);
            } catch (ApiException e) {
                throw ApiErrors.from(e);
            }
        }, executor);
    }

    private ModelsListServerGroupsResponse executeQuery(@org.jetbrains.annotations.Nullable GroupQuery query) throws ApiException {
        return executeQuery(query, null, null);
    }

    ModelsListServerGroupsResponse executeQuery(@Nullable GroupQuery query,
                                                @Nullable String serverGroupId,
                                                @Nullable String name) throws ApiException {
        return serverGroupsApi.listServerGroups(
                this.options.getNetworkId(),
                serverGroupId,
                name,
                query != null && query.getType() != null ? query.getType().name() : null,
                query != null ? query.getTag() : null,
                null,
                null,
                null,
                query != null ? query.getLimit() : null,
                null
        );
    }

    private CompletableFuture<Group> fetchGroupByIdFromController(String id) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ModelsListServerGroupsResponse response = executeQuery(null, id, null);
                List<ModelsServerGroupSummary> groups = response.getServerGroups();
                if (groups == null) {
                    return null;
                }

                return groups.stream()
                        .filter(summary -> id.equals(summary.getServerGroupId()))
                        .findFirst()
                        .<Group>map(GroupImpl::new)
                        .orElse(null);
            } catch (ApiException e) {
                throw ApiErrors.from(e);
            }
        }, executor);
    }

    private QueryKey buildQueryKey(@org.jetbrains.annotations.Nullable GroupQuery query) {
        if (query == null) {
            return QueryKey.of("groups");
        }
        return QueryKey.of("groups", "query",
                query.getType(),
                query.getTag(),
                query.getLimit()
        );
    }
}
