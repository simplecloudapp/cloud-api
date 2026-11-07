package app.simplecloud.api.internal.blueprint;

import app.simplecloud.api.CloudApiException;
import app.simplecloud.api.blueprint.CreateBlueprintRequest;
import com.google.gson.Gson;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ManifestServerUrlResolverTest {

    private static final CreateBlueprintRequest PAPER = CreateBlueprintRequest.builder()
            .serverSoftware("paper")
            .minecraftVersion("1.21.4")
            .build();

    @Test
    void manifestIsNotFetchedWithoutSoftwareAndVersion() {
        ManifestServerUrlResolver resolver = resolver("not a url");

        assertNull(resolver.resolve(
                CreateBlueprintRequest.builder().minecraftVersion("1.21.4").build()));
        assertNull(resolver.resolve(
                CreateBlueprintRequest.builder().serverSoftware("paper").build()));
    }

    @Test
    void plainHttpManifestIsRefused() {
        CloudApiException exception = assertThrows(
                CloudApiException.class,
                () -> resolver("http://example.com/manifest.json").resolve(PAPER));

        assertEquals(CloudApiException.Reason.UNAVAILABLE, exception.getReason());
    }

    @Test
    void manifestOnPrivateAddressIsRefused() {
        CloudApiException exception = assertThrows(
                CloudApiException.class,
                () -> resolver("https://127.0.0.1/manifest.json").resolve(PAPER));

        assertEquals(CloudApiException.Reason.UNAVAILABLE, exception.getReason());
    }

    private static ManifestServerUrlResolver resolver(String manifestUrl) {
        return new ManifestServerUrlResolver(manifestUrl, new OkHttpClient(), Duration.ofSeconds(5), new Gson());
    }
}
