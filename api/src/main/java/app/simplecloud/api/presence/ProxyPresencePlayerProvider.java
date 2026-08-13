package app.simplecloud.api.presence;

import java.util.Collection;

/**
 * Supplies the current set of players visible to this proxy or game server.
 */
public interface ProxyPresencePlayerProvider {

    Collection<ProxyPresencePlayer> getProxyPresencePlayers();
}
