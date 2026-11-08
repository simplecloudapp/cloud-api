package app.simplecloud.api.internal;

import app.simplecloud.api.CloudApiOptions;
import app.simplecloud.api.internal.web.ApiClientFactory;
import app.simplecloud.api.web.ApiClient;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.HttpUrl;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public final class ControllerServer implements AutoCloseable {

    public static final String NETWORK_ID = "test-network";

    private final MockWebServer server = new MockWebServer();

    public ControllerServer() {
        try {
            server.start();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public CloudApiOptions options() {
        String url = server.url("/").toString();
        return CloudApiOptions.builder()
                .controllerUrl(url.substring(0, url.length() - 1))
                .networkId(NETWORK_ID)
                .networkSecret("secret")
                .build();
    }

    public ApiClient apiClient() {
        return ApiClientFactory.create(options());
    }

    public void respond(int status, String json) {
        server.enqueue(new MockResponse()
                .setResponseCode(status)
                .setHeader("Content-Type", "application/json")
                .setBody(json));
    }

    public RecordedRequest takeRequest() {
        try {
            RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
            if (request == null) {
                throw new AssertionError("Expected a request to the controller, but none arrived");
            }
            return request;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }

    public static HttpUrl url(RecordedRequest request) {
        return Objects.requireNonNull(request.getRequestUrl(), "request URL");
    }

    public static JsonObject body(RecordedRequest request) {
        return JsonParser.parseString(request.getBody().readUtf8()).getAsJsonObject();
    }

    public int requestCount() {
        return server.getRequestCount();
    }

    @Override
    public void close() {
        try {
            server.shutdown();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
