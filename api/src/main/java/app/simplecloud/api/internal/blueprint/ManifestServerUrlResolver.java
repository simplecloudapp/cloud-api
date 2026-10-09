package app.simplecloud.api.internal.blueprint;

import app.simplecloud.api.CloudApiOptions;
import app.simplecloud.api.blueprint.CreateBlueprintRequest;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import okhttp3.Call;
import okhttp3.Dns;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSource;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.lang.reflect.Type;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Resolves inline blueprint download links from the server version manifest.
 *
 * <p>The manifest URL is configurable, so the fetch is restricted to HTTPS and public addresses
 * (see {@link PublicAddressDns}). Redirects are followed manually so every hop is checked again,
 * and the body is capped before it is parsed.
 */
final class ManifestServerUrlResolver implements InlineBlueprintSupport.ServerUrlResolver {
    static final int MAX_REDIRECTS = 5;
    static final long MAX_MANIFEST_BYTES = 1024 * 1024;

    private static final Duration FETCH_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);
    private static final Type MANIFEST_TYPE = new TypeToken<List<ManifestEntry>>() {
    }.getType();

    private final @Nullable HttpUrl manifestUrl;
    private final String manifestUrlForErrors;
    private final OkHttpClient httpClient;
    private final Duration fetchTimeout;
    private final Gson gson;

    private volatile CachedManifest cachedManifest;

    ManifestServerUrlResolver(CloudApiOptions options) {
        this(
                options.getServerVersionManifestUrl(),
                new OkHttpClient.Builder()
                        .connectTimeout(options.getHttpConnectTimeout().toMillis(), TimeUnit.MILLISECONDS)
                        .readTimeout(options.getHttpReadTimeout().toMillis(), TimeUnit.MILLISECONDS)
                        .writeTimeout(options.getHttpWriteTimeout().toMillis(), TimeUnit.MILLISECONDS)
                        .dns(new PublicAddressDns(Dns.SYSTEM))
                        .build(),
                FETCH_TIMEOUT,
                new Gson()
        );
    }

    /**
     * Address filtering is the caller's responsibility via the client's {@link Dns};
     * redirects are always disabled so {@link #fetchManifest()} can check each hop.
     * An invalid URL only fails once the manifest is needed, so unrelated SDK features keep working.
     */
    ManifestServerUrlResolver(String manifestUrl, OkHttpClient httpClient, Duration fetchTimeout, Gson gson) {
        this.manifestUrl = HttpUrl.parse(Objects.requireNonNull(manifestUrl, "manifestUrl").trim());
        this.manifestUrlForErrors = this.manifestUrl != null ? withoutSecrets(this.manifestUrl) : "an invalid URL";
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient").newBuilder()
                .followRedirects(false)
                .followSslRedirects(false)
                .build();
        this.fetchTimeout = Objects.requireNonNull(fetchTimeout, "fetchTimeout");
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
        HttpUrl url = manifestUrl;
        if (url == null) {
            throw fetchFailure("invalid URL");
        }

        // The per-read timeouts don't bound a slow trickle across several hops while the cache lock is held.
        Instant deadline = Instant.now().plus(fetchTimeout);
        try {
            for (int redirects = 0; ; redirects++) {
                if (!url.isHttps()) {
                    throw fetchFailure("refusing non-HTTPS URL " + withoutSecrets(url));
                }
                PublicAddressDns.requirePublicLiteral(url);

                long remainingMillis = Duration.between(Instant.now(), deadline).toMillis();
                if (remainingMillis <= 0) {
                    throw fetchFailure("timed out after " + fetchTimeout.toMillis() + " ms");
                }
                Request request = new Request.Builder()
                        .url(url)
                        .get()
                        .build();
                Call call = httpClient.newCall(request);
                call.timeout().timeout(remainingMillis, TimeUnit.MILLISECONDS);
                try (Response response = call.execute()) {
                    if (!response.isRedirect()) {
                        return readManifest(response);
                    }
                    if (redirects == MAX_REDIRECTS) {
                        throw fetchFailure("more than " + MAX_REDIRECTS + " redirects");
                    }
                    url = redirectTarget(response);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to fetch server version manifest from " + manifestUrlForErrors, e);
        }
    }

    private HttpUrl redirectTarget(Response response) {
        String location = response.header("Location");
        if (location == null) {
            throw fetchFailure("HTTP " + response.code() + " without Location header");
        }
        HttpUrl target = response.request().url().resolve(location);
        if (target == null) {
            throw fetchFailure("HTTP " + response.code() + " with invalid Location header");
        }
        return target;
    }

    private List<ManifestEntry> readManifest(Response response) throws IOException {
        if (!response.isSuccessful()) {
            throw fetchFailure("HTTP " + response.code());
        }

        ResponseBody body = response.body();
        if (body == null) {
            throw fetchFailure("empty response body");
        }

        // Content-Length may be absent or lie, so also probe the stream for one byte past the limit.
        BufferedSource source = body.source();
        if (body.contentLength() > MAX_MANIFEST_BYTES || source.request(MAX_MANIFEST_BYTES + 1)) {
            throw fetchFailure("response exceeds " + MAX_MANIFEST_BYTES + " bytes");
        }

        try {
            List<ManifestEntry> manifest = gson.fromJson(source.getBuffer().readUtf8(), MANIFEST_TYPE);
            return manifest != null ? manifest : List.of();
        } catch (JsonParseException e) {
            throw new IllegalStateException("Failed to parse server version manifest from " + manifestUrlForErrors, e);
        }
    }

    private IllegalStateException fetchFailure(String reason) {
        return new IllegalStateException(
                "Failed to fetch server version manifest from " + manifestUrlForErrors + ": " + reason
        );
    }

    private static String withoutSecrets(HttpUrl url) {
        return url.newBuilder()
                .username("")
                .password("")
                .query(null)
                .fragment(null)
                .build()
                .toString();
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
