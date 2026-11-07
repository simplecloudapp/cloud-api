package app.simplecloud.api.platform.shared.player;

import java.util.UUID;

public interface OnlinePlayers {

    boolean isOnline(UUID playerId);
}
