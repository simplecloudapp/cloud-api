package app.simplecloud.api.blueprint;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Request to create a new blueprint.
 */
public class CreateBlueprintRequest {
    private final String name;
    private final String configurator;
    private final String minecraftVersion;
    private final String serverSoftware;
    private final String serverUrl;
    private final String softwareVersion;
    private final RuntimeConfig runtimeConfig;
    private final List<String> workflowSteps;

    private CreateBlueprintRequest(Builder builder) {
        this.name = builder.name;
        this.configurator = builder.configurator;
        this.minecraftVersion = builder.minecraftVersion;
        this.serverSoftware = builder.serverSoftware;
        this.serverUrl = builder.serverUrl;
        this.softwareVersion = builder.softwareVersion;
        this.runtimeConfig = builder.runtimeConfig;
        this.workflowSteps = builder.workflowSteps;
    }

    public String getName() {
        return name;
    }

    @Nullable
    public String getConfigurator() {
        return configurator;
    }

    @Nullable
    public String getMinecraftVersion() {
        return minecraftVersion;
    }

    @Nullable
    public String getServerSoftware() {
        return serverSoftware;
    }

    @Nullable
    public String getServerUrl() {
        return serverUrl;
    }

    @Nullable
    public String getSoftwareVersion() {
        return softwareVersion;
    }

    @Nullable
    public RuntimeConfig getRuntimeConfig() {
        return runtimeConfig;
    }

    @Nullable
    public List<String> getWorkflowSteps() {
        return workflowSteps;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String name;
        private String configurator;
        private String minecraftVersion;
        private String serverSoftware;
        private String serverUrl;
        private String softwareVersion;
        private RuntimeConfig runtimeConfig;
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

        public Builder runtimeConfig(RuntimeConfig runtimeConfig) {
            this.runtimeConfig = runtimeConfig;
            return this;
        }

        public Builder workflowSteps(List<String> workflowSteps) {
            this.workflowSteps = workflowSteps;
            return this;
        }

        public CreateBlueprintRequest build() {
            if (name == null) {
                throw new IllegalStateException("name is required");
            }
            return new CreateBlueprintRequest(this);
        }
    }
}
