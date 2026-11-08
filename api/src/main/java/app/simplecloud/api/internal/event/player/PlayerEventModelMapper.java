package app.simplecloud.api.internal.event.player;

import app.simplecloud.api.internal.player.CloudPlayerImpl;
import app.simplecloud.api.internal.util.ProtoConversionUtil;
import app.simplecloud.api.player.CloudPlayer;
import app.simplecloud.api.player.PlayerApi;
import app.simplecloud.api.player.PlayerClientSettings;
import app.simplecloud.api.player.PlayerProfile;
import build.buf.gen.simplecloud.player.v2.PlayerConfig;
import io.nats.client.Connection;

import java.util.UUID;

final class PlayerEventModelMapper {

    private PlayerEventModelMapper() {}

    static CloudPlayer mapPlayer(
            PlayerApi playerApi, Connection natsConnection, String networkId, PlayerConfig config, boolean online) {
        return new CloudPlayerImpl(
                playerApi,
                natsConnection,
                networkId,
                UUID.fromString(config.getUniqueId()),
                config.getName(),
                ProtoConversionUtil.emptyToNull(config.getDisplayName()),
                ProtoConversionUtil.emptyToNull(config.getConnectedProxyName()),
                ProtoConversionUtil.emptyToNull(config.getConnectedServerName()),
                config.hasConnection() ? config.getConnection().getOnline() : online,
                config.getOnlineTimeSeconds(),
                ProtoConversionUtil.emptyToNull(config.getSessionId()),
                ProtoConversionUtil.optionalEpochMillisToIso(config.getFirstSeen()),
                ProtoConversionUtil.optionalEpochMillisToIso(config.getLastSeen()),
                config.getPropertiesMap());
    }

    static PlayerProfile mapProfile(build.buf.gen.simplecloud.player.v2.PlayerProfile proto) {
        return new PlayerProfile(
                proto.getId(),
                UUID.fromString(proto.getPlayerId()),
                proto.getName(),
                ProtoConversionUtil.emptyToNull(proto.getTexture()),
                ProtoConversionUtil.optionalEpochMillisToIso(proto.getCreatedAt()));
    }

    static PlayerClientSettings mapClientSettings(build.buf.gen.simplecloud.player.v2.PlayerClientSettings proto) {
        PlayerClientSettings settings = new PlayerClientSettings();
        settings.setChatMode(
                PlayerClientSettings.ChatMode.parse(ProtoConversionUtil.convertChatModeToString(proto.getChatMode())));
        settings.setLocale(ProtoConversionUtil.emptyToNull(proto.getLocale()));
        settings.setMainHand(
                PlayerClientSettings.MainHand.parse(ProtoConversionUtil.convertMainHandToString(proto.getMainHand())));
        settings.setViewDistance(proto.getViewDistance());
        settings.setChatColors(proto.getChatColors());
        settings.setClientListingAllowed(proto.getClientListingAllowed());
        settings.setSkinParts(proto.getSkinPartsList());
        return settings;
    }
}
