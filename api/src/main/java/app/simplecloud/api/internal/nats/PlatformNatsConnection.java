package app.simplecloud.api.internal.nats;

import app.simplecloud.api.CloudApiOptions;

import java.time.Duration;
import java.util.Optional;

/**
 * The NATS connection the platform plugin opened for this class loader. Other {@code CloudApi}
 * instances with the same settings use it instead of opening their own connection.
 */
public final class PlatformNatsConnection {

    private static PlatformNatsConnection registered;

    private final Settings settings;
    private final NatsFailoverConnectionManager connectionManager;

    private PlatformNatsConnection(Settings settings, NatsFailoverConnectionManager connectionManager) {
        this.settings = settings;
        this.connectionManager = connectionManager;
    }

    public static synchronized Optional<NatsFailoverConnectionManager> find(Settings settings) {
        if (registered == null || !registered.settings.equals(settings)) {
            return Optional.empty();
        }
        return Optional.of(registered.connectionManager);
    }

    /**
     * Registers {@code connectionManager} unless another connection is registered already.
     */
    public static synchronized void register(Settings settings, NatsFailoverConnectionManager connectionManager) {
        if (registered == null) {
            registered = new PlatformNatsConnection(settings, connectionManager);
        }
    }

    /**
     * Unregisters {@code connectionManager} if it is the registered connection.
     */
    public static synchronized void unregister(NatsFailoverConnectionManager connectionManager) {
        if (registered != null && registered.connectionManager == connectionManager) {
            registered = null;
        }
    }

    public record Settings(String natsUrl, String networkId, String networkSecret, Duration failoverReconnectAfter) {

        public static Settings of(CloudApiOptions options) {
            return new Settings(
                    options.getNatsUrl(),
                    options.getNetworkId(),
                    options.getNetworkSecret(),
                    options.getNatsFailoverReconnectAfter()
            );
        }

        public NatsFailoverConnectionManager openConnectionManager() {
            return new NatsFailoverConnectionManager(natsUrl, networkId, networkSecret, failoverReconnectAfter);
        }
    }
}
