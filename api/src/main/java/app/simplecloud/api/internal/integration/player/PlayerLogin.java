package app.simplecloud.api.internal.integration.player;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * What a proxy reports to the controller when a player logs in.
 *
 * @param connectedProxyName the name of the proxy the player connected to
 * @param addressHash        a hash of the player's address, so the controller never sees the address itself
 * @param clientLanguage     the client's locale, for example {@code en_US}
 * @param clientVersion      the client's protocol version
 * @param texture            the player's skin texture property, or {@code null} if the proxy doesn't know it
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
        @Nullable String texture
) {
}
