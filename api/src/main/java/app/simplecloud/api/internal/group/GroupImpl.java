package app.simplecloud.api.internal.group;

import app.simplecloud.api.group.DeploymentConfig;
import app.simplecloud.api.group.Group;
import app.simplecloud.api.group.GroupServerType;
import app.simplecloud.api.group.ScalingConfig;
import app.simplecloud.api.group.SourceConfig;
import app.simplecloud.api.group.WorkflowsConfig;
import app.simplecloud.api.internal.util.ModelConversionUtil;
import app.simplecloud.api.web.models.ModelsBlueprintInfo;
import app.simplecloud.api.web.models.ModelsCreateServerGroupResponse;
import app.simplecloud.api.web.models.ModelsDeploymentConfig;
import app.simplecloud.api.web.models.ModelsScalingConfig;
import app.simplecloud.api.web.models.ModelsServerGroupInfo;
import app.simplecloud.api.web.models.ModelsServerGroupSummary;
import app.simplecloud.api.web.models.ModelsSourceConfig;
import app.simplecloud.api.web.models.ModelsSourceInfo;
import app.simplecloud.api.web.models.ModelsUpdateServerGroupResponse;
import app.simplecloud.api.web.models.ModelsWorkflowsConfig;
import com.google.gson.Gson;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

public record GroupImpl(
        @Nullable String serverGroupId,
        @Nullable String name,
        @Nullable String type,
        @Nullable Integer minMemory,
        @Nullable Integer maxMemory,
        @Nullable Integer maxPlayers,
        @Nullable Boolean active,
        @Nullable Integer priority,
        @Nullable DeploymentConfig deployment,
        @Nullable ScalingConfig scaling,
        @Nullable SourceConfig source,
        @Nullable WorkflowsConfig workflows,
        @Nullable Map<String, Object> properties,
        @Nullable List<String> tags,
        @Nullable String createdAt,
        @Nullable String updatedAt)
        implements Group {

    private static final Gson GSON = new Gson();

    public static GroupImpl from(ModelsServerGroupSummary summary) {
        return new GroupImpl(
                summary.getServerGroupId(),
                summary.getName(),
                summary.getType(),
                summary.getMinMemory(),
                summary.getMaxMemory(),
                summary.getMaxPlayers(),
                summary.getActive(),
                summary.getPriority(),
                ModelConversionUtil.fromModel(summary.getDeployment()),
                ModelConversionUtil.fromModel(summary.getScaling()),
                ModelConversionUtil.fromModel(summary.getSource()),
                ModelConversionUtil.fromModel(summary.getWorkflows()),
                summary.getProperties(),
                summary.getTags(),
                summary.getCreatedAt(),
                summary.getUpdatedAt());
    }

    public static GroupImpl from(ModelsCreateServerGroupResponse response) {
        return new GroupImpl(
                response.getServerGroupId(),
                response.getName(),
                response.getType(),
                response.getMinMemory(),
                response.getMaxMemory(),
                response.getMaxPlayers(),
                response.getActive(),
                response.getPriority(),
                ModelConversionUtil.fromModel(response.getDeployment()),
                ModelConversionUtil.fromModel(response.getScaling()),
                ModelConversionUtil.fromModel(response.getSource()),
                ModelConversionUtil.fromModel(response.getWorkflows()),
                response.getProperties(),
                response.getTags(),
                response.getCreatedAt(),
                response.getUpdatedAt());
    }

    public static GroupImpl from(ModelsUpdateServerGroupResponse response) {
        return new GroupImpl(
                response.getServerGroupId(),
                response.getName(),
                response.getType(),
                response.getMinMemory(),
                response.getMaxMemory(),
                response.getMaxPlayers(),
                response.getActive(),
                response.getPriority(),
                ModelConversionUtil.fromModel(response.getDeployment()),
                ModelConversionUtil.fromModel(response.getScaling()),
                ModelConversionUtil.fromModel(response.getSource()),
                ModelConversionUtil.fromModel(response.getWorkflows()),
                response.getProperties(),
                response.getTags(),
                response.getCreatedAt(),
                response.getUpdatedAt());
    }

    public static GroupImpl from(ModelsServerGroupInfo info) {
        return new GroupImpl(
                info.getId(),
                info.getName(),
                info.getType(),
                null,
                null,
                null,
                null,
                null,
                ModelConversionUtil.fromModel(deployment(info)),
                ModelConversionUtil.fromModel(fromMap(info.getScalingConfig(), ModelsScalingConfig.class)),
                ModelConversionUtil.fromModel(source(info.getSource())),
                ModelConversionUtil.fromModel(fromMap(info.getWorkflowsConfig(), ModelsWorkflowsConfig.class)),
                info.getProperties(),
                info.getTags(),
                info.getCreatedAt(),
                info.getUpdatedAt());
    }

    @Override
    public String getServerGroupId() {
        return require(serverGroupId, "Server group ID");
    }

    @Override
    public String getName() {
        return require(name, "Group name");
    }

    @Override
    public GroupServerType getType() {
        String value = require(type, "Group type");
        try {
            return GroupServerType.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Invalid group type: " + value, e);
        }
    }

    @Override
    public @Nullable Integer getMinMemory() {
        return minMemory;
    }

    @Override
    public @Nullable Integer getMaxMemory() {
        return maxMemory;
    }

    @Override
    public @Nullable Integer getMaxPlayers() {
        return maxPlayers;
    }

    @Override
    public @Nullable Boolean isActive() {
        return active;
    }

    @Override
    public @Nullable Integer getPriority() {
        return priority;
    }

    @Override
    public @Nullable DeploymentConfig getDeployment() {
        return deployment;
    }

    @Override
    public @Nullable ScalingConfig getScaling() {
        return scaling;
    }

    @Override
    public @Nullable SourceConfig getSource() {
        return source;
    }

    @Override
    public @Nullable WorkflowsConfig getWorkflows() {
        return workflows;
    }

    @Override
    public @Nullable Map<String, Object> getProperties() {
        return properties;
    }

    @Override
    public @Nullable List<String> getTags() {
        return tags;
    }

    @Override
    public String getCreatedAt() {
        return require(createdAt, "Created at timestamp");
    }

    @Override
    public String getUpdatedAt() {
        return require(updatedAt, "Updated at timestamp");
    }

    private static @Nullable ModelsDeploymentConfig deployment(ModelsServerGroupInfo info) {
        String strategy = info.getDeploymentStrategy();
        ModelsDeploymentConfig config = fromMap(info.getDeploymentConfig(), ModelsDeploymentConfig.class);
        if (config == null && strategy != null) {
            config = new ModelsDeploymentConfig();
        }
        if (config != null && config.getStrategy() == null && strategy != null) {
            config.setStrategy(strategy);
        }
        return config;
    }

    private static @Nullable ModelsSourceConfig source(@Nullable ModelsSourceInfo info) {
        if (info == null) {
            return null;
        }
        ModelsSourceConfig config = new ModelsSourceConfig();
        try {
            config.setType(ModelsSourceConfig.TypeEnum.fromValue(info.getType()));
        } catch (IllegalArgumentException ignored) {
        }
        ModelsBlueprintInfo blueprint = info.getBlueprint();
        if (blueprint != null) {
            config.setBlueprint(blueprint.getId());
        }
        config.setImage(info.getImage());
        return config;
    }

    private static <T> @Nullable T fromMap(@Nullable Map<String, Object> values, Class<T> type) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return GSON.fromJson(GSON.toJson(values), type);
    }

    private static <T> T require(@Nullable T value, String name) {
        if (value == null) {
            throw new IllegalStateException(name + " is null");
        }
        return value;
    }
}
