package app.simplecloud.api.internal.web;

import app.simplecloud.api.CloudApiOptions;
import app.simplecloud.api.web.ApiClient;

import java.time.Duration;

/**
 * Helpers for configuring the generated {@link ApiClient}.
 */
public final class ApiClients {

    private ApiClients() {
    }

    /**
     * Creates an isolated client. Generated API no-arg constructors use a global
     * default client, so configuring that client would change headers and timeouts
     * for other CloudApi instances in the same class loader.
     */
    public static ApiClient create(CloudApiOptions options) {
        ApiClient client = new ApiClient();
        applyTimeouts(client, options);
        if (options.getComponent() != null && !options.getComponent().isBlank()) {
            client.addDefaultHeader("X-SC-Component", options.getComponent());
        }
        return client;
    }

    /**
     * Applies HTTP connect/read/write timeouts from {@link CloudApiOptions} to the given
     * {@link ApiClient}. Bounded timeouts are required so that REST calls cannot hang
     * indefinitely — an indefinitely-hanging request traps the query cache's in-flight
     * map entry (via deduplication), and every subsequent caller with the same key
     * shares the zombie future and times out client-side.
     */
    public static void applyTimeouts(ApiClient client, CloudApiOptions options) {
        client.setConnectTimeout(toMillis(options.getHttpConnectTimeout()));
        client.setReadTimeout(toMillis(options.getHttpReadTimeout()));
        client.setWriteTimeout(toMillis(options.getHttpWriteTimeout()));
    }

    private static int toMillis(Duration duration) {
        long millis = duration.toMillis();
        return millis > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) millis;
    }
}
