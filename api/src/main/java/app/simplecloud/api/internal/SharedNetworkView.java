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
 * The view of one network that the {@code CloudApi} instances of one {@link SharedRuntime} share, on that
 * runtime's connections.
 */
final class SharedNetworkView {

    private final NetworkViewShell shell;
    private final NatsFailoverConnectionManager natsConnectionManager;

    private SharedNetworkView(NetworkViewShell shell, NatsFailoverConnectionManager natsConnectionManager) {
        this.shell = shell;
        this.natsConnectionManager = natsConnectionManager;
    }

    /**
     * Starts the view on the given connections, which it leaves open when it closes. It does not block: the first
     * snapshot load starts in the background once the subscriptions are confirmed. The view uses the
     * connection settings, timeouts and component header of {@code options}, the options of the first
     * instance that uses it.
     */
    static SharedNetworkView open(
            CloudApiOptions options,
            NatsFailoverConnectionManager natsConnectionManager,
            OkHttpClient httpClient
    ) {
        ApiClient apiClient = ApiClients.create(options, httpClient);
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
        try {
            natsConnectionManager.addSubscriptionListener(shell);
        } catch (RuntimeException | Error e) {
            // The listener may be in the list already: the first call to it can throw.
            natsConnectionManager.removeSubscriptionListener(shell);
            shell.close();
            throw e;
        }
        return new SharedNetworkView(shell, natsConnectionManager);
    }

    NetworkViewShell shell() {
        return shell;
    }

    void close() {
        natsConnectionManager.removeSubscriptionListener(shell);
        shell.close();
    }
}
