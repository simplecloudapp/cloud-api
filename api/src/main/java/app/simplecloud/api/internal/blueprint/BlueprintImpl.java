package app.simplecloud.api.internal.blueprint;

import app.simplecloud.api.blueprint.Blueprint;
import app.simplecloud.api.blueprint.RuntimeConfig;
import app.simplecloud.api.blueprint.RuntimeType;
import app.simplecloud.api.web.models.ModelsBlueprintSummary;
import app.simplecloud.api.web.models.ModelsRuntimeConfig;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

final class BlueprintImpl implements Blueprint {
    private final ModelsBlueprintSummary delegate;
    private final RuntimeConfig runtimeConfig;
    private final List<String> workflowSteps;

    BlueprintImpl(ModelsBlueprintSummary delegate) {
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
