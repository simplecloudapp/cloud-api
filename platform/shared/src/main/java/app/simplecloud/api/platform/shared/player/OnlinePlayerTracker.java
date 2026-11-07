package app.simplecloud.api.platform.shared.player;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class OnlinePlayerTracker implements OnlinePlayerCount {

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

    @Override
    public int count() {
        return onlinePlayers.size();
    }
}
