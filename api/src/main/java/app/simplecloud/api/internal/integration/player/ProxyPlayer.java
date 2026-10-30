package app.simplecloud.api.internal.integration.player;

import app.simplecloud.api.player.CloudPlayer;
import net.kyori.adventure.text.Component;

import java.util.concurrent.CompletableFuture;

/**
 * A player connected to this proxy, as kick and connect requests act on them.
 */
public interface ProxyPlayer {

    /**
     * Disconnects the player from this proxy.
     */
    void kick(Component reason);

    /**
     * Connects the player to the server this proxy registered under the given name, for a connect request
     * from the controller or {@code connectDirect}. Answers {@code SERVER_NOT_FOUND} if no server has that name.
     */
    CompletableFuture<CloudPlayer.ConnectResult> connect(String proxyServerName);
}
