package app.simplecloud.api.internal;

import app.simplecloud.api.CloudApiOptions;
import app.simplecloud.api.internal.nats.NatsFailoverConnectionManager;
import app.simplecloud.api.internal.networkcopy.NetworkCopy;
import app.simplecloud.api.web.ApiClient;
import okhttp3.OkHttpClient;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * What the {@code CloudApi} instances of one class loader with equal settings share: one NATS connection,
 * one HTTP client and one network copy. No instance owns it. The first instance opens it, and the last one to
 * release it closes it, so the order in which plugins stop does not matter. An instance created after that opens
 * a new one.
 */
final class SharedRuntime {

    private static final Map<Settings, SharedRuntime> OPEN = new HashMap<>();

    private final Settings settings;
    private final NatsFailoverConnectionManager natsConnectionManager;
    private final OkHttpClient httpClient;
    private final NetworkCopy networkCopy;
    private int users;

    private SharedRuntime(Settings settings) {
        this.settings = settings;
        // Built by the generated ApiClient, so it has the progress interceptor the generated code adds.
        this.httpClient = new ApiClient().getHttpClient();
        // Connects in the background, so opening under the lock does not block other instances.
        this.natsConnectionManager = new NatsFailoverConnectionManager(
                settings.natsUrl(),
                settings.networkId(),
                settings.networkSecret(),
                settings.natsFailoverReconnectAfter()
        );
        this.networkCopy = new NetworkCopy(
                httpClient,
                settings.controllerUrl(),
                settings.networkId(),
                settings.networkSecret()
        );
        networkCopy.start(natsConnectionManager.getConnection());
    }

    static SharedRuntime acquire(CloudApiOptions options) {
        synchronized (OPEN) {
            SharedRuntime runtime = OPEN.computeIfAbsent(Settings.of(options), SharedRuntime::new);
            runtime.users++;
            return runtime;
        }
    }

    NatsFailoverConnectionManager natsConnectionManager() {
        return natsConnectionManager;
    }

    OkHttpClient httpClient() {
        return httpClient;
    }

    NetworkCopy networkCopy() {
        return networkCopy;
    }

    /**
     * Call once for each {@link #acquire}. The last release closes the network copy, the NATS connection
     * and the HTTP client.
     */
    void release() {
        synchronized (OPEN) {
            if (--users > 0) {
                return;
            }
            OPEN.remove(settings);
        }
        // Outside the lock, because the NATS close can block for about a second while it flushes.
        try {
            networkCopy.close();
            natsConnectionManager.shutdown();
        } finally {
            httpClient.dispatcher().executorService().shutdown();
            httpClient.connectionPool().evictAll();
        }
    }

    private record Settings(
            String natsUrl,
            String networkId,
            String networkSecret,
            Duration natsFailoverReconnectAfter,
            String controllerUrl
    ) {

        static Settings of(CloudApiOptions options) {
            return new Settings(
                    options.getNatsUrl(),
                    options.getNetworkId(),
                    options.getNetworkSecret(),
                    options.getNatsFailoverReconnectAfter(),
                    options.getControllerUrl()
            );
        }
    }
}
