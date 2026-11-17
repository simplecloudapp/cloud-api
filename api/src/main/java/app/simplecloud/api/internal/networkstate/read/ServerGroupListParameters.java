package app.simplecloud.api.internal.networkstate.read;

import org.jetbrains.annotations.Nullable;

/**
 * The query parameters cloud-api sends to {@code GET /v0/server-groups}, each as the generated client sends
 * it; null leaves the parameter out. List values are comma-separated, as on the wire.
 */
public record ServerGroupListParameters(
        @Nullable String serverGroupId,
        @Nullable String name,
        @Nullable String type,
        @Nullable String tags,
        @Nullable Integer limit,
        @Nullable Integer offset
) {
}
