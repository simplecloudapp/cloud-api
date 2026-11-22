package app.simplecloud.api.internal.integration.player;

import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * What a proxy reports to the controller when a player logs in.
 */
public record PlayerLogin(
        UUID playerId,
        String name,
        String displayName,
        String connectedProxyName,
        String addressHash,
        String clientLanguage,
        int clientVersion,
        boolean onlineMode,
        @Nullable String texture) {}
