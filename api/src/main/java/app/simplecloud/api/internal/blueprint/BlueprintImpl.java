package app.simplecloud.api.internal.blueprint;

import app.simplecloud.api.blueprint.Blueprint;
import app.simplecloud.api.blueprint.RuntimeConfig;
import app.simplecloud.api.blueprint.RuntimeType;
import app.simplecloud.api.web.models.ModelsBlueprintSummary;
import app.simplecloud.api.web.models.ModelsCreateBlueprintResponse;
import app.simplecloud.api.web.models.ModelsRuntimeConfig;
import app.simplecloud.api.web.models.ModelsUpdateBlueprintResponse;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

final class BlueprintImpl implements Blueprint {
    private final String blueprintId;
    private final String name;
    private final String configurator;
    private final String minecraftVersion;
    private final String serverSoftware;
    private final String serverUrl;
    private final String softwareVersion;
    private final RuntimeConfig runtimeConfig;
    private final List<String> workflowSteps;
    private final String createdAt;
    private final String updatedAt;

    private BlueprintImpl(
            String blueprintId,
            String name,
            @Nullable String configurator,
            @Nullable String minecraftVersion,
            @Nullable String serverSoftware,
            @Nullable String serverUrl,
            @Nullable String softwareVersion,
            @Nullable ModelsRuntimeConfig runtimeConfig,
            @Nullable List<String> workflowSteps,
            String createdAt,
            String updatedAt) {
        this.blueprintId = blueprintId;
        this.name = name;
        this.configurator = configurator;
        this.minecraftVersion = minecraftVersion;
        this.serverSoftware = serverSoftware;
        this.serverUrl = serverUrl;
        this.softwareVersion = softwareVersion;
        this.runtimeConfig = convertRuntimeConfig(runtimeConfig);
        this.workflowSteps = workflowSteps == null ? null : List.copyOf(workflowSteps);
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    static BlueprintImpl from(ModelsBlueprintSummary model) {
        return new BlueprintImpl(
                model.getBlueprintId(),
                model.getName(),
                model.getConfigurator(),
                model.getMinecraftVersion(),
                model.getServerSoftware(),
                model.getServerUrl(),
                model.getSoftwareVersion(),
                model.getRuntimeConfig(),
                model.getWorkflowSteps(),
                model.getCreatedAt(),
                model.getUpdatedAt());
    }

    static BlueprintImpl from(ModelsCreateBlueprintResponse model) {
        return new BlueprintImpl(
                model.getBlueprintId(),
                model.getName(),
                model.getConfigurator(),
                model.getMinecraftVersion(),
                model.getServerSoftware(),
                model.getServerUrl(),
                model.getSoftwareVersion(),
                model.getRuntimeConfig(),
                model.getWorkflowSteps(),
                model.getCreatedAt(),
                model.getUpdatedAt());
    }

    static BlueprintImpl from(ModelsUpdateBlueprintResponse model) {
        return new BlueprintImpl(
                model.getBlueprintId(),
                model.getName(),
                model.getConfigurator(),
                model.getMinecraftVersion(),
                model.getServerSoftware(),
                model.getServerUrl(),
                model.getSoftwareVersion(),
                model.getRuntimeConfig(),
                model.getWorkflowSteps(),
                model.getCreatedAt(),
                model.getUpdatedAt());
    }

    @Nullable
    private static RuntimeConfig convertRuntimeConfig(@Nullable ModelsRuntimeConfig model) {
        if (model == null) {
            return null;
        }
        RuntimeConfig config = new RuntimeConfig();
        if (model.getType() != null) {
            try {
                config.setType(RuntimeType.valueOf(model.getType().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        config.setWith(model.getWith());
        return config;
    }

    @Override
    public String getBlueprintId() {
        return blueprintId;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    @Nullable
    public String getConfigurator() {
        return configurator;
    }

    @Override
    @Nullable
    public String getMinecraftVersion() {
        return minecraftVersion;
    }

    @Override
    @Nullable
    public String getServerSoftware() {
        return serverSoftware;
    }

    @Override
    @Nullable
    public String getServerUrl() {
        return serverUrl;
    }

    @Override
    @Nullable
    public String getSoftwareVersion() {
        return softwareVersion;
    }

    @Override
    @Nullable
    public RuntimeConfig getRuntimeConfig() {
        return runtimeConfig;
    }

    @Override
    @Nullable
    public List<String> getWorkflowSteps() {
        return workflowSteps;
    }

    @Override
    public String getCreatedAt() {
        return createdAt;
    }

    @Override
    public String getUpdatedAt() {
        return updatedAt;
    }
}
