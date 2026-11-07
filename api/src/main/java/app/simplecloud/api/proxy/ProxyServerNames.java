package app.simplecloud.api.proxy;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Maps server IDs to the names under which this proxy registered the servers.
 */
public final class ProxyServerNames {

    private static final AtomicReference<Registration> CURRENT_REGISTRATION = new AtomicReference<>();

    private ProxyServerNames() {}

    /**
     * Registers the lookup, replacing any previously registered one.
     *
     * @param lookup finds the proxy name of a server ID
     * @return the registration; close it to remove the lookup again
     */
    public static Registration register(Lookup lookup) {
        Objects.requireNonNull(lookup, "lookup");
        Registration registration = new Registration(lookup);
        CURRENT_REGISTRATION.set(registration);
        return registration;
    }

    /**
     * Looks up the name under which this proxy registered a server.
     *
     * @param serverId the unique server ID
     * @return the proxy name, or an empty Optional if nothing is registered or the lookup doesn't know
     * the server
     * @throws NullPointerException if the registered lookup returns {@code null}
     */
    public static Optional<String> proxyNameOf(String serverId) {
        Registration registration = CURRENT_REGISTRATION.get();
        if (registration == null) {
            return Optional.empty();
        }
        Optional<String> proxyName = registration.lookup.proxyNameOf(serverId);
        return Objects.requireNonNull(proxyName, "The registered proxy server name lookup returned null");
    }

    /**
     * Finds the name under which this proxy registered a server.
     */
    @FunctionalInterface
    public interface Lookup {

        /**
         * @param serverId the unique server ID
         * @return the proxy name, or an empty Optional if the server is not registered on this proxy
         */
        Optional<String> proxyNameOf(String serverId);
    }

    /**
     * A lookup registered with {@link #register(Lookup)}.
     */
    public static final class Registration implements AutoCloseable {

        private final Lookup lookup;

        private Registration(Lookup lookup) {
            this.lookup = lookup;
        }

        @Override
        public void close() {
            CURRENT_REGISTRATION.compareAndSet(this, null);
        }
    }
}
