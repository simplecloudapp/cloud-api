package app.simplecloud.api.internal.networkstate.read;

import org.jetbrains.annotations.Nullable;

/**
 * The query parameters cloud-api sends to {@code GET /v0/persistent-servers}, each as the generated client
 * sends it; null leaves the parameter out. List values are comma-separated, as on the wire.
 */
public record PersistentServerListParameters(
        @Nullable String persistentServerId,
        @Nullable String name,
        @Nullable String tags,
        @Nullable Boolean active,
        @Nullable String serverhostId,
        @Nullable Integer limit,
        @Nullable Integer offset
) {
}
