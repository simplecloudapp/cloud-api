package app.simplecloud.api.platform.velocity;

import app.simplecloud.api.platform.shared.player.ConnectingPlayer;
import app.simplecloud.api.platform.shared.player.ProxyPlayerSessions;
import app.simplecloud.api.player.PlayerClientSettings;
import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.player.PlayerSettingsChangedEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.player.PlayerSettings;
import com.velocitypowered.api.proxy.player.SkinParts;
import com.velocitypowered.api.util.GameProfile;

import java.util.ArrayList;
import java.util.List;

public class PlayerConnectionListener {

    private final ProxyPlayerSessions sessions;

    public PlayerConnectionListener(ProxyPlayerSessions sessions) {
        this.sessions = sessions;
    }

    @Subscribe(priority = 100)
    public EventTask onPlayerJoin(PostLoginEvent event) {
        Player player = event.getPlayer();
        return EventTask.resumeWhenComplete(sessions.login(
                toConnectingPlayer(player),
                () -> player.hasSentPlayerSettings() ? toClientSettings(player.getPlayerSettings()) : null
        ));
    }

    @Subscribe
    public void onPlayerQuit(DisconnectEvent event) {
        Player player = event.getPlayer();
        sessions.disconnect(player.getUniqueId(), player.getUsername());
    }

    @Subscribe
    public void onPlayerSettingsChanged(PlayerSettingsChangedEvent event) {
        Player player = event.getPlayer();
        sessions.clientSettingsChanged(player.getUniqueId(), player.getUsername(), toClientSettings(event.getPlayerSettings()));
    }

    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        Player player = event.getPlayer();
        sessions.serverSwitched(player.getUniqueId(), player.getUsername(), event.getServer().getServerInfo().getName());
    }

    static ConnectingPlayer toConnectingPlayer(Player player) {
        return new ConnectingPlayer(
                player.getUniqueId(),
                player.getUsername(),
                player.getUsername(),
                player.getRemoteAddress(),
                player.getEffectiveLocale(),
                player.getProtocolVersion().getProtocol(),
                player.isOnlineMode(),
                texture(player.getGameProfile())
        );
    }

    private static String texture(GameProfile profile) {
        return profile.getProperties().stream()
                .filter(property -> property.getName().equals("textures"))
                .map(GameProfile.Property::getValue)
                .findFirst()
                .orElse(null);
    }

    static PlayerClientSettings toClientSettings(PlayerSettings settings) {
        PlayerClientSettings result = new PlayerClientSettings();
        result.setChatMode(switch (settings.getChatMode()) {
            case SHOWN -> PlayerClientSettings.ChatMode.ENABLED;
            case COMMANDS_ONLY -> PlayerClientSettings.ChatMode.COMMANDS_ONLY;
            case HIDDEN -> PlayerClientSettings.ChatMode.HIDDEN;
        });
        result.setLocale(settings.getLocale() != null ? settings.getLocale().toString() : null);
        result.setMainHand(settings.getMainHand() == PlayerSettings.MainHand.LEFT
                ? PlayerClientSettings.MainHand.LEFT
                : PlayerClientSettings.MainHand.RIGHT);
        result.setViewDistance(settings.getViewDistance());
        result.setChatColors(settings.hasChatColors());
        result.setClientListingAllowed(settings.isClientListingAllowed());
        result.setSkinParts(toSkinParts(settings.getSkinParts()));
        return result;
    }

    private static List<String> toSkinParts(SkinParts skinParts) {
        List<String> result = new ArrayList<>();
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
