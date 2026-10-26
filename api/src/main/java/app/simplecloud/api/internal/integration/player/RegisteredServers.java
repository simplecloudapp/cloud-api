package app.simplecloud.api.internal.integration.player;

import app.simplecloud.api.proxy.ProxyServerNames;

import java.util.Optional;

/**
 * The servers registered on this proxy, as a connect request sees them.
 *
 * @param <T> the proxy platform's server type
 */
public interface RegisteredServers<T> {

    /**
     * Returns the server this proxy registered under the given name.
     */
    Optional<T> findByName(String name);

    /**
     * Returns the server with the given cloud server ID, if this proxy registered it.
     * The ID is mapped to its proxy name through {@link ProxyServerNames}.
     */
    default Optional<T> findById(String serverId) {
        Optional<String> proxyName = ProxyServerNames.proxyNameOf(serverId);
        if (proxyName.isEmpty()) {
            return Optional.empty();
        }
        return findByName(proxyName.get());
    }
}
