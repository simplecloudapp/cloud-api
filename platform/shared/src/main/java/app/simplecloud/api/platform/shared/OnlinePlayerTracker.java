package app.simplecloud.api.platform.shared;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe set of online players, maintained from join/quit events.
 * Unlike reading the platform's player list, the count is already correct while the quit event runs
 * and can be read from any thread.
 */
public class OnlinePlayerTracker {

    private final Set<UUID> onlinePlayers = ConcurrentHashMap.newKeySet();

    public void reset(Collection<UUID> playerIds) {
        onlinePlayers.clear();
        onlinePlayers.addAll(playerIds);
    }

    public void add(UUID playerId) {
        onlinePlayers.add(playerId);
    }

    public void remove(UUID playerId) {
        onlinePlayers.remove(playerId);
    }

    public long count() {
        return onlinePlayers.size();
    }
}
