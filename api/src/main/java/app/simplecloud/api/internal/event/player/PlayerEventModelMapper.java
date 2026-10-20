package app.simplecloud.api.internal.event.player;

import app.simplecloud.api.internal.ProtoConversionUtil;
import app.simplecloud.api.internal.player.CloudPlayerImpl;
import app.simplecloud.api.player.CloudPlayer;
import app.simplecloud.api.player.PlayerApi;
import app.simplecloud.api.player.PlayerClientSettings;
import app.simplecloud.api.player.PlayerProfile;
import build.buf.gen.simplecloud.player.v2.PlayerConfig;
import io.nats.client.Connection;

import java.time.Instant;
import java.util.UUID;

final class PlayerEventModelMapper {

    private PlayerEventModelMapper() {
    }

    static CloudPlayer mapPlayer(
            PlayerApi playerApi,
            Connection natsConnection,
            String networkId,
            PlayerConfig config,
            boolean online
    ) {
        return new CloudPlayerImpl(
                playerApi,
                natsConnection,
                networkId,
                UUID.fromString(config.getUniqueId()),
                config.getName(),
                emptyToNull(config.getDisplayName()),
                emptyToNull(config.getConnectedProxyName()),
                emptyToNull(config.getConnectedServerName()),
                config.hasConnection() ? config.getConnection().getOnline() : online,
                config.getOnlineTimeSeconds(),
                emptyToNull(config.getSessionId()),
                toIsoTimestamp(config.getFirstSeen()),
                toIsoTimestamp(config.getLastSeen()),
                config.getPropertiesMap()
        );
    }

    static PlayerProfile mapProfile(build.buf.gen.simplecloud.player.v2.PlayerProfile proto) {
        PlayerProfile profile = new PlayerProfile();
        profile.setId(proto.getId());
        profile.setPlayerId(UUID.fromString(proto.getPlayerId()));
        profile.setName(proto.getName());
        profile.setTexture(emptyToNull(proto.getTexture()));
        profile.setCreatedAt(toIsoTimestamp(proto.getCreatedAt()));
        return profile;
    }

    static PlayerClientSettings mapClientSettings(build.buf.gen.simplecloud.player.v2.PlayerClientSettings proto) {
        PlayerClientSettings settings = new PlayerClientSettings();
        settings.setChatMode(PlayerClientSettings.ChatMode.parse(ProtoConversionUtil.convertChatModeToString(proto.getChatMode())));
        settings.setLocale(emptyToNull(proto.getLocale()));
        settings.setMainHand(PlayerClientSettings.MainHand.parse(ProtoConversionUtil.convertMainHandToString(proto.getMainHand())));
        settings.setViewDistance(proto.getViewDistance());
        settings.setChatColors(proto.getChatColors());
        settings.setClientListingAllowed(proto.getClientListingAllowed());
        settings.setSkinParts(proto.getSkinPartsList());
        return settings;
    }

    static String toIsoTimestamp(long epochMillis) {
        return epochMillis > 0 ? Instant.ofEpochMilli(epochMillis).toString() : null;
    }

    static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }
}
