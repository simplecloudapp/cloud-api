package app.simplecloud.api.internal.blueprint;

import app.simplecloud.api.CloudApiOptions;
import app.simplecloud.api.blueprint.CreateBlueprintRequest;
import com.google.gson.Gson;
import okhttp3.Dns;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.tls.HandshakeCertificates;
import okhttp3.tls.HeldCertificate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManifestServerUrlResolverTest {
    private static final String HOST = "manifest.test";
    private static final String MANIFEST = """
            [
              {
                "name": "paper",
                "downloadLinks": [
                  { "version": "1.21.11", "link": "https://example.com/paper-1.21.11.jar" }
                ]
              }
            ]
            """;

    private static final HeldCertificate CERTIFICATE = new HeldCertificate.Builder()
            .addSubjectAlternativeName(HOST)
            .build();
    private static final Dns LOOPBACK_DNS = hostname -> List.of(InetAddress.getLoopbackAddress());

    private MockWebServer server;

    @BeforeEach
    void startServer() throws IOException {
        server = new MockWebServer();
        server.useHttps(new HandshakeCertificates.Builder()
                .heldCertificate(CERTIFICATE)
                .build()
                .sslSocketFactory(), false);
        server.start();
    }

    @AfterEach
    void stopServer() throws IOException {
        server.close();
    }

    @Test
    void resolve_returnsDownloadLinkForMinecraftVersion() {
        server.enqueue(new MockResponse().setBody(MANIFEST));

        assertEquals("https://example.com/paper-1.21.11.jar", resolver(manifestUrl()).resolve(paper("1.21.11")));
    }

    @Test
    void resolve_returnsNullWhenVersionIsMissingFromManifest() {
        server.enqueue(new MockResponse().setBody(MANIFEST));

        assertNull(resolver(manifestUrl()).resolve(paper("1.21.10")));
    }

    @Test
    void resolve_doesNotTouchManifestWithoutSoftwareOrVersion() {
        assertNull(resolver(manifestUrl()).resolve(CreateBlueprintRequest.builder().serverSoftware("paper").build()));
        assertEquals(0, server.getRequestCount());
    }

    @Test
    void resolve_rejectsHttpManifestUrl() {
        String httpUrl = server.url("/server_versions.json").newBuilder().scheme("http").host(HOST).build().toString();

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> resolver(httpUrl).resolve(paper("1.21.11"))
        );

        assertTrue(failure.getMessage().contains("non-HTTPS"), failure.getMessage());
        assertEquals(0, server.getRequestCount());
    }

    @Test
    void resolve_rejectsPrivateIpLiteralManifestUrl() {
        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> resolver("https://127.0.0.1:" + server.getPort() + "/server_versions.json").resolve(paper("1.21.11"))
        );

        assertInstanceOf(UnknownHostException.class, failure.getCause());
        assertEquals(0, server.getRequestCount());
    }

    @Test
    void resolve_rejectsHostResolvingToPrivateAddress() throws UnknownHostException {
        InetAddress privateAddress = InetAddress.getByName("10.0.0.5");
        ManifestServerUrlResolver resolver = resolver(
                manifestUrl(),
                new PublicAddressDns(hostname -> List.of(privateAddress))
        );

        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> resolver.resolve(paper("1.21.11")));

        assertInstanceOf(UnknownHostException.class, failure.getCause());
    }

    @Test
    void resolve_productionClientRejectsHostResolvingToLoopback() {
        ManifestServerUrlResolver resolver = new ManifestServerUrlResolver(CloudApiOptions.builder()
                .serverVersionManifestUrl("https://localhost:" + server.getPort() + "/server_versions.json")
                .build());

        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> resolver.resolve(paper("1.21.11")));

        assertInstanceOf(UnknownHostException.class, failure.getCause());
        assertEquals(0, server.getRequestCount());
    }

    @Test
    void resolve_followsRedirects() {
        server.enqueue(redirectTo("/moved.json"));
        server.enqueue(new MockResponse().setBody(MANIFEST));

        assertEquals("https://example.com/paper-1.21.11.jar", resolver(manifestUrl()).resolve(paper("1.21.11")));
        assertEquals(2, server.getRequestCount());
    }

    @Test
    void resolve_rejectsRedirectToHttp() {
        server.enqueue(redirectTo("http://" + HOST + ":" + server.getPort() + "/server_versions.json"));

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> resolver(manifestUrl()).resolve(paper("1.21.11"))
        );

        assertTrue(failure.getMessage().contains("non-HTTPS"), failure.getMessage());
        assertEquals(1, server.getRequestCount());
    }

    @Test
    void resolve_rejectsRedirectToPrivateAddress() {
        server.enqueue(redirectTo("https://169.254.169.254/latest/meta-data/"));

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> resolver(manifestUrl()).resolve(paper("1.21.11"))
        );

        assertInstanceOf(UnknownHostException.class, failure.getCause());
    }

    @Test
    void resolve_rejectsRedirectWithoutLocation() {
        server.enqueue(new MockResponse().setResponseCode(302));

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> resolver(manifestUrl()).resolve(paper("1.21.11"))
        );

        assertTrue(failure.getMessage().contains("without Location"), failure.getMessage());
    }

    @Test
    void resolve_capsRedirects() {
        for (int i = 0; i <= ManifestServerUrlResolver.MAX_REDIRECTS; i++) {
            server.enqueue(redirectTo("/server_versions.json"));
        }

        assertThrows(IllegalStateException.class, () -> resolver(manifestUrl()).resolve(paper("1.21.11")));
        assertEquals(ManifestServerUrlResolver.MAX_REDIRECTS + 1, server.getRequestCount());
    }

    @Test
    void resolve_acceptsManifestAtSizeLimit() {
        server.enqueue(new MockResponse().setBody(paddedManifest(ManifestServerUrlResolver.MAX_MANIFEST_BYTES)));

        assertNull(resolver(manifestUrl()).resolve(paper("1.21.11")));
    }

    @Test
    void resolve_rejectsManifestOverSizeLimit() {
        server.enqueue(new MockResponse().setBody(paddedManifest(ManifestServerUrlResolver.MAX_MANIFEST_BYTES + 1)));

        assertThrows(IllegalStateException.class, () -> resolver(manifestUrl()).resolve(paper("1.21.11")));
    }

    @Test
    void resolve_rejectsChunkedManifestOverSizeLimit() {
        server.enqueue(new MockResponse().setChunkedBody(
                paddedManifest(ManifestServerUrlResolver.MAX_MANIFEST_BYTES + 1),
                64 * 1024
        ));

        assertThrows(IllegalStateException.class, () -> resolver(manifestUrl()).resolve(paper("1.21.11")));
    }

    @Test
    void resolve_enforcesOverallDeadline() {
        // Each read arrives well within the read timeout, but the whole body takes longer than the deadline.
        server.enqueue(new MockResponse().setBody(MANIFEST).throttleBody(16, 100, TimeUnit.MILLISECONDS));

        assertThrows(
                IllegalStateException.class,
                () -> resolver(manifestUrl(), LOOPBACK_DNS, Duration.ofMillis(300)).resolve(paper("1.21.11"))
        );
    }

    @Test
    void resolve_keepsCredentialsOutOfErrors() {
        server.enqueue(new MockResponse().setResponseCode(500));
        String url = server.url("/server_versions.json").newBuilder()
                .host(HOST)
                .username("user")
                .password("hunter2")
                .addQueryParameter("token", "s3cret")
                .build()
                .toString();

        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> resolver(url).resolve(paper("1.21.11")));

        assertFalse(failure.getMessage().contains("hunter2"), failure.getMessage());
        assertFalse(failure.getMessage().contains("s3cret"), failure.getMessage());
    }

    @Test
    void resolve_wrapsMalformedManifest() {
        server.enqueue(new MockResponse().setBody("{ not json"));

        assertThrows(IllegalStateException.class, () -> resolver(manifestUrl()).resolve(paper("1.21.11")));
    }

    private ManifestServerUrlResolver resolver(String manifestUrl) {
        return resolver(manifestUrl, LOOPBACK_DNS);
    }

    private ManifestServerUrlResolver resolver(String manifestUrl, Dns dns) {
        return resolver(manifestUrl, dns, Duration.ofSeconds(10));
    }

    private ManifestServerUrlResolver resolver(String manifestUrl, Dns dns, Duration fetchTimeout) {
        HandshakeCertificates trust = new HandshakeCertificates.Builder()
                .addTrustedCertificate(CERTIFICATE.certificate())
                .build();
        OkHttpClient client = new OkHttpClient.Builder()
                .sslSocketFactory(trust.sslSocketFactory(), trust.trustManager())
                .dns(dns)
                .build();
        return new ManifestServerUrlResolver(manifestUrl, client, fetchTimeout, new Gson());
    }

    private String manifestUrl() {
        return server.url("/server_versions.json").newBuilder().host(HOST).build().toString();
    }

    private MockResponse redirectTo(String location) {
        return new MockResponse().setResponseCode(302).setHeader("Location", location);
    }

    private static CreateBlueprintRequest paper(String minecraftVersion) {
        return CreateBlueprintRequest.builder()
                .serverSoftware("paper")
                .minecraftVersion(minecraftVersion)
                .build();
    }

    private static String paddedManifest(long sizeBytes) {
        return "[" + " ".repeat((int) sizeBytes - 2) + "]";
    }
}
