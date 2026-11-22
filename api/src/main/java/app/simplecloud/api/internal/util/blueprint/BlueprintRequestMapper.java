package app.simplecloud.api.internal.util.blueprint;

import app.simplecloud.api.blueprint.CreateBlueprintRequest;
import app.simplecloud.api.blueprint.RuntimeConfig;
import app.simplecloud.api.blueprint.UpdateBlueprintRequest;
import app.simplecloud.api.web.models.ModelsCreateBlueprintRequest;
import app.simplecloud.api.web.models.ModelsPatchBlueprintRequest;
import app.simplecloud.api.web.models.ModelsRuntimeConfig;
import app.simplecloud.api.web.models.ModelsUpdateBlueprintRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.jetbrains.annotations.Nullable;

public final class BlueprintRequestMapper {

    private final InlineBlueprintManager.ServerUrlResolver serverUrlResolver;

    public BlueprintRequestMapper(InlineBlueprintManager.ServerUrlResolver serverUrlResolver) {
        this.serverUrlResolver = serverUrlResolver;
    }

    public ModelsCreateBlueprintRequest toCreateRequest(String name, CreateBlueprintRequest request) {
        ModelsCreateBlueprintRequest apiRequest = new ModelsCreateBlueprintRequest();
        apiRequest.setName(name);
        apiRequest.setConfigurator(request.getConfigurator());
        apiRequest.setMinecraftVersion(request.getMinecraftVersion());
        apiRequest.setServerSoftware(request.getServerSoftware());
        apiRequest.setServerUrl(resolveServerUrl(request));
        apiRequest.setSoftwareVersion(request.getSoftwareVersion());
        apiRequest.setWorkflowSteps(copy(request.getWorkflowSteps()));
        apiRequest.setRuntimeConfig(toApiRuntimeConfig(request.getRuntimeConfig()));
        return apiRequest;
    }

    public ModelsUpdateBlueprintRequest toReplaceRequest(String name, CreateBlueprintRequest request) {
        ModelsUpdateBlueprintRequest apiRequest = new ModelsUpdateBlueprintRequest();
        apiRequest.setName(name);
        apiRequest.setConfigurator(request.getConfigurator());
        apiRequest.setMinecraftVersion(request.getMinecraftVersion());
        apiRequest.setServerSoftware(request.getServerSoftware());
        apiRequest.setServerUrl(resolveServerUrl(request));
        apiRequest.setSoftwareVersion(request.getSoftwareVersion());
        apiRequest.setWorkflowSteps(copy(request.getWorkflowSteps()));
        apiRequest.setRuntimeConfig(toApiRuntimeConfig(request.getRuntimeConfig()));
        return apiRequest;
    }

    public ModelsPatchBlueprintRequest toPatchRequest(UpdateBlueprintRequest request) {
        if (request.getRuntimeConfig() != null && request.getRuntimeConfig().type() == null) {
            throw new IllegalArgumentException("runtimeConfig.type is required when updating the runtime config");
        }
        ModelsPatchBlueprintRequest apiRequest = new ModelsPatchBlueprintRequest();
        apiRequest.setName(request.getName());
        apiRequest.setConfigurator(request.getConfigurator());
        apiRequest.setMinecraftVersion(request.getMinecraftVersion());
        apiRequest.setServerSoftware(request.getServerSoftware());
        apiRequest.setServerUrl(request.getServerUrl());
        apiRequest.setSoftwareVersion(request.getSoftwareVersion());
        apiRequest.setWorkflowSteps(copy(request.getWorkflowSteps()));
        apiRequest.setRuntimeConfig(toApiRuntimeConfig(request.getRuntimeConfig()));
        return apiRequest;
    }

    private @Nullable ModelsRuntimeConfig toApiRuntimeConfig(@Nullable RuntimeConfig runtimeConfig) {
        if (runtimeConfig == null) {
            return null;
        }
        ModelsRuntimeConfig apiRuntimeConfig = new ModelsRuntimeConfig();
        if (runtimeConfig.type() != null) {
            apiRuntimeConfig.setType(runtimeConfig.type().name().toLowerCase(Locale.ROOT));
        }
        apiRuntimeConfig.setWith(runtimeConfig.with());
        return apiRuntimeConfig;
    }

    private @Nullable String resolveServerUrl(CreateBlueprintRequest request) {
        String explicitServerUrl = normalize(request.getServerUrl());
        if (explicitServerUrl != null) {
            return explicitServerUrl;
        }

        String software = normalize(request.getServerSoftware());
        String version = resolveRequestedVersion(request);
        if (software == null || version == null) {
            return null;
        }

        String resolvedServerUrl = normalize(serverUrlResolver.resolve(request));
        if (resolvedServerUrl == null) {
            throw new IllegalArgumentException(
                    "No manifest download link found for software '" + software + "' and version '" + version + "'");
        }
        return resolvedServerUrl;
    }

    private @Nullable String resolveRequestedVersion(CreateBlueprintRequest request) {
        String minecraftVersion = normalize(request.getMinecraftVersion());
        if (minecraftVersion != null) {
            return minecraftVersion;
        }
        return normalize(request.getSoftwareVersion());
    }

    private static @Nullable List<String> copy(@Nullable List<String> values) {
        return values != null ? new ArrayList<>(values) : null;
    }

    public static @Nullable String normalize(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
