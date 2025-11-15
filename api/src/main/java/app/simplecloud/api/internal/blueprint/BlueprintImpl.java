package app.simplecloud.api.internal.blueprint;

import app.simplecloud.api.blueprint.Blueprint;
import app.simplecloud.api.blueprint.RuntimeConfig;
import app.simplecloud.api.blueprint.RuntimeType;
import app.simplecloud.api.web.models.ModelsBlueprintInfo;
import app.simplecloud.api.web.models.ModelsBlueprintSummary;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class BlueprintImpl implements Blueprint {
    private final ModelsBlueprintSummary summaryDelegate;
    private final ModelsBlueprintInfo infoDelegate;
    private RuntimeConfig runtimeConfig;

    public BlueprintImpl(ModelsBlueprintSummary delegate) {
        this.summaryDelegate = delegate;
        this.infoDelegate = null;
    }

    public BlueprintImpl(ModelsBlueprintInfo delegate) {
        this.summaryDelegate = null;
        this.infoDelegate = delegate;
    }

    @Override
    public String getBlueprintId() {
        if (summaryDelegate != null) {
            String id = summaryDelegate.getBlueprintId();
            if (id == null) {
                throw new IllegalStateException("Blueprint ID is null");
            }
            return id;
        }
        if (infoDelegate != null) {
            String id = infoDelegate.getId();
            if (id == null) {
                throw new IllegalStateException("Blueprint ID is null");
            }
            return id;
        }
        throw new IllegalStateException("No delegate available");
    }

    @Override
    public String getName() {
        String name = null;
        if (summaryDelegate != null) {
            name = summaryDelegate.getName();
        } else if (infoDelegate != null) {
            name = infoDelegate.getName();
        }
        if (name == null) {
            throw new IllegalStateException("Blueprint name is null");
        }
        return name;
    }

    @Override
    @Nullable
    public String getConfigurator() {
        if (summaryDelegate != null) {
            return summaryDelegate.getConfigurator();
        }
        return infoDelegate != null ? infoDelegate.getConfigurator() : null;
    }

    @Override
    @Nullable
    public String getMinecraftVersion() {
        if (summaryDelegate != null) {
            return summaryDelegate.getMinecraftVersion();
        }
        return infoDelegate != null ? infoDelegate.getMinecraftVersion() : null;
    }

    @Override
    @Nullable
    public String getServerSoftware() {
        if (summaryDelegate != null) {
            return summaryDelegate.getServerSoftware();
        }
        return infoDelegate != null ? infoDelegate.getServerSoftware() : null;
    }

    @Override
    @Nullable
    public String getServerUrl() {
        if (summaryDelegate != null) {
            return summaryDelegate.getServerUrl();
        }
        return infoDelegate != null ? infoDelegate.getServerUrl() : null;
    }

    @Override
    @Nullable
    public String getSoftwareVersion() {
        if (summaryDelegate != null) {
            return summaryDelegate.getSoftwareVersion();
        }
        return infoDelegate != null ? infoDelegate.getSoftwareVersion() : null;
    }

    @Override
    @Nullable
    public RuntimeConfig getRuntimeConfig() {
        if (runtimeConfig == null) {
            app.simplecloud.api.web.models.ModelsRuntimeConfig config = null;
            if (summaryDelegate != null) {
                config = summaryDelegate.getRuntimeConfig();
            } else if (infoDelegate != null) {
                java.util.Map<String, Object> runtimeConfigMap = infoDelegate.getRuntimeConfig();
                if (runtimeConfigMap != null) {
                    config = new app.simplecloud.api.web.models.ModelsRuntimeConfig();
                    config.setWith(runtimeConfigMap);
                }
            }
            if (config != null) {
                runtimeConfig = convertRuntimeConfig(config);
            }
        }
        return runtimeConfig;
    }

    @Override
    @Nullable
    public List<String> getWorkflowSteps() {
        if (summaryDelegate != null) {
            return summaryDelegate.getWorkflowSteps();
        }
        return infoDelegate != null ? infoDelegate.getWorkflowSteps() : null;
    }

    @Override
    public String getCreatedAt() {
        String createdAt = null;
        if (summaryDelegate != null) {
            createdAt = summaryDelegate.getCreatedAt();
        } else if (infoDelegate != null) {
            createdAt = infoDelegate.getCreatedAt();
        }
        if (createdAt == null) {
            throw new IllegalStateException("Created at timestamp is null");
        }
        return createdAt;
    }

    @Override
    public String getUpdatedAt() {
        String updatedAt = null;
        if (summaryDelegate != null) {
            updatedAt = summaryDelegate.getUpdatedAt();
        } else if (infoDelegate != null) {
            updatedAt = infoDelegate.getUpdatedAt();
        }
        if (updatedAt == null) {
            throw new IllegalStateException("Updated at timestamp is null");
        }
        return updatedAt;
    }

    private RuntimeConfig convertRuntimeConfig(app.simplecloud.api.web.models.ModelsRuntimeConfig config) {
        RuntimeConfig result = new RuntimeConfig();
        String typeStr = config.getType();
        if (typeStr != null) {
            try {
                result.setType(RuntimeType.valueOf(typeStr.toUpperCase()));
            } catch (IllegalArgumentException e) {
            }
        }
        result.setWith(config.getWith());
        return result;
    }
}

