package app.simplecloud.api.platform.bungeecord.player;

import app.simplecloud.api.platform.shared.player.ConnectingPlayer;
import app.simplecloud.api.platform.shared.player.ProxyPlayerSessions;
import app.simplecloud.api.player.PlayerClientSettings;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.md_5.bungee.api.SkinConfiguration;
import net.md_5.bungee.api.connection.PendingConnection;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.event.ServerSwitchEvent;
import net.md_5.bungee.api.event.SettingsChangedEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.event.EventPriority;

public class PlayerConnectionListener implements Listener {

    private final Plugin plugin;
    private final ProxyPlayerSessions sessions;

    public PlayerConnectionListener(Plugin plugin, ProxyPlayerSessions sessions) {
        this.plugin = plugin;
        this.sessions = sessions;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerJoin(PostLoginEvent event) {
        ProxiedPlayer player = event.getPlayer();
        CompletableFuture<Void> login = sessions.login(
                toConnectingPlayer(player), player, () -> player.getLocale() != null ? toClientSettings(player) : null);
        event.registerIntent(plugin);
        login.whenComplete((ignored, failure) -> event.completeIntent(plugin));
    }

    @EventHandler
    public void onPlayerQuit(PlayerDisconnectEvent event) {
        ProxiedPlayer player = event.getPlayer();
        sessions.disconnect(player.getUniqueId(), player.getName(), player);
    }

    @EventHandler
    public void onSettingsChanged(SettingsChangedEvent event) {
        ProxiedPlayer player = event.getPlayer();
        sessions.clientSettingsChanged(player.getUniqueId(), player.getName(), toClientSettings(player));
    }

    @EventHandler
    public void onServerSwitch(ServerSwitchEvent event) {
        ProxiedPlayer player = event.getPlayer();
        if (player.getServer() != null) {
            sessions.serverSwitched(
                    player.getUniqueId(),
                    player.getName(),
                    player.getServer().getInfo().getName());
        }
    }

    static ConnectingPlayer toConnectingPlayer(ProxiedPlayer player) {
        PendingConnection connection = player.getPendingConnection();
        return new ConnectingPlayer(
                player.getUniqueId(),
                player.getName(),
                player.getDisplayName(),
                connection.getSocketAddress(),
                player.getLocale(),
                connection.getVersion(),
                connection.isOnlineMode(),
                null);
    }

    static PlayerClientSettings toClientSettings(ProxiedPlayer player) {
        return new PlayerClientSettings(
                toChatMode(player.getChatMode()),
                player.getLocale() != null ? player.getLocale().toString() : null,
                toMainHand(player.getMainHand()),
                player.getViewDistance(),
                player.hasChatColors(),
                // Bungee does not expose the client listing flag.
                true,
                toSkinParts(player.getSkinParts()));
    }

    private static PlayerClientSettings.ChatMode toChatMode(ProxiedPlayer.ChatMode chatMode) {
        if (chatMode == null) {
            return PlayerClientSettings.ChatMode.UNKNOWN;
        }
        return switch (chatMode) {
            case SHOWN -> PlayerClientSettings.ChatMode.ENABLED;
            case COMMANDS_ONLY -> PlayerClientSettings.ChatMode.COMMANDS_ONLY;
            case HIDDEN -> PlayerClientSettings.ChatMode.HIDDEN;
        };
    }

    private static PlayerClientSettings.MainHand toMainHand(ProxiedPlayer.MainHand mainHand) {
        if (mainHand == null) {
            return PlayerClientSettings.MainHand.UNKNOWN;
        }
        return mainHand == ProxiedPlayer.MainHand.LEFT
                ? PlayerClientSettings.MainHand.LEFT
                : PlayerClientSettings.MainHand.RIGHT;
    }

    private static List<String> toSkinParts(SkinConfiguration skinParts) {
        List<String> result = new ArrayList<>();
        if (skinParts == null) {
            return result;
        }
        if (skinParts.hasCape()) result.add("CAPE");
        if (skinParts.hasJacket()) result.add("JACKET");
        if (skinParts.hasLeftSleeve()) result.add("LEFT_SLEEVE");
        if (skinParts.hasRightSleeve()) result.add("RIGHT_SLEEVE");
        if (skinParts.hasLeftPants()) result.add("LEFT_PANTS");
        if (skinParts.hasRightPants()) result.add("RIGHT_PANTS");
        if (skinParts.hasHat()) result.add("HAT");
        return result;
    }
}
