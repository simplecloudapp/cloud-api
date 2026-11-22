package app.simplecloud.api.testing.internal.blueprint;

import app.simplecloud.api.blueprint.Blueprint;
import app.simplecloud.api.blueprint.RuntimeConfig;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import org.jetbrains.annotations.Nullable;

public final class BlueprintData {

    public final String id;
    public final String createdAt;
    public String name;
    public String updatedAt;
    public String configurator = "";
    public String minecraftVersion = "";
    public String serverSoftware = "";
    public String serverUrl = "";
    public String softwareVersion = "";
    public @Nullable RuntimeConfig runtimeConfig;
    public final List<String> workflowSteps = new ArrayList<>();
    public boolean deleted;

    public BlueprintData(String id, String name, String createdAt) {
        this.id = id;
        this.name = name;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public BlueprintData copy() {
        BlueprintData copy = new BlueprintData(id, name, createdAt);
        copy.updatedAt = updatedAt;
        copy.configurator = configurator;
        copy.minecraftVersion = minecraftVersion;
        copy.serverSoftware = serverSoftware;
        copy.serverUrl = serverUrl;
        copy.softwareVersion = softwareVersion;
        copy.runtimeConfig = copy(runtimeConfig);
        copy.workflowSteps.addAll(workflowSteps);
        copy.deleted = deleted;
        return copy;
    }

    public Blueprint snapshot() {
        return new BlueprintImpl(
                id,
                name,
                configurator,
                minecraftVersion,
                serverSoftware,
                serverUrl,
                softwareVersion,
                copy(runtimeConfig),
                List.copyOf(workflowSteps),
                createdAt,
                updatedAt);
    }

    public static @Nullable RuntimeConfig copy(@Nullable RuntimeConfig config) {
        if (config == null) {
            return null;
        }
        return RuntimeConfig.builder()
                .type(config.getType())
                .with(config.getWith() == null ? null : new LinkedHashMap<>(config.getWith()))
                .build();
    }
}
