package app.simplecloud.api.internal.blueprint;

import app.simplecloud.api.CloudApiException;
import app.simplecloud.api.CloudApiOptions;
import app.simplecloud.api.blueprint.CreateBlueprintRequest;
import app.simplecloud.api.group.SourceConfig;
import app.simplecloud.api.group.SourceType;
import app.simplecloud.api.internal.web.ApiErrors;
import app.simplecloud.api.web.ApiException;
import app.simplecloud.api.web.apis.BlueprintsApi;
import app.simplecloud.api.web.models.ModelsCreateBlueprintRequest;
import app.simplecloud.api.web.models.ModelsCreateBlueprintResponse;
import app.simplecloud.api.web.models.ModelsSourceConfig;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;

/**
 * Shared support for resources that create or delete a referenced blueprint as part of their lifecycle.
 */
public final class InlineBlueprintSupport {
    private static final Duration DELETE_RETRY_DELAY = Duration.ofMillis(200);
    private static final int DELETE_MAX_ATTEMPTS = 3;
    private static final Duration RECONCILE_RETRY_DELAY = Duration.ofMillis(200);
    private static final int RECONCILE_MAX_ATTEMPTS = 3;

    private final CloudApiOptions options;
    private final BlueprintsApi blueprintsApi;
    private final BlueprintRequestConverter converter;

    public InlineBlueprintSupport(CloudApiOptions options, BlueprintsApi blueprintsApi) {
        this(options, blueprintsApi, new ManifestServerUrlResolver(options));
    }

    public InlineBlueprintSupport(CloudApiOptions options,
                                  BlueprintsApi blueprintsApi,
                                  ServerUrlResolver serverUrlResolver) {
        this.options = options;
        this.blueprintsApi = blueprintsApi;
        this.converter = new BlueprintRequestConverter(serverUrlResolver);
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

        String name = normalize(request.getName());
        ModelsCreateBlueprintRequest apiRequest = converter.toCreateRequest(name != null ? name : blueprintName, request);
        ModelsCreateBlueprintResponse response = blueprintsApi.createBlueprint(
                options.getNetworkId(),
                apiRequest
        );

        String blueprintId = response.getBlueprintId();
        if (blueprintId == null || blueprintId.isBlank()) {
            throw new CloudApiException(CloudApiException.Reason.UNKNOWN, "Controller returned blueprint without a blueprint ID");
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
    public String getReferencedBlueprintId(@Nullable ModelsSourceConfig source) {
        return source == null ? null : normalize(source.getBlueprint());
    }

    public void deleteBlueprint(@Nullable String blueprintId) throws ApiException {
        String normalizedBlueprintId = normalize(blueprintId);
        if (normalizedBlueprintId == null) {
            return;
        }

        ApiException lastDeleteFailure = null;
        for (int attempt = 1; attempt <= DELETE_MAX_ATTEMPTS; attempt++) {
            try {
                blueprintsApi.deleteBlueprint(
                        options.getNetworkId(),
                        normalizedBlueprintId
                );
                return;
            } catch (ApiException deleteFailure) {
                int statusCode = deleteFailure.getCode();
                if (statusCode == 404 || statusCode == 409) {
                    return;
                }
                if (!isRetryable(statusCode)) {
                    throw deleteFailure;
                }
                lastDeleteFailure = deleteFailure;
                if (attempt < DELETE_MAX_ATTEMPTS) {
                    sleepQuietly(DELETE_RETRY_DELAY);
                }
            }
        }

        throw lastDeleteFailure;
    }

    /**
     * Deletes the inline blueprint after creating the resource that references it failed,
     * and returns the exception the caller throws.
     * Keeps the blueprint if the resource may have been created and either turns out to exist or can't be checked.
     *
     * @param resourceKind names the resource in the failure message, for example {@code "group"}
     * @return the create failure converted to a {@link CloudApiException}, or a rollback failure wrapping it if
     * the blueprint could not be deleted
     */
    public CloudApiException rollBackAfterCreateFailure(String resourceKind,
                                                        @Nullable String blueprintId,
                                                        ApiException createFailure,
                                                        CreatedResourceCheck createdResource) {
        if (blueprintId == null) {
            return ApiErrors.from(createFailure);
        }

        if (failureMayHaveCreatedResource(createFailure)) {
            try {
                if (resourceExistsAfterReconciliation(createdResource)) {
                    return ApiErrors.from(createFailure);
                }
            } catch (RuntimeException reconcileFailure) {
                createFailure.addSuppressed(reconcileFailure);
                return ApiErrors.from(createFailure);
            }
        }

        try {
            deleteBlueprint(blueprintId);
            return ApiErrors.from(createFailure);
        } catch (ApiException deleteFailure) {
            createFailure.addSuppressed(deleteFailure);
            CloudApiException rollbackFailure = ApiErrors.from(
                    "Failed to create " + resourceKind + " and rollback inline blueprint " + blueprintId,
                    createFailure
            );
            rollbackFailure.addSuppressed(deleteFailure);
            return rollbackFailure;
        }
    }

    private boolean failureMayHaveCreatedResource(ApiException createFailure) {
        int statusCode = createFailure.getCode();
        return statusCode <= 0 || statusCode == 408 || statusCode >= 500;
    }

    private boolean isRetryable(int statusCode) {
        return statusCode <= 0 || statusCode == 408 || statusCode == 429 || statusCode >= 500;
    }

    private boolean resourceExistsAfterReconciliation(CreatedResourceCheck createdResource) {
        for (int attempt = 1; attempt <= RECONCILE_MAX_ATTEMPTS; attempt++) {
            if (createdResource.resourceExists()) {
                return true;
            }
            if (attempt < RECONCILE_MAX_ATTEMPTS) {
                sleepQuietly(RECONCILE_RETRY_DELAY);
            }
        }
        return false;
    }

    private boolean hasText(@Nullable String value) {
        return value != null && !value.isBlank();
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

    /**
     * Checks whether the resource that references the inline blueprint exists.
     */
    @FunctionalInterface
    public interface CreatedResourceCheck {
        boolean resourceExists();
    }
}
