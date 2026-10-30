package app.simplecloud.api.internal.integration.adventure;

import app.simplecloud.api.internal.CloudApiImpl;
import build.buf.gen.simplecloud.adventure.v1.*;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.Message;
import io.nats.client.MessageHandler;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.title.TitlePart;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Adventure integration for server plugins.
 * Handles adventure actions (messages, titles, sounds, boss bars) from remote sources.
 */
public class AdventureIntegration {

    private static final List<String> ACTIONS = List.of(
            "message",
            "actionbar",
            "title",
            "subtitle",
            "title-times",
            "clear-title",
            "reset-title",
            "sound",
            "sound-at",
            "stop-sound",
            "bossbar",
            "bossbar-remove",
            "player-list",
            "open-book"
    );

    private final Connection natsConnection;
    private final String networkId;
    private final PlayerAudiences playerAudiences;
    private final String serverId;
    private final String groupName;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Map<String, BossBar> activeBossBars = new ConcurrentHashMap<>();

    private Dispatcher dispatcher;

    /**
     * Creates an integration for actions sent to a player on this server, to this server, or to its group.
     */
    public AdventureIntegration(CloudApiImpl cloudApi, PlayerAudiences playerAudiences, String serverId, String groupName) {
        this(cloudApi.getNatsConnection(), cloudApi.getNetworkId(), playerAudiences, serverId, groupName);
    }

    AdventureIntegration(Connection natsConnection, String networkId, PlayerAudiences playerAudiences,
                         String serverId, String groupName) {
        this.natsConnection = natsConnection;
        this.networkId = networkId;
        this.playerAudiences = playerAudiences;
        this.serverId = serverId;
        this.groupName = groupName;
    }

    /**
     * Starts listening for adventure actions.
     */
    public void start() {
        if (!running.compareAndSet(false, true)) return;

        dispatcher = natsConnection.createDispatcher(null);

        String playerPrefix = networkId + ".adventure.player.*.";
        subscribeActions(playerPrefix, this::handlePlayerMessage);

        String serverPrefix = networkId + ".adventure.server." + serverId + ".";
        subscribeActions(serverPrefix, this::handleBroadcastMessage);

        String groupPrefix = networkId + ".adventure.group." + groupName + ".";
        subscribeActions(groupPrefix, this::handleBroadcastMessage);
    }

    private void subscribeActions(String prefix, MessageHandler handler) {
        for (String action : ACTIONS) {
            dispatcher.subscribe(prefix + action, handler);
        }
    }

    /**
     * Stops listening for adventure actions.
     */
    public void stop() {
        if (!running.compareAndSet(true, false)) return;

        if (dispatcher != null) {
            try {
                dispatcher.drain(Duration.ofSeconds(1));
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void handlePlayerMessage(Message msg) {
        Optional<Audience> player = findAddressedPlayer(msg.getSubject());
        // Every server receives player actions; only the server holding the player runs them.
        if (player.isEmpty()) return;

        String action = extractAction(msg.getSubject());
        executeAction(action, msg, player.get());
    }

    private void handleBroadcastMessage(Message msg) {
        String action = extractAction(msg.getSubject());
        executeAction(action, msg, playerAudiences.allPlayers());
    }

    private Optional<Audience> findAddressedPlayer(String subject) {
        // Subject pattern: {networkId}.adventure.player.{uuid}.{action}
        String[] parts = subject.split("\\.");
        String playerToken = parts[parts.length - 2];
        try {
            UUID playerId = UUID.fromString(playerToken);
            return playerAudiences.findPlayer(playerId);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private String extractAction(String subject) {
        String[] parts = subject.split("\\.");
        return parts.length > 0 ? parts[parts.length - 1] : "";
    }

    private void executeAction(String action, Message msg, Audience audience) {
        try {
            switch (action) {
                case "message":
                    SendMessageRequest msgReq = SendMessageRequest.parseFrom(msg.getData());
                    audience.sendMessage(deserialize(msgReq.getMessage()));
                    break;
                case "actionbar":
                    SendActionbarRequest abReq = SendActionbarRequest.parseFrom(msg.getData());
                    audience.sendActionBar(deserialize(abReq.getMessage()));
                    break;
                case "title":
                    SendTitleRequest titleReq = SendTitleRequest.parseFrom(msg.getData());
                    audience.sendTitlePart(TitlePart.TITLE, deserialize(titleReq.getTitle()));
                    break;
                case "subtitle":
                    SendSubtitleRequest subReq = SendSubtitleRequest.parseFrom(msg.getData());
                    audience.sendTitlePart(TitlePart.SUBTITLE, deserialize(subReq.getSubtitle()));
                    break;
                case "title-times":
                    SendTitleTimesRequest timesReq = SendTitleTimesRequest.parseFrom(msg.getData());
                    TitleTimes times = timesReq.getTimes();
                    Title.Times titleTimes = Title.Times.times(
                            Duration.ofMillis(times.getFadeInMs()),
                            Duration.ofMillis(times.getStayMs()),
                            Duration.ofMillis(times.getFadeOutMs())
                    );
                    audience.sendTitlePart(TitlePart.TIMES, titleTimes);
                    break;
                case "clear-title":
                    audience.clearTitle();
                    break;
                case "reset-title":
                    audience.resetTitle();
                    break;
                case "sound":
                    SendPlaySoundRequest soundReq = SendPlaySoundRequest.parseFrom(msg.getData());
                    audience.playSound(convertSound(soundReq.getSound()));
                    break;
                case "sound-at":
                    SendPlaySoundAtCoordinatesRequest soundAtReq = SendPlaySoundAtCoordinatesRequest.parseFrom(msg.getData());
                    SoundCoordinates coords = soundAtReq.getCoordinates();
                    audience.playSound(convertSound(soundAtReq.getSound()), coords.getX(), coords.getY(), coords.getZ());
                    break;
                case "stop-sound":
                    SendStopSoundRequest stopReq = SendStopSoundRequest.parseFrom(msg.getData());
                    if (stopReq.hasSound()) {
                        audience.stopSound(net.kyori.adventure.sound.SoundStop.named(Key.key(stopReq.getSound())));
                    } else if (stopReq.hasSource()) {
                        audience.stopSound(net.kyori.adventure.sound.SoundStop.source(convertSoundSource(stopReq.getSource())));
                    } else {
                        audience.stopSound(net.kyori.adventure.sound.SoundStop.all());
                    }
                    break;
                case "bossbar":
                    SendBossBarRequest bbReq = SendBossBarRequest.parseFrom(msg.getData());
                    handleBossBar(audience, bbReq);
                    break;
                case "bossbar-remove":
                    SendBossBarRemoveRequest bbRemReq = SendBossBarRemoveRequest.parseFrom(msg.getData());
                    handleBossBarRemove(audience, bbRemReq);
                    break;
                case "player-list":
                    SendPlayerListHeaderAndFooterRequest plReq = SendPlayerListHeaderAndFooterRequest.parseFrom(msg.getData());
                    audience.sendPlayerListHeaderAndFooter(deserialize(plReq.getHeader()), deserialize(plReq.getFooter()));
                    break;
                case "open-book":
                    SendOpenBookRequest bookReq = SendOpenBookRequest.parseFrom(msg.getData());
                    handleOpenBook(audience, bookReq);
                    break;
            }
        } catch (Exception ignored) {
        }
    }

    private void handleBossBar(Audience audience, SendBossBarRequest request) {
        AdventureBossBar config = request.getBossBar();

        BossBar bossBar = BossBar.bossBar(
                deserialize(config.getTitle()),
                config.getProgress(),
                convertBossBarColor(config.getColor()),
                convertBossBarOverlay(config.getOverlay())
        );

        BossBar oldBar = activeBossBars.put(config.getId(), bossBar);
        if (oldBar != null) {
            audience.hideBossBar(oldBar);
        }

        audience.showBossBar(bossBar);
    }

    private void handleBossBarRemove(Audience audience, SendBossBarRemoveRequest request) {
        BossBar bossBar = activeBossBars.remove(request.getBossBarId());
        if (bossBar != null) {
            audience.hideBossBar(bossBar);
        }
    }

    private void handleOpenBook(Audience audience, SendOpenBookRequest request) {
        AdventureBook bookConfig = request.getBook();

        net.kyori.adventure.inventory.Book.Builder bookBuilder = net.kyori.adventure.inventory.Book.builder()
                .title(deserialize(bookConfig.getTitle()))
                .author(deserialize(bookConfig.getAuthor()));

        for (AdventureComponent page : bookConfig.getPagesList()) {
            bookBuilder.addPage(deserialize(page));
        }

        audience.openBook(bookBuilder.build());
    }

    private Component deserialize(AdventureComponent component) {
        return GsonComponentSerializer.gson().deserialize(component.getJson());
    }

    private Sound convertSound(AdventureSound protoSound) {
        return Sound.sound(
                Key.key(protoSound.getSound()),
                convertSoundSource(protoSound.getSource()),
                protoSound.getVolume(),
                protoSound.getPitch()
        );
    }

    private Sound.Source convertSoundSource(SoundSource source) {
        return switch (source) {
            case SOUND_SOURCE_MUSIC -> Sound.Source.MUSIC;
            case SOUND_SOURCE_RECORD -> Sound.Source.RECORD;
            case SOUND_SOURCE_WEATHER -> Sound.Source.WEATHER;
            case SOUND_SOURCE_BLOCK -> Sound.Source.BLOCK;
            case SOUND_SOURCE_HOSTILE -> Sound.Source.HOSTILE;
            case SOUND_SOURCE_NEUTRAL -> Sound.Source.NEUTRAL;
            case SOUND_SOURCE_PLAYER -> Sound.Source.PLAYER;
            case SOUND_SOURCE_AMBIENT -> Sound.Source.AMBIENT;
            case SOUND_SOURCE_VOICE -> Sound.Source.VOICE;
            default -> Sound.Source.MASTER;
        };
    }

    private BossBar.Color convertBossBarColor(BossBarColor color) {
        return switch (color) {
            case BOSS_BAR_COLOR_BLUE -> BossBar.Color.BLUE;
            case BOSS_BAR_COLOR_RED -> BossBar.Color.RED;
            case BOSS_BAR_COLOR_GREEN -> BossBar.Color.GREEN;
            case BOSS_BAR_COLOR_YELLOW -> BossBar.Color.YELLOW;
            case BOSS_BAR_COLOR_PURPLE -> BossBar.Color.PURPLE;
            case BOSS_BAR_COLOR_WHITE -> BossBar.Color.WHITE;
            default -> BossBar.Color.PINK;
        };
    }

    private BossBar.Overlay convertBossBarOverlay(BossBarOverlay overlay) {
        return switch (overlay) {
            case BOSS_BAR_OVERLAY_NOTCHED_6 -> BossBar.Overlay.NOTCHED_6;
            case BOSS_BAR_OVERLAY_NOTCHED_10 -> BossBar.Overlay.NOTCHED_10;
            case BOSS_BAR_OVERLAY_NOTCHED_12 -> BossBar.Overlay.NOTCHED_12;
            case BOSS_BAR_OVERLAY_NOTCHED_20 -> BossBar.Overlay.NOTCHED_20;
            default -> BossBar.Overlay.PROGRESS;
        };
    }
}
