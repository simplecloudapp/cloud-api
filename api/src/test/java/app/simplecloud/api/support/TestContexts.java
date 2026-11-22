package app.simplecloud.api.support;

import app.simplecloud.api.CloudApiOptions;
import app.simplecloud.api.cache.QueryCache;
import app.simplecloud.api.internal.ApiContext;
import app.simplecloud.api.internal.cache.NoOpQueryCache;
import app.simplecloud.api.internal.web.ApiClientFactory;

public final class TestContexts {

    public static final String NETWORK_ID = "network-1";
    public static final String NETWORK_SECRET = "secret";

    private TestContexts() {
    }

    public static ApiContext create(ControllerServer controller) {
        return create(controller, new NoOpQueryCache());
    }

    public static ApiContext create(ControllerServer controller, QueryCache cache) {
        return create(options(controller.url(), NETWORK_ID), cache);
    }

    public static ApiContext forNetwork(String networkId) {
        return create(options("http://127.0.0.1:1", networkId), new NoOpQueryCache());
    }

    private static ApiContext create(CloudApiOptions options, QueryCache cache) {
        return new ApiContext(options, cache, ApiClientFactory.create(options), Runnable::run);
    }

    private static CloudApiOptions options(String controllerUrl, String networkId) {
        return CloudApiOptions.builder()
                .controllerUrl(controllerUrl)
                .natsUrl("nats://127.0.0.1:1")
                .networkId(networkId)
                .networkSecret(NETWORK_SECRET)
                .serverVersionManifestUrl("https://manifest.invalid/server_versions.json")
                .disableCache()
                .build();
    }
}
