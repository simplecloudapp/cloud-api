package app.simplecloud.api.internal.integration.adventure;

import app.simplecloud.api.CloudApi;
import app.simplecloud.api.internal.CloudApiImpl;
import build.buf.gen.simplecloud.adventure.v1.*;
import io.nats.client.Connection;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.inventory.Book;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.sound.SoundStop;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.title.TitlePart;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An Adventure Audience implementation that sends all actions via NATS
 * to be handled by the target (player, server, or group).
 */
public class RemoteAudience implements Audience {

    private final Connection natsConnection;
    private final String subjectPrefix;
    private final Map<String, BossBar> activeBossBars = new ConcurrentHashMap<>();

    private RemoteAudience(Connection natsConnection, String subjectPrefix) {
        this.natsConnection = natsConnection;
        this.subjectPrefix = subjectPrefix;
    }

    public static Builder builder(Connection natsConnection, String networkId) {
        return new Builder(natsConnection, networkId);
    }

    public static Builder builder(CloudApi cloudApi) {
        if (!(cloudApi instanceof CloudApiImpl)) {
            throw new IllegalArgumentException("CloudApi must be an instance of CloudApiImpl");
        }
        CloudApiImpl impl = (CloudApiImpl) cloudApi;
        return new Builder(impl.getNatsConnection(), cloudApi.getNetworkId());
    }

    public static class Builder {
        private final Connection natsConnection;
        private final String networkId;

        private Builder(Connection natsConnection, String networkId) {
            this.natsConnection = natsConnection;
            this.networkId = networkId;
        }

        public RemoteAudience forPlayer(UUID playerUUID) {
            String subjectPrefix = networkId + ".adventure.player." + playerUUID + ".";
            return new RemoteAudience(natsConnection, subjectPrefix);
        }

        public RemoteAudience forServer(String serverId) {
            String subjectPrefix = networkId + ".adventure.server." + serverId + ".";
            return new RemoteAudience(natsConnection, subjectPrefix);
        }

        public RemoteAudience forGroup(String groupName) {
            String subjectPrefix = networkId + ".adventure.group." + groupName + ".";
            return new RemoteAudience(natsConnection, subjectPrefix);
        }
    }

    private void publish(String action, byte[] data) {
        natsConnection.publish(subjectPrefix + action, data);
    }

    private AdventureComponent serialize(Component component) {
        return AdventureComponent.newBuilder()
                .setJson(GsonComponentSerializer.gson().serialize(component))
                .build();
    }

    @Override
    public void sendMessage(Component message) {
        SendMessageRequest request = SendMessageRequest.newBuilder()
                .setMessage(serialize(message))
                .build();
        publish("message", request.toByteArray());
    }

    @Override
    public void sendActionBar(Component message) {
        SendActionbarRequest request = SendActionbarRequest.newBuilder()
                .setMessage(serialize(message))
                .build();
        publish("actionbar", request.toByteArray());
    }

    @Override
    public void showTitle(Title title) {
        Title.Times times = title.times();
        if (times != null) {
            sendTitlePart(TitlePart.TIMES, times);
        }
        sendTitlePart(TitlePart.SUBTITLE, title.subtitle());
        sendTitlePart(TitlePart.TITLE, title.title());
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> void sendTitlePart(TitlePart<T> part, T value) {
        if (part == TitlePart.TITLE) {
            SendTitleRequest request = SendTitleRequest.newBuilder()
                    .setTitle(serialize((Component) value))
                    .build();
            publish("title", request.toByteArray());
        } else if (part == TitlePart.SUBTITLE) {
            SendSubtitleRequest request = SendSubtitleRequest.newBuilder()
                    .setSubtitle(serialize((Component) value))
                    .build();
            publish("subtitle", request.toByteArray());
        } else if (part == TitlePart.TIMES) {
            Title.Times times = (Title.Times) value;
            TitleTimes protoTimes = TitleTimes.newBuilder()
                    .setFadeInMs(times.fadeIn().toMillis())
                    .setStayMs(times.stay().toMillis())
                    .setFadeOutMs(times.fadeOut().toMillis())
                    .build();
            SendTitleTimesRequest request = SendTitleTimesRequest.newBuilder()
                    .setTimes(protoTimes)
                    .build();
            publish("title-times", request.toByteArray());
        }
    }

    @Override
    public void clearTitle() {
        publish("clear-title", new byte[0]);
    }

    @Override
    public void resetTitle() {
        publish("reset-title", new byte[0]);
    }

    @Override
    public void playSound(Sound sound) {
        SendPlaySoundRequest request = SendPlaySoundRequest.newBuilder()
                .setSound(convertSound(sound))
                .build();
        publish("sound", request.toByteArray());
    }

    @Override
    public void playSound(Sound sound, double x, double y, double z) {
        SendPlaySoundAtCoordinatesRequest request = SendPlaySoundAtCoordinatesRequest.newBuilder()
                .setSound(convertSound(sound))
                .setCoordinates(SoundCoordinates.newBuilder()
                        .setX(x)
                        .setY(y)
                        .setZ(z)
                        .build())
                .build();
        publish("sound-at", request.toByteArray());
    }

    @Override
    public void stopSound(SoundStop stop) {
        SendStopSoundRequest.Builder builder = SendStopSoundRequest.newBuilder();

        if (stop.sound() != null) {
            builder.setSound(stop.sound().asString());
        }
        if (stop.source() != null) {
            builder.setSource(convertSoundSource(stop.source()));
        }

        publish("stop-sound", builder.build().toByteArray());
    }

    @Override
    public void showBossBar(BossBar bar) {
        String id = UUID.randomUUID().toString();
        activeBossBars.put(id, bar);

        AdventureBossBar protoBossBar = AdventureBossBar.newBuilder()
                .setId(id)
                .setTitle(serialize(bar.name()))
                .setProgress(bar.progress())
                .setColor(convertBossBarColor(bar.color()))
                .setOverlay(convertBossBarOverlay(bar.overlay()))
                .build();

        SendBossBarRequest request = SendBossBarRequest.newBuilder()
                .setBossBar(protoBossBar)
                .build();
        publish("bossbar", request.toByteArray());
    }

    @Override
    public void hideBossBar(BossBar bar) {
        String id = null;
        for (Map.Entry<String, BossBar> entry : activeBossBars.entrySet()) {
            if (entry.getValue() == bar) {
                id = entry.getKey();
                break;
            }
        }

        if (id != null) {
            activeBossBars.remove(id);
            SendBossBarRemoveRequest request = SendBossBarRemoveRequest.newBuilder()
                    .setBossBarId(id)
                    .build();
            publish("bossbar-remove", request.toByteArray());
        }
    }

    @Override
    public void sendPlayerListHeaderAndFooter(Component header, Component footer) {
        SendPlayerListHeaderAndFooterRequest request = SendPlayerListHeaderAndFooterRequest.newBuilder()
                .setHeader(serialize(header))
                .setFooter(serialize(footer))
                .build();
        publish("player-list", request.toByteArray());
    }

    @Override
    public void openBook(Book book) {
        AdventureBook.Builder bookBuilder = AdventureBook.newBuilder()
                .setTitle(serialize(book.title()))
                .setAuthor(serialize(book.author()));

        for (Component page : book.pages()) {
            bookBuilder.addPages(serialize(page));
        }

        SendOpenBookRequest request = SendOpenBookRequest.newBuilder()
                .setBook(bookBuilder.build())
                .build();
        publish("open-book", request.toByteArray());
    }

    private AdventureSound convertSound(Sound sound) {
        return AdventureSound.newBuilder()
                .setSound(sound.name().asString())
                .setSource(convertSoundSource(sound.source()))
                .setVolume(sound.volume())
                .setPitch(sound.pitch())
                .build();
    }

    private SoundSource convertSoundSource(Sound.Source source) {
        switch (source) {
            case MUSIC:
                return SoundSource.SOUND_SOURCE_MUSIC;
            case RECORD:
                return SoundSource.SOUND_SOURCE_RECORD;
            case WEATHER:
                return SoundSource.SOUND_SOURCE_WEATHER;
            case BLOCK:
                return SoundSource.SOUND_SOURCE_BLOCK;
            case HOSTILE:
                return SoundSource.SOUND_SOURCE_HOSTILE;
            case NEUTRAL:
                return SoundSource.SOUND_SOURCE_NEUTRAL;
            case PLAYER:
                return SoundSource.SOUND_SOURCE_PLAYER;
            case AMBIENT:
                return SoundSource.SOUND_SOURCE_AMBIENT;
            case VOICE:
                return SoundSource.SOUND_SOURCE_VOICE;
            default:
                return SoundSource.SOUND_SOURCE_MASTER;
        }
    }

    private BossBarColor convertBossBarColor(BossBar.Color color) {
        switch (color) {
            case BLUE:
                return BossBarColor.BOSS_BAR_COLOR_BLUE;
            case RED:
                return BossBarColor.BOSS_BAR_COLOR_RED;
            case GREEN:
                return BossBarColor.BOSS_BAR_COLOR_GREEN;
            case YELLOW:
                return BossBarColor.BOSS_BAR_COLOR_YELLOW;
            case PURPLE:
                return BossBarColor.BOSS_BAR_COLOR_PURPLE;
            case WHITE:
                return BossBarColor.BOSS_BAR_COLOR_WHITE;
            default:
                return BossBarColor.BOSS_BAR_COLOR_PINK;
        }
    }

    private BossBarOverlay convertBossBarOverlay(BossBar.Overlay overlay) {
        switch (overlay) {
            case NOTCHED_6:
                return BossBarOverlay.BOSS_BAR_OVERLAY_NOTCHED_6;
            case NOTCHED_10:
                return BossBarOverlay.BOSS_BAR_OVERLAY_NOTCHED_10;
            case NOTCHED_12:
                return BossBarOverlay.BOSS_BAR_OVERLAY_NOTCHED_12;
            case NOTCHED_20:
                return BossBarOverlay.BOSS_BAR_OVERLAY_NOTCHED_20;
            default:
                return BossBarOverlay.BOSS_BAR_OVERLAY_PROGRESS;
        }
    }
}
