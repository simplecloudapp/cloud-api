package app.simplecloud.api.presence;

import java.util.Collection;

/**
 * Supplies the current set of players connected through a proxy.
 */
public interface ProxyPresencePlayerProvider {

    Collection<ProxyPresencePlayer> getProxyPresencePlayers();
}
