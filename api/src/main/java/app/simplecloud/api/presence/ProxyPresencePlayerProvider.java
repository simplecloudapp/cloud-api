package app.simplecloud.api.presence;

import java.util.Collection;

/**
 * Supplies the current set of players connected through a proxy.
 *
 * <p>The controller compares its player list with this one and corrects its own list if they differ.
 */
public interface ProxyPresencePlayerProvider {

    /**
     * Returns the players currently connected through this proxy.
     *
     * <p>Called on a background thread, so the implementation must be thread-safe. {@code null} entries are ignored.
     *
     * @return the connected players
     */
    Collection<ProxyPresencePlayer> getProxyPresencePlayers();
}
