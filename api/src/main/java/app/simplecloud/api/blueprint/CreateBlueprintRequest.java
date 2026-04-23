package app.simplecloud.api.blueprint;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Request object for creating a new blueprint.
 */
public class CreateBlueprintRequest {
    private String configurator;
    private String minecraftVersion;
    private RuntimeConfig runtimeConfig;
    private String serverSoftware;
    private String serverUrl;
    private String softwareVersion;
    private List<String> workflowSteps;

    public CreateBlueprintRequest() {
    }

    @Nullable
    public String getConfigurator() {
        return configurator;
    }

    public CreateBlueprintRequest setConfigurator(@Nullable String configurator) {
        this.configurator = configurator;
        return this;
    }

    @Nullable
    public String getMinecraftVersion() {
        return minecraftVersion;
    }

    public CreateBlueprintRequest setMinecraftVersion(@Nullable String minecraftVersion) {
        this.minecraftVersion = minecraftVersion;
        return this;
    }

    @Nullable
    public RuntimeConfig getRuntimeConfig() {
        return runtimeConfig;
    }

    public CreateBlueprintRequest setRuntimeConfig(@Nullable RuntimeConfig runtimeConfig) {
        this.runtimeConfig = runtimeConfig;
        return this;
    }

    @Nullable
    public String getServerSoftware() {
        return serverSoftware;
    }

    public CreateBlueprintRequest setServerSoftware(@Nullable String serverSoftware) {
        this.serverSoftware = serverSoftware;
        return this;
    }

    @Nullable
    public String getServerUrl() {
        return serverUrl;
    }

    public CreateBlueprintRequest setServerUrl(@Nullable String serverUrl) {
        this.serverUrl = serverUrl;
        return this;
    }

    @Nullable
    public String getSoftwareVersion() {
        return softwareVersion;
    }

    public CreateBlueprintRequest setSoftwareVersion(@Nullable String softwareVersion) {
        this.softwareVersion = softwareVersion;
        return this;
    }

    @Nullable
    public List<String> getWorkflowSteps() {
        return workflowSteps;
    }

    public CreateBlueprintRequest setWorkflowSteps(@Nullable List<String> workflowSteps) {
        this.workflowSteps = workflowSteps;
        return this;
    }
}
