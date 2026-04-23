package app.simplecloud.api.internal.blueprint;

import app.simplecloud.api.CloudApiOptions;
import app.simplecloud.api.blueprint.CreateBlueprintRequest;
import app.simplecloud.api.blueprint.RuntimeConfig;
import app.simplecloud.api.group.SourceConfig;
import app.simplecloud.api.group.SourceType;
import app.simplecloud.api.web.ApiException;
import app.simplecloud.api.web.apis.BlueprintsApi;
import app.simplecloud.api.web.models.ModelsCreateBlueprintRequest;
import app.simplecloud.api.web.models.ModelsCreateBlueprintResponse;
import app.simplecloud.api.web.models.ModelsRuntimeConfig;
import app.simplecloud.api.web.models.V0BlueprintsPostRequest;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Shared support for flows that create a blueprint before creating the actual resource.
 */
public final class InlineBlueprintSupport {
    private static final Duration DELETE_RETRY_DELAY = Duration.ofMillis(200);
    private static final int DELETE_MAX_ATTEMPTS = 3;
    private static final Duration RECONCILE_RETRY_DELAY = Duration.ofMillis(200);
    private static final int RECONCILE_MAX_ATTEMPTS = 3;

    private final CloudApiOptions options;
    private final BlueprintsApi blueprintsApi;
    private final ServerUrlResolver serverUrlResolver;

    public InlineBlueprintSupport(CloudApiOptions options, BlueprintsApi blueprintsApi) {
        this(options, blueprintsApi, new ManifestServerUrlResolver(options));
    }

    public InlineBlueprintSupport(CloudApiOptions options,
                                  BlueprintsApi blueprintsApi,
                                  ServerUrlResolver serverUrlResolver) {
        this.options = options;
        this.blueprintsApi = blueprintsApi;
        this.serverUrlResolver = serverUrlResolver;
    }

    public void validateInlineBlueprintSource(@Nullable CreateBlueprintRequest createBlueprint,
                                              @Nullable SourceConfig requestedSource) {
        if (createBlueprint == null || requestedSource == null) {
            return;
        }

        if (requestedSource.getType() != null && requestedSource.getType() != SourceType.BLUEPRINT) {
            throw new IllegalArgumentException("createBlueprint requires source.type to be BLUEPRINT when source is provided");
        }
        if (hasText(requestedSource.getBlueprint())) {
            throw new IllegalArgumentException("createBlueprint cannot be combined with source.blueprint");
        }
        if (hasText(requestedSource.getImage())) {
            throw new IllegalArgumentException("createBlueprint cannot be combined with source.image");
        }
    }

    @Nullable
    public String createBlueprint(String blueprintName, @Nullable CreateBlueprintRequest request) throws ApiException {
        if (request == null) {
            return null;
        }

        ModelsCreateBlueprintRequest apiRequest = convertCreateBlueprintRequest(blueprintName, request);
        ModelsCreateBlueprintResponse response = blueprintsApi.v0BlueprintsPost(
                options.getNetworkId(),
                options.getNetworkSecret(),
                new V0BlueprintsPostRequest(apiRequest)
        );

        String blueprintId = response.getBlueprintId();
        if (blueprintId == null || blueprintId.isBlank()) {
            throw new IllegalStateException("Controller returned blueprint without a blueprint ID");
        }
        return blueprintId;
    }

    public SourceConfig buildInlineBlueprintSource(@Nullable SourceConfig requestedSource, String blueprintId) {
        SourceConfig source = new SourceConfig();
        if (requestedSource != null && requestedSource.getType() != null) {
            source.setType(requestedSource.getType());
        } else {
            source.setType(SourceType.BLUEPRINT);
        }
        source.setBlueprint(blueprintId);
        return source;
    }

    @Nullable
    public ApiException rollbackBlueprintAfterCreateFailure(@Nullable String blueprintId,
                                                            ApiException createFailure,
                                                            Supplier<Boolean> resourceExistsProbe) {
        if (blueprintId == null) {
            return null;
        }

        if (failureMayHaveCreatedResource(createFailure)) {
            try {
                if (resourceExistsAfterReconciliation(resourceExistsProbe)) {
                    return null;
                }
            } catch (RuntimeException reconcileFailure) {
                createFailure.addSuppressed(reconcileFailure);
                return null;
            }
        }

        ApiException lastDeleteFailure = null;
        for (int attempt = 1; attempt <= DELETE_MAX_ATTEMPTS; attempt++) {
            try {
                blueprintsApi.v0BlueprintsDelete(
                        options.getNetworkId(),
                        options.getNetworkSecret(),
                        blueprintId
                );
                return null;
            } catch (ApiException deleteFailure) {
                if (deleteFailure.getCode() == 404) {
                    return null;
                }
                lastDeleteFailure = deleteFailure;
                if (attempt < DELETE_MAX_ATTEMPTS) {
                    sleepQuietly(DELETE_RETRY_DELAY);
                }
            }
        }

        if (lastDeleteFailure != null) {
            createFailure.addSuppressed(lastDeleteFailure);
        }
        return lastDeleteFailure;
    }

    private boolean failureMayHaveCreatedResource(ApiException createFailure) {
        int statusCode = createFailure.getCode();
        return statusCode <= 0 || statusCode == 408 || statusCode >= 500;
    }

    private boolean resourceExistsAfterReconciliation(Supplier<Boolean> resourceExistsProbe) {
        for (int attempt = 1; attempt <= RECONCILE_MAX_ATTEMPTS; attempt++) {
            if (resourceExistsProbe.get()) {
                return true;
            }
            if (attempt < RECONCILE_MAX_ATTEMPTS) {
                sleepQuietly(RECONCILE_RETRY_DELAY);
            }
        }
        return false;
    }

    private ModelsCreateBlueprintRequest convertCreateBlueprintRequest(String blueprintName, CreateBlueprintRequest request) {
        ModelsCreateBlueprintRequest apiRequest = new ModelsCreateBlueprintRequest();
        apiRequest.setName(blueprintName);
        apiRequest.setConfigurator(request.getConfigurator());
        apiRequest.setMinecraftVersion(request.getMinecraftVersion());
        apiRequest.setServerSoftware(request.getServerSoftware());
        apiRequest.setServerUrl(resolveServerUrl(request));
        apiRequest.setSoftwareVersion(request.getSoftwareVersion());
        apiRequest.setWorkflowSteps(request.getWorkflowSteps());

        RuntimeConfig runtimeConfig = request.getRuntimeConfig();
        if (runtimeConfig != null) {
            ModelsRuntimeConfig apiRuntimeConfig = new ModelsRuntimeConfig();
            if (runtimeConfig.getType() != null) {
                apiRuntimeConfig.setType(runtimeConfig.getType().name().toLowerCase());
            }
            apiRuntimeConfig.setWith(runtimeConfig.getWith());
            apiRequest.setRuntimeConfig(apiRuntimeConfig);
        }

        return apiRequest;
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
                    "No manifest download link found for software '" + software + "' and version '" + version + "'"
            );
        }
        return resolvedServerUrl;
    }

    private boolean hasText(@Nullable String value) {
        return value != null && !value.isBlank();
    }

    private @Nullable String resolveRequestedVersion(CreateBlueprintRequest request) {
        String minecraftVersion = normalize(request.getMinecraftVersion());
        if (minecraftVersion != null) {
            return minecraftVersion;
        }
        return normalize(request.getSoftwareVersion());
    }

    private @Nullable String normalize(@Nullable String value) {
        if (!hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private void sleepQuietly(Duration delay) {
        try {
            Thread.sleep(delay.toMillis());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    @FunctionalInterface
    public interface ServerUrlResolver {
        @Nullable String resolve(CreateBlueprintRequest request);
    }
}
