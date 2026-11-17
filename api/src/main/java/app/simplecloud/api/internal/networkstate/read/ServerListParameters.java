package app.simplecloud.api.internal.networkstate.read;

import org.jetbrains.annotations.Nullable;

/**
 * The query parameters cloud-api sends to {@code GET /v0/servers}, each as the generated client sends it;
 * null leaves the parameter out. List values are comma-separated, as on the wire.
 */
public record ServerListParameters(
        @Nullable String serverId,
        @Nullable String serverGroupId,
        @Nullable String state,
        @Nullable String serverhostId,
        @Nullable String persistentServerId,
        @Nullable String type,
        @Nullable String name,
        @Nullable String tags,
        @Nullable String numericalId,
        @Nullable String sortBy,
        @Nullable String sortOrder,
        @Nullable Integer limit,
        @Nullable Integer offset
) {
}
