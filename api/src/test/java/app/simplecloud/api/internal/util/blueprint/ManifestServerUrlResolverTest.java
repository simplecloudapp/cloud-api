package app.simplecloud.api.internal.util.blueprint;

import app.simplecloud.api.CloudApiException;
import app.simplecloud.api.blueprint.CreateBlueprintRequest;
import app.simplecloud.api.support.ControllerServer;
import com.google.gson.Gson;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ManifestServerUrlResolverTest {

    private static final String MANIFEST = "/server_versions.json";
    private static final String MANIFEST_JSON = """
            [
              {"name": "Paper", "downloadLinks": [
                {"version": "1.21.3", "link": "https://example.com/paper-1.21.3.jar"},
                {"version": "1.21.4", "link": "https://example.com/paper-1.21.4.jar"}
              ]},
              {"name": "Velocity", "downloadLinks": [
                {"version": "3.4.0", "link": "https://example.com/velocity-3.4.0.jar"}
              ]}
            ]
            """;

    private final ControllerServer server = ControllerServer.start();
    private final ManifestServerUrlResolver resolver = new ManifestServerUrlResolver(
            server.url() + MANIFEST, new OkHttpClient(), Duration.ofSeconds(5), new Gson()
    );

    @AfterEach
    void stopServer() {
        server.close();
    }

    @Test
    void resolvesLinkBySoftwareNameIgnoringCase() {
        server.on("GET", MANIFEST, MANIFEST_JSON);

        assertEquals("https://example.com/paper-1.21.4.jar", resolver.resolve(request("paper", "1.21.4", null)));
    }

    @Test
    void minecraftVersionWinsOverSoftwareVersion() {
        server.on("GET", MANIFEST, MANIFEST_JSON);

        assertEquals("https://example.com/paper-1.21.3.jar", resolver.resolve(request("paper", "1.21.3", "1.21.4")));
        assertEquals("https://example.com/velocity-3.4.0.jar", resolver.resolve(request("velocity", null, "3.4.0")));
    }

    @Test
    void unknownVersionResolvesToNothing() {
        server.on("GET", MANIFEST, MANIFEST_JSON);

        assertNull(resolver.resolve(request("paper", "0.0.1", null)));
    }

    @Test
    void manifestIsFetchedOnlyOnce() {
        server.on("GET", MANIFEST, MANIFEST_JSON);

        resolver.resolve(request("paper", "1.21.4", null));
        resolver.resolve(request("velocity", null, "3.4.0"));

        assertEquals(1, server.requests().size());
    }

    @Test
    void failedFetchIsReportedAsUnavailable() {
        server.on("GET", MANIFEST, 500, "{}");

        CloudApiException exception = assertThrows(CloudApiException.class, () -> resolver.resolve(request("paper", "1.21.4", null)));

        assertEquals(CloudApiException.Reason.UNAVAILABLE, exception.getReason());
    }

    @Test
    void oversizedManifestIsRejected() {
        server.on("GET", MANIFEST, "[" + " ".repeat((int) ManifestServerUrlResolver.MAX_MANIFEST_BYTES) + "]");

        CloudApiException exception = assertThrows(CloudApiException.class, () -> resolver.resolve(request("paper", "1.21.4", null)));

        assertEquals(CloudApiException.Reason.UNAVAILABLE, exception.getReason());
    }

    private static CreateBlueprintRequest request(String software, String minecraftVersion, String softwareVersion) {
        return CreateBlueprintRequest.builder()
                .serverSoftware(software)
                .minecraftVersion(minecraftVersion)
                .softwareVersion(softwareVersion)
                .build();
    }
}
