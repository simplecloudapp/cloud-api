package app.simplecloud.api.testing.internal.blueprint;

import app.simplecloud.api.blueprint.Blueprint;
import app.simplecloud.api.blueprint.RuntimeConfig;
import java.util.List;
import org.jetbrains.annotations.Nullable;

public record BlueprintImpl(
        String blueprintId,
        String name,
        String configurator,
        String minecraftVersion,
        String serverSoftware,
        String serverUrl,
        String softwareVersion,
        @Nullable RuntimeConfig runtimeConfig,
        List<String> workflowSteps,
        String createdAt,
        String updatedAt)
        implements Blueprint {

    @Override
    public String getBlueprintId() {
        return blueprintId;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getConfigurator() {
        return configurator;
    }

    @Override
    public String getMinecraftVersion() {
        return minecraftVersion;
    }

    @Override
    public String getServerSoftware() {
        return serverSoftware;
    }

    @Override
    public String getServerUrl() {
        return serverUrl;
    }

    @Override
    public String getSoftwareVersion() {
        return softwareVersion;
    }

    @Override
    public @Nullable RuntimeConfig getRuntimeConfig() {
        return BlueprintData.copy(runtimeConfig);
    }

    @Override
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
