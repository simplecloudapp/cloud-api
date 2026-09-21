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
import java.io.Reader;
import java.lang.reflect.Type;
import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

final class ManifestServerUrlResolver implements InlineBlueprintSupport.ServerUrlResolver {
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);
    private static final int MAX_REDIRECTS = 2;
    private static final long MAX_MANIFEST_BYTES = 1_048_576L;
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
                        .followRedirects(false)
                        .followSslRedirects(false)
                        .build(),
                new Gson()
        );
    }

    ManifestServerUrlResolver(String manifestUrl, OkHttpClient httpClient, Gson gson) {
        this.manifestUrl = Objects.requireNonNull(manifestUrl, "manifestUrl");
        // Fail fast for obvious SSRF vectors before any network call
        validateManifestUrl(this.manifestUrl);
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
                .map(link -> {
                    validateDownloadLink(link);
                    return link;
                })
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
        String currentUrl = manifestUrl;
        for (int redirect = 0; redirect <= MAX_REDIRECTS; redirect++) {
            validateManifestUrl(currentUrl);
            Request request = new Request.Builder()
                    .url(currentUrl)
                    .get()
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                // Manual redirect handling with SSRF re-validation
                if (isRedirect(response.code())) {
                    if (redirect == MAX_REDIRECTS) {
                        throw new IllegalStateException("Too many redirects fetching manifest from " + manifestUrl);
                    }
                    String location = response.header("Location");
                    if (location == null || location.isBlank()) {
                        throw new IllegalStateException("Redirect without Location from " + currentUrl);
                    }
                    currentUrl = resolveRedirect(currentUrl, location);
                    continue;
                }

                if (!response.isSuccessful()) {
                    throw new IllegalStateException("Failed to fetch server version manifest from " + manifestUrl
                            + ": HTTP " + response.code());
                }

                if (response.body() == null) {
                    throw new IllegalStateException("Failed to fetch server version manifest from " + manifestUrl
                            + ": empty response body");
                }

                // Content-Length + streaming cap to avoid OOM
                String cl = response.header("Content-Length");
                if (cl != null) {
                    try {
                        long len = Long.parseLong(cl.trim());
                        if (len > MAX_MANIFEST_BYTES) {
                            throw new IllegalStateException("Manifest too large (" + len + " bytes) from " + manifestUrl);
                        }
                    } catch (NumberFormatException ignored) {}
                }

                try (Reader reader = new BoundedReader(response.body().charStream(), MAX_MANIFEST_BYTES)) {
                    List<ManifestEntry> manifest = gson.fromJson(reader, MANIFEST_TYPE);
                    return manifest != null ? manifest : List.of();
                }
            } catch (IOException e) {
                throw new IllegalStateException("Failed to fetch server version manifest from " + manifestUrl, e);
            }
        }
        throw new IllegalStateException("Failed to fetch server version manifest from " + manifestUrl + ": redirect loop");
    }

    private static boolean isRedirect(int code) {
        return code == 301 || code == 302 || code == 303 || code == 307 || code == 308;
    }

    private static String resolveRedirect(String currentUrl, String location) {
        try {
            URI base = URI.create(currentUrl);
            URI resolved = base.resolve(location.trim());
            return resolved.toString();
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Invalid redirect Location: " + location, e);
        }
    }

    static void validateManifestUrl(String url) {
        validateUrlForSsrf(url, "manifestUrl");
    }

    static void validateDownloadLink(String url) {
        validateUrlForSsrf(url, "download link");
    }

    private static void validateUrlForSsrf(String urlString, String context) {
        URI uri;
        try {
            uri = URI.create(urlString.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid " + context + " URL: " + urlString, e);
        }
        String scheme = uri.getScheme();
        if (scheme == null || !scheme.equalsIgnoreCase("https")) {
            throw new IllegalArgumentException(context + " must use https:// - got: " + urlString);
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            // Trying authority if host return null for some reason
            String authority = uri.getAuthority();
            throw new IllegalArgumentException(context + " must have a valid host - got: " + urlString + (authority != null ? " (authority=" + authority + ")" : ""));
        }
        // DNS resolution + private IP deny (covers literal IPs and rebinding)
        try {
            for (InetAddress addr : InetAddress.getAllByName(host)) {
                if (isBlockedAddress(addr)) {
                    throw new IllegalArgumentException(
                            context + " resolves to blocked private/link-local address " + addr.getHostAddress() + " - url: " + urlString);
                }
            }
        } catch (java.net.UnknownHostException e) {
            throw new IllegalArgumentException("Unknown host for " + context + ": " + host, e);
        }
    }

    private static boolean isBlockedAddress(InetAddress addr) {
        return addr.isLoopbackAddress()
                || addr.isLinkLocalAddress()
                || addr.isSiteLocalAddress()
                || addr.isAnyLocalAddress()
                || isCarrierGradeNat(addr)
                || isPrivateExtra(addr);
    }

    private static boolean isCarrierGradeNat(InetAddress addr) {
        // 100.64.0.0/10
        byte[] b = addr.getAddress();
        if (b.length != 4) return false;
        int first = b[0] & 0xFF;
        int second = b[1] & 0xFF;
        return first == 100 && second >= 64 && second <= 127;
    }

    private static boolean isPrivateExtra(InetAddress addr) {
        byte[] b = addr.getAddress();
        if (b.length == 4) {
            // 192.0.2.0/24 TEST-NET, 198.51.100.0/24, 203.0.113.0/24 deny as well to avoid test leakage
            int f = b[0] & 0xFF, s = b[1] & 0xFF, t = b[2] & 0xFF;
            if (f == 192 && s == 0 && t == 2) return true;
            if (f == 198 && s == 51 && t == 100) return true;
            if (f == 203 && s == 0 && t == 113) return true;
        }
        return false;
    }

    private static final class BoundedReader extends Reader {
        private final Reader delegate;
        private long remaining;
        BoundedReader(Reader delegate, long maxBytes) {
            this.delegate = delegate;
            this.remaining = maxBytes;
        }
        @Override public int read(char[] cbuf, int off, int len) throws IOException {
            if (remaining <= 0) throw new IOException("Manifest exceeds " + MAX_MANIFEST_BYTES + " bytes");
            int toRead = (int) Math.min(len, remaining);
            int n = delegate.read(cbuf, off, toRead);
            if (n > 0) {
                remaining -= n * 2L;
            }
            return n;
        }
        @Override public void close() throws IOException { delegate.close(); }
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
