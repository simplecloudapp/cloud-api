package app.simplecloud.api.internal.blueprint;

import app.simplecloud.api.CloudApiException;
import app.simplecloud.api.CloudApiOptions;
import app.simplecloud.api.blueprint.CreateBlueprintRequest;
import app.simplecloud.api.cache.QueryCache;
import app.simplecloud.api.cache.QueryKey;
import app.simplecloud.api.group.SourceConfig;
import app.simplecloud.api.group.SourceType;
import app.simplecloud.api.internal.web.ApiExceptions;
import app.simplecloud.api.web.ApiException;
import app.simplecloud.api.web.apis.BlueprintsApi;
import app.simplecloud.api.web.models.ModelsCreateBlueprintRequest;
import app.simplecloud.api.web.models.ModelsCreateBlueprintResponse;
import app.simplecloud.api.web.models.ModelsSourceConfig;
import org.jetbrains.annotations.Nullable;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Shared support for resources that create or delete a referenced blueprint as part of their lifecycle.
 */
public final class InlineBlueprintSupport {
    private static final Logger LOGGER = Logger.getLogger(InlineBlueprintSupport.class.getName());

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
        String effectiveName = name != null ? name : blueprintName;
        ModelsCreateBlueprintRequest apiRequest = converter.toCreateRequest(effectiveName, request);
        ModelsCreateBlueprintResponse response;
        try {
            response = blueprintsApi.createBlueprint(options.getNetworkId(), apiRequest);
        } catch (ApiException e) {
            if (e.getCode() == 409) {
                throw ApiExceptions.from("Blueprint '" + effectiveName
                        + "' already exists, set another name with CreateBlueprintRequest.name", e);
            }
            throw e;
        }

        String blueprintId = response.getBlueprintId();
        if (blueprintId == null || blueprintId.isBlank()) {
            throw new CloudApiException(CloudApiException.Reason.UNKNOWN, "Controller returned blueprint without a blueprint ID");
        }
        return blueprintId;
    }

    public @Nullable SourceConfig resolveSource(@Nullable SourceConfig requestedSource, @Nullable String blueprintId) {
        if (blueprintId == null) {
            return requestedSource;
        }
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

    /**
     * Deletes the inline blueprint after creating the resource that references it failed and returns the
     * exception the caller throws. If the resource was created anyway, the controller refuses to delete the
     * blueprint because it is in use, so it is kept.
     */
    public CloudApiException rollBackAfterCreateFailure(@Nullable String blueprintId, ApiException createFailure) {
        if (blueprintId != null) {
            try {
                deleteBlueprint(blueprintId);
            } catch (ApiException deleteFailure) {
                createFailure.addSuppressed(deleteFailure);
            }
        }
        return ApiExceptions.from(createFailure);
    }

    /**
     * Deletes the blueprint of a resource that was already deleted. Failures are logged instead of thrown,
     * because the resource itself is gone.
     */
    public void deleteBlueprintOfDeletedResource(@Nullable String blueprintId) {
        try {
            deleteBlueprint(blueprintId);
        } catch (ApiException e) {
            LOGGER.log(Level.WARNING, "Failed to delete blueprint " + blueprintId + ", it may be left unused", e);
        }
    }

    public static void invalidateCache(QueryCache cache, @Nullable String blueprintId) {
        if (blueprintId != null) {
            cache.invalidate(QueryKey.of("blueprint", blueprintId));
        }
        cache.invalidateAll(QueryKey.of("blueprints"));
        cache.invalidateAll(QueryKey.of("blueprint", "name"));
    }

    private void deleteBlueprint(@Nullable String blueprintId) throws ApiException {
        String normalizedBlueprintId = normalize(blueprintId);
        if (normalizedBlueprintId == null) {
            return;
        }
        try {
            blueprintsApi.deleteBlueprint(options.getNetworkId(), normalizedBlueprintId);
        } catch (ApiException e) {
            if (e.getCode() != 404 && e.getCode() != 409) {
                throw e;
            }
        }
    }

    private static boolean hasText(@Nullable String value) {
        return value != null && !value.isBlank();
    }

    private static @Nullable String normalize(@Nullable String value) {
        return hasText(value) ? value.trim() : null;
    }

    @FunctionalInterface
    public interface ServerUrlResolver {
        @Nullable String resolve(CreateBlueprintRequest request);
    }
}
