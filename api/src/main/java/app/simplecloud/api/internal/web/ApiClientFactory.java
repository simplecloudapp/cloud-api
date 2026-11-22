package app.simplecloud.api.internal.web;

import app.simplecloud.api.CloudApiOptions;
import app.simplecloud.api.web.ApiClient;
import app.simplecloud.api.web.ApiException;
import app.simplecloud.api.web.auth.ApiKeyAuth;
import com.google.gson.JsonParseException;
import okhttp3.Response;

import java.lang.reflect.Type;
import java.time.Duration;

/**
 * Helpers for configuring the generated {@link ApiClient}.
 */
public final class ApiClientFactory {

    private ApiClientFactory() {
    }
    
    public static ApiClient create(CloudApiOptions options) {
        ApiClient client = new ResponseValidatingApiClient();
        client.setBasePath(options.getControllerUrl());
        applyTimeouts(client, options);
        ApiKeyAuth credential = (ApiKeyAuth) client.getAuthentication("NetworkCredential");
        credential.setApiKey(options.getNetworkSecret());
        if (options.getComponent() != null && !options.getComponent().isBlank()) {
            client.addDefaultHeader("X-SC-Component", options.getComponent());
        }
        return client;
    }

    public static void applyTimeouts(ApiClient client, CloudApiOptions options) {
        client.setConnectTimeout(toMillis(options.getHttpConnectTimeout()));
        client.setReadTimeout(toMillis(options.getHttpReadTimeout()));
        client.setWriteTimeout(toMillis(options.getHttpWriteTimeout()));
    }

    public static Duration maxRequestDuration(CloudApiOptions options) {
        if (options.getHttpConnectTimeout().isZero()
                || options.getHttpWriteTimeout().isZero()
                || options.getHttpReadTimeout().isZero()) {
            return Duration.ofSeconds(60);
        }
        return options.getHttpConnectTimeout()
                .plus(options.getHttpWriteTimeout())
                .plus(options.getHttpReadTimeout())
                .plus(Duration.ofSeconds(5));
    }

    private static int toMillis(Duration duration) {
        long millis = duration.toMillis();
        return millis > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) millis;
    }

    static final class ResponseValidatingApiClient extends ApiClient {

        @Override
        public <T> T deserialize(Response response, Type returnType) throws ApiException {
            try {
                return super.deserialize(response, returnType);
            } catch (JsonParseException | IllegalArgumentException | IllegalStateException e) {
                throw new ApiException(
                        "Invalid controller response: " + e.getMessage(),
                        e,
                        response.code(),
                        response.headers().toMultimap()
                );
            }
        }
    }
}
