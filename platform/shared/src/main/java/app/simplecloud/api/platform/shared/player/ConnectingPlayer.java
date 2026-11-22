package app.simplecloud.api.platform.shared.player;

import java.net.SocketAddress;
import java.util.Locale;
import java.util.UUID;

/**
 * A player logging in to this proxy, as read from the platform's login event.
 */
public record ConnectingPlayer(
        UUID id,
        String name,
        String displayName,
        SocketAddress address,
        Locale locale,
        int protocolVersion,
        boolean onlineMode,
        String texture) {}
