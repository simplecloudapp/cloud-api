package app.simplecloud.api.internal.blueprint;

import app.simplecloud.api.blueprint.Blueprint;
import app.simplecloud.api.blueprint.RuntimeConfig;
import app.simplecloud.api.blueprint.RuntimeType;
import app.simplecloud.api.web.models.ModelsBlueprintInfo;
import app.simplecloud.api.web.models.ModelsBlueprintSummary;
import app.simplecloud.api.web.models.ModelsRuntimeConfig;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class BlueprintImpl implements Blueprint {
    private final ModelsBlueprintSummary delegate;
    private final RuntimeConfig runtimeConfig;
    private final List<String> workflowSteps;

    public static BlueprintImpl fromInfo(ModelsBlueprintInfo info) {
        ModelsBlueprintSummary summary = new ModelsBlueprintSummary();
        summary.setBlueprintId(info.getId());
        summary.setName(info.getName());
        summary.setConfigurator(info.getConfigurator());
        summary.setMinecraftVersion(info.getMinecraftVersion());
        summary.setServerSoftware(info.getServerSoftware());
        summary.setServerUrl(info.getServerUrl());
        summary.setSoftwareVersion(info.getSoftwareVersion());
        summary.setWorkflowSteps(info.getWorkflowSteps());
        summary.setCreatedAt(info.getCreatedAt());
        summary.setUpdatedAt(info.getUpdatedAt());
        Map<String, Object> runtime = info.getRuntimeConfig();
        if (runtime != null && !runtime.isEmpty()) {
            ModelsRuntimeConfig runtimeConfig = new ModelsRuntimeConfig();
            if (runtime.get("type") instanceof String type) {
                runtimeConfig.setType(type);
            }
            if (runtime.get("with") instanceof Map<?, ?> with) {
                Map<String, Object> copy = new HashMap<>();
                with.forEach((key, value) -> copy.put(String.valueOf(key), value));
                runtimeConfig.setWith(copy);
            }
            summary.setRuntimeConfig(runtimeConfig);
        }
        return new BlueprintImpl(summary);
    }

    public BlueprintImpl(ModelsBlueprintSummary delegate) {
        this.delegate = delegate;
        this.runtimeConfig = convertRuntimeConfig(delegate.getRuntimeConfig());
        this.workflowSteps = delegate.getWorkflowSteps() == null ? null : List.copyOf(delegate.getWorkflowSteps());
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
        return delegate.getBlueprintId();
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    @Nullable
    public String getConfigurator() {
        return delegate.getConfigurator();
    }

    @Override
    @Nullable
    public String getMinecraftVersion() {
        return delegate.getMinecraftVersion();
    }

    @Override
    @Nullable
    public String getServerSoftware() {
        return delegate.getServerSoftware();
    }

    @Override
    @Nullable
    public String getServerUrl() {
        return delegate.getServerUrl();
    }

    @Override
    @Nullable
    public String getSoftwareVersion() {
        return delegate.getSoftwareVersion();
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
        return delegate.getCreatedAt();
    }

    @Override
    public String getUpdatedAt() {
        return delegate.getUpdatedAt();
    }
}
