package app.simplecloud.api.blueprint;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Request object for creating a new blueprint.
 *
 * <p>{@link BlueprintApi#createBlueprint(CreateBlueprintRequest)} requires a name. Inline blueprints of groups and
 * persistent servers use the name of the group or persistent server when no name is set.
 */
public class CreateBlueprintRequest {
    private String name;
    private String configurator;
    private String minecraftVersion;
    private RuntimeConfig runtimeConfig;
    private String serverSoftware;
    private String serverUrl;
    private String softwareVersion;
    private List<String> workflowSteps;

    public CreateBlueprintRequest() {
    }

    public static Builder builder() {
        return new Builder();
    }

    @Nullable
    public String getName() {
        return name;
    }

    public CreateBlueprintRequest setName(@Nullable String name) {
        this.name = name;
        return this;
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

    public static class Builder {
        private String name;
        private String configurator;
        private String minecraftVersion;
        private RuntimeConfig runtimeConfig;
        private String serverSoftware;
        private String serverUrl;
        private String softwareVersion;
        private List<String> workflowSteps;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder configurator(String configurator) {
            this.configurator = configurator;
            return this;
        }

        public Builder minecraftVersion(String minecraftVersion) {
            this.minecraftVersion = minecraftVersion;
            return this;
        }

        public Builder runtimeConfig(RuntimeConfig runtimeConfig) {
            this.runtimeConfig = runtimeConfig;
            return this;
        }

        public Builder serverSoftware(String serverSoftware) {
            this.serverSoftware = serverSoftware;
            return this;
        }

        public Builder serverUrl(String serverUrl) {
            this.serverUrl = serverUrl;
            return this;
        }

        public Builder softwareVersion(String softwareVersion) {
            this.softwareVersion = softwareVersion;
            return this;
        }

        public Builder workflowSteps(List<String> workflowSteps) {
            this.workflowSteps = workflowSteps;
            return this;
        }

        public CreateBlueprintRequest build() {
            return new CreateBlueprintRequest()
                    .setName(name)
                    .setConfigurator(configurator)
                    .setMinecraftVersion(minecraftVersion)
                    .setRuntimeConfig(runtimeConfig)
                    .setServerSoftware(serverSoftware)
                    .setServerUrl(serverUrl)
                    .setSoftwareVersion(softwareVersion)
                    .setWorkflowSteps(workflowSteps);
        }
    }
}
