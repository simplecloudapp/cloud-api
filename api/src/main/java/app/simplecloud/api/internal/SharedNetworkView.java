package app.simplecloud.api.internal;

import app.simplecloud.api.CloudApiOptions;
import app.simplecloud.api.internal.nats.NatsFailoverConnectionManager;
import app.simplecloud.api.internal.networkstate.MonotonicClock;
import app.simplecloud.api.internal.networkstate.NetworkView;
import app.simplecloud.api.internal.networkstate.NetworkViewMachine;
import app.simplecloud.api.internal.networkstate.NetworkViewShell;
import app.simplecloud.api.internal.networkstate.RandomSequence;
import app.simplecloud.api.internal.networkstate.SnapshotLoader;
import app.simplecloud.api.internal.web.ApiClients;
import app.simplecloud.api.web.ApiClient;
import okhttp3.OkHttpClient;

/**
 * The view of one network that the {@code CloudApi} instances of this class loader with the same
 * {@link NetworkViewKey} share, with its own handles on the shared connections. It lives until the last of
 * those instances closes, so closing the platform plugin's instance does not end it for other plugins.
 */
final class SharedNetworkView {

    private final NetworkViewShell shell;
    private final SharedResources.Handle<NatsFailoverConnectionManager> natsConnectionManagerHandle;
    private final SharedResources.Handle<OkHttpClient> httpClientHandle;

    private SharedNetworkView(
            NetworkViewShell shell,
            SharedResources.Handle<NatsFailoverConnectionManager> natsConnectionManagerHandle,
            SharedResources.Handle<OkHttpClient> httpClientHandle
    ) {
        this.shell = shell;
        this.natsConnectionManagerHandle = natsConnectionManagerHandle;
        this.httpClientHandle = httpClientHandle;
    }

    /**
     * Starts the view on the given handles, which it closes when it closes. It does not block: the first
     * snapshot load starts in the background once the subscriptions are confirmed. The view uses the
     * connection settings, timeouts and component header of {@code options}, the options of the first
     * instance that uses it.
     */
    static SharedNetworkView open(
            CloudApiOptions options,
            SharedResources.Handle<NatsFailoverConnectionManager> natsConnectionManagerHandle,
            SharedResources.Handle<OkHttpClient> httpClientHandle
    ) {
        NatsFailoverConnectionManager natsConnectionManager = natsConnectionManagerHandle.resource();
        ApiClient apiClient = ApiClients.create(options, httpClientHandle.resource());
        SnapshotLoader snapshots = new SnapshotLoader(apiClient, options.getControllerUrl(), options.getNetworkId());
        RandomSequence random = RandomSequence.seeded(System.nanoTime());
        NetworkView startView = NetworkViewMachine.start(random);
        NetworkViewShell shell = NetworkViewShell.start(
                options.getNetworkId(),
                natsConnectionManager.getConnection(),
                snapshots,
                startView,
                MonotonicClock.SYSTEM
        );
        natsConnectionManager.addSubscriptionListener(shell);
        return new SharedNetworkView(shell, natsConnectionManagerHandle, httpClientHandle);
    }

    NetworkViewShell shell() {
        return shell;
    }

    void close() {
        natsConnectionManagerHandle.resource().removeSubscriptionListener(shell);
        shell.close();
        natsConnectionManagerHandle.close();
        httpClientHandle.close();
    }
}
