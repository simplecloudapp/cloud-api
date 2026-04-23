package app.simplecloud.api.internal.group;

import app.simplecloud.api.CloudApiOptions;
import app.simplecloud.api.cache.QueryCache;
import app.simplecloud.api.cache.QueryKey;
import app.simplecloud.api.blueprint.CreateBlueprintRequest;
import app.simplecloud.api.group.*;
import app.simplecloud.api.internal.blueprint.InlineBlueprintSupport;
import app.simplecloud.api.internal.create.CreateRequestDefaults;
import app.simplecloud.api.internal.web.ApiClients;
import app.simplecloud.api.web.ApiException;
import app.simplecloud.api.web.apis.BlueprintsApi;
import app.simplecloud.api.web.apis.ServerGroupsApi;
import app.simplecloud.api.web.models.*;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class GroupApiImpl implements GroupApi {
    private static final Duration TRANSIENT_MISS_RETRY_DELAY = Duration.ofMillis(200);
    private static final int TRANSIENT_MISS_MAX_RETRIES = 2;

    private final CloudApiOptions options;
    private final ServerGroupsApi serverGroupsApi;
    private final InlineBlueprintSupport inlineBlueprintSupport;
    private final CreateRequestDefaults createRequestDefaults;
    private final QueryCache cache;

    public GroupApiImpl(CloudApiOptions options, QueryCache cache) {
        this(options, cache, createServerGroupsApi(options), createBlueprintsApi(options));
    }

    GroupApiImpl(CloudApiOptions options,
                 QueryCache cache,
                 ServerGroupsApi serverGroupsApi,
                 BlueprintsApi blueprintsApi) {
        this.options = options;
        this.cache = cache;
        this.serverGroupsApi = serverGroupsApi;
        this.inlineBlueprintSupport = new InlineBlueprintSupport(options, blueprintsApi);
        this.createRequestDefaults = new CreateRequestDefaults();
    }

    @Override
    public CompletableFuture<GroupStartQueue> getServerStartQueue() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ModelsListServerGroupStartQueueResponse response = serverGroupsApi.v0ServerGroupsStartQueueGet(
                        options.getNetworkId(),
                        options.getNetworkSecret()
                );

                List<GroupStartQueueEntry> items = response.getItems() == null
                        ? List.of()
                        : response.getItems().stream()
                        .map(this::convertStartQueueEntry)
                        .toList();

                return new GroupStartQueue(
                        intValue(response.getCount()),
                        intValue(response.getFailedStarts()),
                        items,
                        intValue(response.getQueuedStarts()),
                        intValue(response.getTotalStarts())
                );
            } catch (ApiException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> requestServerStart(String serverGroupId) {
        return CompletableFuture.runAsync(() -> {
            String normalizedServerGroupId = normalize(serverGroupId);
            if (normalizedServerGroupId == null) {
                throw new IllegalArgumentException("serverGroupId must not be blank");
            }

            try {
                ModelsQueueServerGroupStartRequest request = new ModelsQueueServerGroupStartRequest();
                request.setServerGroupId(normalizedServerGroupId);

                serverGroupsApi.v0ServerGroupsStartQueuePost(
                        options.getNetworkId(),
                        options.getNetworkSecret(),
                        new V0ServerGroupsStartQueuePostRequest(request)
                );
            } catch (ApiException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> clearServerStartQueue(String serverGroupId) {
        return CompletableFuture.runAsync(() -> {
            String normalizedServerGroupId = normalize(serverGroupId);
            if (normalizedServerGroupId == null) {
                throw new IllegalArgumentException("serverGroupId must not be blank");
            }

            try {
                serverGroupsApi.v0ServerGroupsStartQueueDelete(
                        options.getNetworkId(),
                        options.getNetworkSecret(),
                        normalizedServerGroupId
                );
            } catch (ApiException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public CompletableFuture<Group> getGroupByName(String name) {
        QueryKey key = QueryKey.of("group", "name", name);

        return cache.fetchWithTransientMissRecovery(
                key,
                () -> fetchGroupByNameFromController(name),
                group -> group == null,
                TRANSIENT_MISS_RETRY_DELAY,
                TRANSIENT_MISS_MAX_RETRIES
        ).thenApply(group -> {
            if (group == null) {
                throw new RuntimeException("Group not found: " + name);
            }
            return group;
        });
    }

    @Override
    public CompletableFuture<Group> getGroupById(String id) {
        QueryKey key = QueryKey.of("group", id);

        return cache.fetchWithTransientMissRecovery(
                key,
                () -> fetchGroupByIdFromController(id),
                group -> group == null,
                TRANSIENT_MISS_RETRY_DELAY,
                TRANSIENT_MISS_MAX_RETRIES
        ).thenApply(group -> {
            if (group == null) {
                throw new RuntimeException("Group not found: " + id);
            }
            return group;
        });
    }

    @Override
    public CompletableFuture<List<Group>> getAllGroups(@org.jetbrains.annotations.Nullable GroupQuery query) {
        QueryKey key = buildQueryKey(query);

        return cache.fetch(key, () -> CompletableFuture.supplyAsync(() -> {
            try {
                ModelsListServerGroupsResponse response = executeQuery(query);
                List<ModelsServerGroupSummary> groups = response.getServerGroups();
                if (groups == null) {
                    return List.of();
                }

                List<Group> result = groups.stream()
                        .<Group>map(GroupImpl::new)
                        .toList();

                if (query != null) {
                    result = result.stream()
                            .filter(group -> query.getType() == null || query.getType().equals(group.getType()))
                            .filter(group -> query.getTag() == null ||
                                    (group.getTags() != null && group.getTags().contains(query.getTag())))
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

    private ModelsListServerGroupsResponse executeQuery(@org.jetbrains.annotations.Nullable GroupQuery query) throws ApiException {
        return serverGroupsApi.v0ServerGroupsGet(
                this.options.getNetworkId(),
                this.options.getNetworkSecret(),
                null
        );
    }

    private boolean groupExistsWithBlueprint(String name, @Nullable String blueprintId) {
        if (blueprintId == null) {
            return false;
        }

        try {
            ModelsListServerGroupsResponse response = executeQuery(null);
            List<ModelsServerGroupSummary> groups = response.getServerGroups();
            if (groups == null) {
                return false;
            }

            return groups.stream()
                    .anyMatch(summary -> name.equals(summary.getName()) && sourceReferencesBlueprint(summary.getSource(), blueprintId));
        } catch (ApiException e) {
            throw new RuntimeException(e);
        }
    }

    private CompletableFuture<Group> fetchGroupByNameFromController(String name) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ModelsListServerGroupsResponse response = executeQuery(null);
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
                throw new RuntimeException(e);
            }
        });
    }

    private CompletableFuture<Group> fetchGroupByIdFromController(String id) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ModelsListServerGroupsResponse response = executeQuery(null);
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
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public CompletableFuture<Group> createGroup(CreateGroupRequest request) {
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
            DeploymentConfig effectiveDeployment = createRequestDefaults.defaultDeployment(request.getDeployment());
            ScalingConfig effectiveScaling = createRequestDefaults.defaultGroupScaling(request.getScaling());

            inlineBlueprintSupport.validateInlineBlueprintSource(effectiveCreateBlueprint, request.getSource());
            String createdBlueprintId = null;
            try {
                createdBlueprintId = inlineBlueprintSupport.createBlueprint(request.getName(), effectiveCreateBlueprint);
                SourceConfig effectiveSource = createdBlueprintId != null
                        ? inlineBlueprintSupport.buildInlineBlueprintSource(request.getSource(), createdBlueprintId)
                        : request.getSource();

                ModelsCreateServerGroupRequest apiRequest = new ModelsCreateServerGroupRequest();
                apiRequest.setName(request.getName());
                apiRequest.setType(effectiveType.name());
                apiRequest.setMinMemory(request.getMinMemory());
                apiRequest.setMaxMemory(request.getMaxMemory());
                apiRequest.setMaxPlayers(effectiveMaxPlayers);
                apiRequest.setActive(effectiveActive);
                apiRequest.setPriority(request.getPriority());
                apiRequest.setProperties(effectiveProperties);
                apiRequest.setTags(effectiveTags);
                apiRequest.setDeployment(convertDeploymentConfig(effectiveDeployment));
                apiRequest.setScaling(convertScalingConfig(effectiveScaling));
                if (effectiveSource != null) {
                    apiRequest.setSource(convertSourceConfig(effectiveSource));
                }
                apiRequest.setWorkflows(convertWorkflowsConfig(effectiveWorkflows));

                ModelsCreateServerGroupResponse response;
                try {
                    response = serverGroupsApi.v0ServerGroupsPost(
                            this.options.getNetworkId(),
                            this.options.getNetworkSecret(),
                            new V0ServerGroupsPostRequest(apiRequest)
                    );
                } catch (ApiException e) {
                    String blueprintId = createdBlueprintId;
                    ApiException rollbackFailure = inlineBlueprintSupport.rollbackBlueprintAfterCreateFailure(
                            blueprintId,
                            e,
                            () -> groupExistsWithBlueprint(request.getName(), blueprintId)
                    );
                    if (rollbackFailure != null) {
                        RuntimeException failure = new RuntimeException(
                                "Failed to create group and rollback inline blueprint " + blueprintId,
                                e
                        );
                        failure.addSuppressed(rollbackFailure);
                        throw failure;
                    }
                    throw e;
                }

                // Invalidate group list caches (new group created)
                cache.invalidateAll(QueryKey.of("groups"));

                ModelsServerGroupSummary summary = new ModelsServerGroupSummary();
                summary.setServerGroupId(response.getServerGroupId());
                summary.setName(response.getName());
                summary.setType(response.getType());
                summary.setMinMemory(response.getMinMemory());
                summary.setMaxMemory(response.getMaxMemory());
                summary.setMaxPlayers(response.getMaxPlayers());
                summary.setActive(response.getActive());
                summary.setPriority(response.getPriority());
                summary.setDeployment(response.getDeployment());
                summary.setScaling(response.getScaling());
                summary.setSource(response.getSource());
                summary.setWorkflows(response.getWorkflows());
                summary.setProperties(response.getProperties());
                summary.setTags(response.getTags());
                summary.setCreatedAt(response.getCreatedAt());
                summary.setUpdatedAt(response.getUpdatedAt());

                return new GroupImpl(summary);
            } catch (ApiException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public CompletableFuture<Group> updateGroup(String id, UpdateGroupRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ModelsUpdateServerGroupRequest apiRequest = new ModelsUpdateServerGroupRequest();
                apiRequest.setName(request.getName());
                if (request.getType() != null) {
                    apiRequest.setType(request.getType().name());
                }
                apiRequest.setMinMemory(request.getMinMemory());
                apiRequest.setMaxMemory(request.getMaxMemory());
                apiRequest.setMaxPlayers(request.getMaxPlayers());
                apiRequest.setActive(request.getActive());
                apiRequest.setPriority(request.getPriority());
                apiRequest.setProperties(request.getProperties());
                apiRequest.setTags(request.getTags());

                if (request.getDeployment() != null) {
                    apiRequest.setDeployment(convertDeploymentConfig(request.getDeployment()));
                }
                if (request.getScaling() != null) {
                    apiRequest.setScaling(convertScalingConfig(request.getScaling()));
                }
                if (request.getSource() != null) {
                    apiRequest.setSource(convertSourceConfig(request.getSource()));
                }
                if (request.getWorkflows() != null) {
                    apiRequest.setWorkflows(convertWorkflowsConfig(request.getWorkflows()));
                }

                ModelsUpdateServerGroupResponse response = serverGroupsApi.v0ServerGroupsPut(
                        this.options.getNetworkId(),
                        this.options.getNetworkSecret(),
                        id,
                        new V0ServerGroupsPutRequest(apiRequest)
                );

                ModelsServerGroupSummary summary = new ModelsServerGroupSummary();
                summary.setServerGroupId(response.getServerGroupId());
                summary.setName(response.getName());
                summary.setType(response.getType());
                summary.setMinMemory(response.getMinMemory());
                summary.setMaxMemory(response.getMaxMemory());
                summary.setMaxPlayers(response.getMaxPlayers());
                summary.setActive(response.getActive());
                summary.setPriority(response.getPriority());
                summary.setDeployment(response.getDeployment());
                summary.setScaling(response.getScaling());
                summary.setSource(response.getSource());
                summary.setWorkflows(response.getWorkflows());
                summary.setProperties(response.getProperties());
                summary.setTags(response.getTags());
                summary.setCreatedAt(response.getCreatedAt());
                summary.setUpdatedAt(response.getUpdatedAt());

                Group group = new GroupImpl(summary);

                // Update cache with new data and invalidate list caches
                cache.set(QueryKey.of("group", id), group);
                cache.invalidateAll(QueryKey.of("groups"));

                return group;
            } catch (ApiException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private ModelsDeploymentConfig convertDeploymentConfig(DeploymentConfig config) {
        ModelsDeploymentConfig result = new ModelsDeploymentConfig();
        if (config.getStrategy() != null) {
            result.setStrategy(convertDeploymentStrategy(config.getStrategy()));
        }

        if (config.getHosts() != null && config.getHosts().length > 0) {
            for (DeploymentHost host : config.getHosts()) {
                ModelsDeploymentHost modelHost = new ModelsDeploymentHost();
                modelHost.setName(host.getName());
                modelHost.setPriority(host.getPriority());
                result.addHostsItem(modelHost);
            }
        }

        return result;
    }

    private String convertDeploymentStrategy(DeploymentStrategy strategy) {
        return switch (strategy) {
            case WHITELIST -> "whitelist";
            case BLACKLIST -> "blacklist";
            case RANDOM, PRIORITY, ROUND_ROBIN -> throw new IllegalArgumentException(
                    "DeploymentStrategy." + strategy.name() + " is not supported by the controller REST API. Supported strategies are WHITELIST and BLACKLIST."
            );
        };
    }

    private ModelsScalingConfig convertScalingConfig(ScalingConfig config) {
        ModelsScalingConfig result = new ModelsScalingConfig();
        result.setAvailableSlots(config.getAvailableSlots());
        result.setMaxServers(config.getMaxServers());
        result.setMinServers(config.getMinServers());
        result.setPlayerThreshold(java.math.BigDecimal.valueOf(config.getPlayerThreshold()));
        if (config.getScalingMode() != null) {
            result.setScalingMode(convertScalingMode(config.getScalingMode()));
        }

        if (config.getScaleDown() != null) {
            ModelsScaleDownConfig scaleDown = new ModelsScaleDownConfig();
            scaleDown.setIdleTime(config.getScaleDown().getIdleTime());
            scaleDown.setIgnorePlayers(config.getScaleDown().isIgnorePlayers());
            result.setScaleDown(scaleDown);
        }

        return result;
    }

    private ModelsScalingMode convertScalingMode(ScalingMode scalingMode) {
        return switch (scalingMode) {
            case SLOTS -> ModelsScalingMode.fromValue("SLOTS");
            case PLAYERS -> ModelsScalingMode.fromValue("SERVERS");
            case MANUAL -> throw new IllegalArgumentException(
                    "ScalingMode.MANUAL is not supported by the controller REST API. Supported modes are SLOTS and PLAYERS."
            );
        };
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

    private boolean sourceReferencesBlueprint(@Nullable ModelsSourceConfig source, String blueprintId) {
        return source != null && blueprintId.equals(source.getBlueprint());
    }

    @Override
    public CompletableFuture<Void> deleteGroup(String id) {
        return CompletableFuture.runAsync(() -> {
            try {
                serverGroupsApi.v0ServerGroupsDelete(
                        this.options.getNetworkId(),
                        this.options.getNetworkSecret(),
                        id
                );

                // Invalidate this group and list caches
                cache.invalidate(QueryKey.of("group", id));
                cache.invalidateAll(QueryKey.of("groups"));
            } catch (ApiException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public CompletableFuture<Map<String, Object>> updateGroupProperties(String id, Map<String, Object> properties) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ModelsPatchPropertiesRequest request = new ModelsPatchPropertiesRequest();
                request.setProperties(properties);

                ModelsPatchPropertiesResponse response = serverGroupsApi.v0ServerGroupsPropertiesPatch(
                        this.options.getNetworkId(),
                        this.options.getNetworkSecret(),
                        id,
                        new V0PersistentServersPropertiesPatchRequest(request)
                );

                // Invalidate group cache (properties changed)
                cache.invalidate(QueryKey.of("group", id));
                cache.invalidateAll(QueryKey.of("groups"));

                Map<String, Object> result = response.getProperties();
                return result != null ? result : new HashMap<>();
            } catch (ApiException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public CompletableFuture<Map<String, Object>> deleteGroupProperties(String id, List<String> keys) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ModelsDeletePropertiesRequest request = new ModelsDeletePropertiesRequest();
                request.setKeys(keys);

                ModelsDeletePropertiesResponse response = serverGroupsApi.v0ServerGroupsPropertiesDelete(
                        this.options.getNetworkId(),
                        this.options.getNetworkSecret(),
                        id,
                        new V0PersistentServersPropertiesDeleteRequest(request)
                );

                // Invalidate group cache (properties changed)
                cache.invalidate(QueryKey.of("group", id));
                cache.invalidateAll(QueryKey.of("groups"));

                Map<String, Object> result = response.getProperties();
                return result != null ? result : new HashMap<>();
            } catch (ApiException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private GroupStartQueueEntry convertStartQueueEntry(ModelsServerGroupStartQueueEntry entry) {
        List<GroupStartQueueItem> starts = entry.getStarts() == null
                ? List.of()
                : entry.getStarts().stream()
                .map(this::convertStartQueueItem)
                .toList();

        return new GroupStartQueueEntry(
                intValue(entry.getFailedStarts()),
                intValue(entry.getQueuedStarts()),
                entry.getServerGroupId(),
                entry.getServerGroupName(),
                starts,
                intValue(entry.getTotalStarts())
        );
    }

    private GroupStartQueueItem convertStartQueueItem(ModelsManualServerStartQueueItem item) {
        String statusValue = item.getStatus() == null ? null : item.getStatus().getValue();
        return new GroupStartQueueItem(
                item.getCreatedAt(),
                item.getFailureReason(),
                item.getId(),
                GroupStartQueueItemStatus.fromApiValue(statusValue)
        );
    }

    private int intValue(Integer value) {
        return value != null ? value : 0;
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }

    private static ServerGroupsApi createServerGroupsApi(CloudApiOptions options) {
        ServerGroupsApi api = new ServerGroupsApi();
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
