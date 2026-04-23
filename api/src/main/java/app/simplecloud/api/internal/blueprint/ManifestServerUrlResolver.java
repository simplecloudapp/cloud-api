package app.simplecloud.api.internal.blueprint;

import app.simplecloud.api.CloudApiOptions;
import app.simplecloud.api.blueprint.CreateBlueprintRequest;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.lang.reflect.Type;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

final class ManifestServerUrlResolver implements InlineBlueprintSupport.ServerUrlResolver {
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);
    private static final Type MANIFEST_TYPE = new TypeToken<List<ManifestEntry>>() {
    }.getType();

    private final String manifestUrl;
    private final OkHttpClient httpClient;
    private final Gson gson;

    private volatile CachedManifest cachedManifest;

    ManifestServerUrlResolver(CloudApiOptions options) {
        this(
                options.getServerVersionManifestUrl(),
                new OkHttpClient.Builder()
                        .connectTimeout(options.getHttpConnectTimeout().toMillis(), TimeUnit.MILLISECONDS)
                        .readTimeout(options.getHttpReadTimeout().toMillis(), TimeUnit.MILLISECONDS)
                        .writeTimeout(options.getHttpWriteTimeout().toMillis(), TimeUnit.MILLISECONDS)
                        .build(),
                new Gson()
        );
    }

    ManifestServerUrlResolver(String manifestUrl, OkHttpClient httpClient, Gson gson) {
        this.manifestUrl = Objects.requireNonNull(manifestUrl, "manifestUrl");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.gson = Objects.requireNonNull(gson, "gson");
    }

    @Override
    public @Nullable String resolve(CreateBlueprintRequest request) {
        String softwareName = normalize(request.getServerSoftware());
        String version = resolveRequestedVersion(request);
        if (softwareName == null || version == null) {
            return null;
        }

        return loadManifest().stream()
                .filter(entry -> softwareName.equalsIgnoreCase(normalize(entry.name)))
                .flatMap(entry -> safeList(entry.downloadLinks).stream())
                .filter(downloadLink -> version.equals(normalize(downloadLink.version)))
                .map(downloadLink -> normalize(downloadLink.link))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private List<ManifestEntry> loadManifest() {
        CachedManifest current = cachedManifest;
        if (current != null && !current.isExpired()) {
            return current.entries;
        }

        synchronized (this) {
            current = cachedManifest;
            if (current != null && !current.isExpired()) {
                return current.entries;
            }

            List<ManifestEntry> fetchedManifest = fetchManifest();
            cachedManifest = new CachedManifest(fetchedManifest, Instant.now().plus(CACHE_TTL));
            return fetchedManifest;
        }
    }

    private List<ManifestEntry> fetchManifest() {
        Request request = new Request.Builder()
                .url(manifestUrl)
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IllegalStateException("Failed to fetch server version manifest from " + manifestUrl
                        + ": HTTP " + response.code());
            }

            if (response.body() == null) {
                throw new IllegalStateException("Failed to fetch server version manifest from " + manifestUrl
                        + ": empty response body");
            }

            List<ManifestEntry> manifest = gson.fromJson(response.body().charStream(), MANIFEST_TYPE);
            return manifest != null ? manifest : List.of();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to fetch server version manifest from " + manifestUrl, e);
        }
    }

    private static @Nullable String resolveRequestedVersion(CreateBlueprintRequest request) {
        String minecraftVersion = normalize(request.getMinecraftVersion());
        if (minecraftVersion != null) {
            return minecraftVersion;
        }
        return normalize(request.getSoftwareVersion());
    }

    private static <T> List<T> safeList(@Nullable List<T> values) {
        return values != null ? values : List.of();
    }

    private static @Nullable String normalize(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static final class CachedManifest {
        private final List<ManifestEntry> entries;
        private final Instant expiresAt;

        private CachedManifest(List<ManifestEntry> entries, Instant expiresAt) {
            this.entries = List.copyOf(entries);
            this.expiresAt = expiresAt;
        }

        private boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }

    private static final class ManifestEntry {
        private String name;
        private List<ManifestDownloadLink> downloadLinks;
    }

    private static final class ManifestDownloadLink {
        private String version;
        private String link;
    }
}
