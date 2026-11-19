package app.simplecloud.api.internal;

import app.simplecloud.api.CloudApiOptions;
import app.simplecloud.api.internal.nats.NatsFailoverConnectionManager;
import app.simplecloud.api.internal.networkstate.MonotonicClock;
import app.simplecloud.api.internal.networkstate.NetworkView;
import app.simplecloud.api.internal.networkstate.NetworkViewMachine;
import app.simplecloud.api.internal.networkstate.NetworkViewShell;
import app.simplecloud.api.internal.networkstate.RandomSequence;
import app.simplecloud.api.internal.networkstate.SnapshotLoader;
import app.simplecloud.api.internal.networkstate.StateRevisionInterceptor;
import app.simplecloud.api.internal.networkstate.read.ReadableCopies;
import app.simplecloud.api.internal.web.ApiClients;
import app.simplecloud.api.web.ApiClient;
import okhttp3.OkHttpClient;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * What the {@code CloudApi} instances of one class loader with equal settings share: one NATS connection,
 * one HTTP client and, when their network and credential allow one, one view of the network, which every write
 * through that client reaches. No instance owns it. The first instance opens it, and the last one to release it
 * closes it, so the order in which plugins stop does not matter. An instance created after that opens a new one.
 */
final class SharedRuntime {

    private static final Map<Settings, SharedRuntime> OPEN = new HashMap<>();

    private final Settings settings;
    private final NatsFailoverConnectionManager natsConnectionManager;
    private final OkHttpClient httpClient;
    private final @Nullable NetworkViewShell networkViewShell;
    private final ReadableCopies readableCopies;
    private int users;

    private SharedRuntime(Settings settings, CloudApiOptions options) {
        this.settings = settings;
        // Built by the generated ApiClient, so it has the progress interceptor the generated code adds.
        OkHttpClient baseHttpClient = new ApiClient().getHttpClient();
        // Connects in the background, so opening under the lock does not block other instances.
        this.natsConnectionManager = new NatsFailoverConnectionManager(
                settings.natsUrl(),
                settings.networkId(),
                settings.networkSecret(),
                settings.natsFailoverReconnectAfter()
        );
        try {
            // On the base client, because the client that reports writes needs the view's shell. The view's
            // snapshot loads are reads, which would report nothing anyway.
            this.networkViewShell = NetworkViewEligibility.allows(options)
                    ? openNetworkViewShell(options, natsConnectionManager, baseHttpClient)
                    : null;
        } catch (RuntimeException | Error e) {
            // Under the lock, so other instances wait for this NATS close; it runs only when the view failed to open.
            try {
                closeConnections(baseHttpClient);
            } catch (RuntimeException closeFailure) {
                e.addSuppressed(closeFailure);
            }
            throw e;
        }
        if (networkViewShell == null) {
            this.httpClient = baseHttpClient;
            this.readableCopies = ReadableCopies.none();
        } else {
            this.httpClient = StateRevisionInterceptor.newClientReportingWrites(baseHttpClient, networkViewShell);
            this.readableCopies = new ReadableCopies(networkViewShell, settings.networkId());
        }
    }

    /**
     * Starts the view on the given connections, which it leaves open when it closes. It does not block: the first
     * snapshot load starts in the background once the subscriptions are confirmed. The view uses the
     * connection settings, timeouts and component header of {@code options}, the options of the first
     * instance that uses it.
     */
    private static NetworkViewShell openNetworkViewShell(
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
        return shell;
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

    /**
     * Returns the client for all of the instances' requests. When there is a view, it hands the view the state
     * revision of each write, so that a read after a write waits for the copy to have it.
     */
    OkHttpClient httpClient() {
        return httpClient;
    }

    /**
     * Returns the shell that runs the view of the instances, or null when their network and credential allow
     * none. For tests of this package.
     */
    @Nullable NetworkViewShell networkViewShell() {
        return networkViewShell;
    }

    /**
     * Returns the copies that answer the reads of all instances while the view lets them. When the network and
     * credential of the instances allow no view, it never hands out a copy.
     */
    ReadableCopies readableCopies() {
        return readableCopies;
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
        if (networkViewShell != null) {
            closeNetworkViewShell();
        }
        closeConnections(httpClient);
    }

    private void closeNetworkViewShell() {
        natsConnectionManager.removeSubscriptionListener(networkViewShell);
        networkViewShell.close();
    }

    /**
     * Either client will do: the one that reports writes to the view shares the base client's dispatcher and
     * connection pool.
     */
    private void closeConnections(OkHttpClient client) {
        try {
            natsConnectionManager.shutdown();
        } finally {
            client.dispatcher().executorService().shutdown();
            client.connectionPool().evictAll();
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
