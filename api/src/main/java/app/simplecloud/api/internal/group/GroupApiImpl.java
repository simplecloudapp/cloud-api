package app.simplecloud.api.internal.group;

import app.simplecloud.api.CloudApiOptions;
import app.simplecloud.api.group.*;
import app.simplecloud.api.web.ApiException;
import app.simplecloud.api.web.apis.ServerGroupsApi;
import app.simplecloud.api.web.models.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class GroupApiImpl implements GroupApi {

    private final CloudApiOptions options;
    private final ServerGroupsApi serverGroupsApi;

    public GroupApiImpl(CloudApiOptions options) {
        this.options = options;
        this.serverGroupsApi = new ServerGroupsApi();
        this.serverGroupsApi.setCustomBaseUrl(options.getControllerUrl());
    }

    @Override
    public CompletableFuture<Group> getGroupByName(String name) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ModelsListServerGroupsResponse response = executeQuery(null);
                List<ModelsServerGroupSummary> groups = response.getServerGroups();
                if (groups != null) {
                    for (ModelsServerGroupSummary summary : groups) {
                        if (name.equals(summary.getName())) {
                            return new GroupImpl(summary);
                        }
                    }
                }
                throw new RuntimeException("Group not found: " + name);
            } catch (ApiException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public CompletableFuture<Group> getGroupById(String id) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ModelsListServerGroupsResponse response = executeQuery(null);
                List<ModelsServerGroupSummary> groups = response.getServerGroups();
                if (groups != null) {
                    for (ModelsServerGroupSummary summary : groups) {
                        if (id.equals(summary.getServerGroupId())) {
                            return new GroupImpl(summary);
                        }
                    }
                }
                throw new RuntimeException("Group not found: " + id);
            } catch (ApiException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public CompletableFuture<List<Group>> getAllGroups(@org.jetbrains.annotations.Nullable GroupQuery query) {
        return CompletableFuture.supplyAsync(() -> {
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
        });
    }

    private ModelsListServerGroupsResponse executeQuery(@org.jetbrains.annotations.Nullable GroupQuery query) throws ApiException {
        return serverGroupsApi.v0ServerGroupsGet(
                this.options.getNetworkId(),
                this.options.getNetworkSecret(),
                null
        );
    }

    @Override
    public CompletableFuture<Group> createGroup(CreateGroupRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ModelsCreateServerGroupRequest apiRequest = new ModelsCreateServerGroupRequest();
                apiRequest.setName(request.getName());
                if (request.getType() != null) {
                    apiRequest.setType(request.getType().name());
                }
                apiRequest.setMinMemory(request.getMinMemory());
                apiRequest.setMaxMemory(request.getMaxMemory());
                apiRequest.setMaxPlayers(request.getMaxPlayers());
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

                ModelsCreateServerGroupResponse response = serverGroupsApi.v0ServerGroupsPost(
                        this.options.getNetworkId(),
                        this.options.getNetworkSecret(),
                        apiRequest
                );

                ModelsServerGroupSummary summary = new ModelsServerGroupSummary();
                summary.setServerGroupId(response.getServerGroupId());
                summary.setName(response.getName());
                summary.setType(response.getType());
                summary.setMinMemory(response.getMinMemory());
                summary.setMaxMemory(response.getMaxMemory());
                summary.setMaxPlayers(response.getMaxPlayers());
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
                        apiRequest
                );

                ModelsServerGroupSummary summary = new ModelsServerGroupSummary();
                summary.setServerGroupId(response.getServerGroupId());
                summary.setName(response.getName());
                summary.setType(response.getType());
                summary.setMinMemory(response.getMinMemory());
                summary.setMaxMemory(response.getMaxMemory());
                summary.setMaxPlayers(response.getMaxPlayers());
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

    private ModelsDeploymentConfig convertDeploymentConfig(DeploymentConfig config) {
        ModelsDeploymentConfig result = new ModelsDeploymentConfig();
        if (config.getStrategy() != null) {
            result.setStrategy(config.getStrategy().name());
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

    private ModelsScalingConfig convertScalingConfig(ScalingConfig config) {
        ModelsScalingConfig result = new ModelsScalingConfig();
        result.setAvailableSlots(config.getAvailableSlots());
        result.setMaxServers(config.getMaxServers());
        result.setMinServers(config.getMinServers());
        result.setPlayerThreshold(java.math.BigDecimal.valueOf(config.getPlayerThreshold()));
        if (config.getScalingMode() != null) {
            result.setScalingMode(config.getScalingMode().name());
        }

        if (config.getScaleDown() != null) {
            ModelsScaleDownConfig scaleDown = new ModelsScaleDownConfig();
            scaleDown.setIdleTime(config.getScaleDown().getIdleTime());
            scaleDown.setIgnorePlayers(config.getScaleDown().isIgnorePlayers());
            result.setScaleDown(scaleDown);
        }

        return result;
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

    @Override
    public CompletableFuture<Void> deleteGroup(String id) {
        return CompletableFuture.runAsync(() -> {
            try {
                serverGroupsApi.v0ServerGroupsDelete(
                        this.options.getNetworkId(),
                        this.options.getNetworkSecret(),
                        id
                );
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
                        request
                );

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
                        request
                );

                Map<String, Object> result = response.getProperties();
                return result != null ? result : new HashMap<>();
            } catch (ApiException e) {
                throw new RuntimeException(e);
            }
        });
    }
}

