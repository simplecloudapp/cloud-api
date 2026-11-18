package app.simplecloud.api.internal;

import app.simplecloud.api.CloudApiOptions;
import app.simplecloud.api.internal.nats.NatsFailoverConnectionManager;
import app.simplecloud.api.internal.networkstate.NetworkViewShell;
import app.simplecloud.api.web.ApiClient;
import okhttp3.OkHttpClient;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * What the {@code CloudApi} instances of one class loader with equal settings share: one NATS connection,
 * one HTTP client and, when their network and credential allow one, one view of the network. No instance owns
 * it. The first instance opens it, and the last one to release it closes it, so the order in which plugins stop
 * does not matter. An instance created after that opens a new one.
 */
final class SharedRuntime {

    private static final Map<Settings, SharedRuntime> OPEN = new HashMap<>();

    private final Settings settings;
    private final NatsFailoverConnectionManager natsConnectionManager;
    private final OkHttpClient httpClient;
    private final SharedNetworkView networkView;
    private int users;

    private SharedRuntime(Settings settings, CloudApiOptions options) {
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
        try {
            this.networkView = NetworkViewEligibility.allows(options)
                    ? SharedNetworkView.open(options, natsConnectionManager, httpClient)
                    : null;
        } catch (RuntimeException | Error e) {
            // Under the lock, so other instances wait for this NATS close; it runs only when the view failed to open.
            try {
                closeConnections();
            } catch (RuntimeException closeFailure) {
                e.addSuppressed(closeFailure);
            }
            throw e;
        }
    }

    static SharedRuntime acquire(CloudApiOptions options) {
        synchronized (OPEN) {
            SharedRuntime runtime = OPEN.computeIfAbsent(Settings.of(options), settings -> new SharedRuntime(settings, options));
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

    /**
     * Returns null when the network and credential of the instances allow no view.
     */
    NetworkViewShell networkViewShell() {
        return networkView == null ? null : networkView.shell();
    }

    /**
     * Call once for each {@link #acquire}. The last release closes the view, the NATS connection and the HTTP
     * client.
     */
    void release() {
        synchronized (OPEN) {
            if (--users > 0) {
                return;
            }
            OPEN.remove(settings);
        }
        // Outside the lock, because the NATS close can block for about a second while it flushes.
        if (networkView != null) {
            networkView.close();
        }
        closeConnections();
    }

    private void closeConnections() {
        try {
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
