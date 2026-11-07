package app.simplecloud.api.blueprint;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Fields to change on a blueprint, see {@link BlueprintApi#updateBlueprint(String, UpdateBlueprintRequest)}.
 *
 * <p>Fields left {@code null} stay unchanged. The runtime config and the workflow steps replace the stored
 * values as a whole.
 */
public class UpdateBlueprintRequest {
    private String name;
    private String configurator;
    private String minecraftVersion;
    private RuntimeConfig runtimeConfig;
    private String serverSoftware;
    private String serverUrl;
    private String softwareVersion;
    private List<String> workflowSteps;

    public UpdateBlueprintRequest() {}

    public static Builder builder() {
        return new Builder();
    }

    @Nullable
    public String getName() {
        return name;
    }

    public UpdateBlueprintRequest setName(@Nullable String name) {
        this.name = name;
        return this;
    }

    @Nullable
    public String getConfigurator() {
        return configurator;
    }

    public UpdateBlueprintRequest setConfigurator(@Nullable String configurator) {
        this.configurator = configurator;
        return this;
    }

    @Nullable
    public String getMinecraftVersion() {
        return minecraftVersion;
    }

    public UpdateBlueprintRequest setMinecraftVersion(@Nullable String minecraftVersion) {
        this.minecraftVersion = minecraftVersion;
        return this;
    }

    @Nullable
    public RuntimeConfig getRuntimeConfig() {
        return runtimeConfig;
    }

    public UpdateBlueprintRequest setRuntimeConfig(@Nullable RuntimeConfig runtimeConfig) {
        this.runtimeConfig = runtimeConfig;
        return this;
    }

    @Nullable
    public String getServerSoftware() {
        return serverSoftware;
    }

    public UpdateBlueprintRequest setServerSoftware(@Nullable String serverSoftware) {
        this.serverSoftware = serverSoftware;
        return this;
    }

    @Nullable
    public String getServerUrl() {
        return serverUrl;
    }

    public UpdateBlueprintRequest setServerUrl(@Nullable String serverUrl) {
        this.serverUrl = serverUrl;
        return this;
    }

    @Nullable
    public String getSoftwareVersion() {
        return softwareVersion;
    }

    public UpdateBlueprintRequest setSoftwareVersion(@Nullable String softwareVersion) {
        this.softwareVersion = softwareVersion;
        return this;
    }

    @Nullable
    public List<String> getWorkflowSteps() {
        return workflowSteps;
    }

    public UpdateBlueprintRequest setWorkflowSteps(@Nullable List<String> workflowSteps) {
        this.workflowSteps = workflowSteps;
        return this;
    }

    public static class Builder {
        private final UpdateBlueprintRequest request = new UpdateBlueprintRequest();

        public Builder name(String name) {
            request.setName(name);
            return this;
        }

        public Builder configurator(String configurator) {
            request.setConfigurator(configurator);
            return this;
        }

        public Builder minecraftVersion(String minecraftVersion) {
            request.setMinecraftVersion(minecraftVersion);
            return this;
        }

        public Builder runtimeConfig(RuntimeConfig runtimeConfig) {
            request.setRuntimeConfig(runtimeConfig);
            return this;
        }

        public Builder serverSoftware(String serverSoftware) {
            request.setServerSoftware(serverSoftware);
            return this;
        }

        public Builder serverUrl(String serverUrl) {
            request.setServerUrl(serverUrl);
            return this;
        }

        public Builder softwareVersion(String softwareVersion) {
            request.setSoftwareVersion(softwareVersion);
            return this;
        }

        public Builder workflowSteps(List<String> workflowSteps) {
            request.setWorkflowSteps(workflowSteps);
            return this;
        }

        public UpdateBlueprintRequest build() {
            return new UpdateBlueprintRequest()
                    .setName(request.getName())
                    .setConfigurator(request.getConfigurator())
                    .setMinecraftVersion(request.getMinecraftVersion())
                    .setRuntimeConfig(request.getRuntimeConfig())
                    .setServerSoftware(request.getServerSoftware())
                    .setServerUrl(request.getServerUrl())
                    .setSoftwareVersion(request.getSoftwareVersion())
                    .setWorkflowSteps(request.getWorkflowSteps());
        }
    }
}
