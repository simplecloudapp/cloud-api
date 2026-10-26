package app.simplecloud.api.proxy;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Maps server IDs to the names under which this proxy registered the servers.
 *
 * <p>The proxy uses it to resolve connect requests that target a server by ID. It is fed by the server
 * connection plugin, which registers the servers on the proxy and knows which ID belongs to which name.
 * Only Velocity and BungeeCord proxies handle connect requests, so it has no effect on other platforms.
 * Without a registration, connects by server ID answer
 * {@link app.simplecloud.api.player.CloudPlayer.ConnectResult#SERVER_NOT_FOUND}.
 *
 * <p>The registry is process-wide rather than tied to a {@code CloudApi} instance, because the connection
 * plugin creates its own instance. Only one registration is active at a time.
 */
public final class ProxyServerNames {

    private static final AtomicReference<Registration> CURRENT_REGISTRATION = new AtomicReference<>();

    private ProxyServerNames() {
    }

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
     *
     * <p>It is called on the NATS dispatcher thread, concurrently with the code that registers servers,
     * so it must be thread-safe, fast and must not throw. If it throws or returns {@code null}, the proxy
     * answers {@link app.simplecloud.api.player.CloudPlayer.ConnectResult#CONNECTION_FAILED}.
     *
     * <p>Several servers can resolve to the same proxy name, and the last one registered replaces the
     * others on the proxy. The lookup must only answer for the server that currently holds the name,
     * otherwise a connect by ID lands on the wrong server.
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

        /**
         * Removes the lookup if this registration is still the current one. Does nothing if another
         * registration replaced it or it was already closed.
         */
        @Override
        public void close() {
            CURRENT_REGISTRATION.compareAndSet(this, null);
        }
    }
}
