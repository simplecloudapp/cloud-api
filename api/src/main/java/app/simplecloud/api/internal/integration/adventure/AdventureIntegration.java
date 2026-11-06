package app.simplecloud.api.internal.integration.adventure;

import app.simplecloud.api.internal.CloudApiImpl;
import app.simplecloud.api.internal.integration.adventure.PlayerAudiences.PlayerAction;
import build.buf.gen.simplecloud.adventure.v1.AdventureBook;
import build.buf.gen.simplecloud.adventure.v1.AdventureBossBar;
import build.buf.gen.simplecloud.adventure.v1.AdventureComponent;
import build.buf.gen.simplecloud.adventure.v1.AdventureSound;
import build.buf.gen.simplecloud.adventure.v1.BossBarColor;
import build.buf.gen.simplecloud.adventure.v1.BossBarOverlay;
import build.buf.gen.simplecloud.adventure.v1.SendActionbarRequest;
import build.buf.gen.simplecloud.adventure.v1.SendBossBarRemoveRequest;
import build.buf.gen.simplecloud.adventure.v1.SendBossBarRequest;
import build.buf.gen.simplecloud.adventure.v1.SendMessageRequest;
import build.buf.gen.simplecloud.adventure.v1.SendOpenBookRequest;
import build.buf.gen.simplecloud.adventure.v1.SendPlaySoundAtCoordinatesRequest;
import build.buf.gen.simplecloud.adventure.v1.SendPlaySoundRequest;
import build.buf.gen.simplecloud.adventure.v1.SendPlayerListHeaderAndFooterRequest;
import build.buf.gen.simplecloud.adventure.v1.SendStopSoundRequest;
import build.buf.gen.simplecloud.adventure.v1.SendSubtitleRequest;
import build.buf.gen.simplecloud.adventure.v1.SendTitleRequest;
import build.buf.gen.simplecloud.adventure.v1.SendTitleTimesRequest;
import build.buf.gen.simplecloud.adventure.v1.SoundCoordinates;
import build.buf.gen.simplecloud.adventure.v1.SoundSource;
import build.buf.gen.simplecloud.adventure.v1.TitleTimes;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.Message;
import io.nats.client.MessageHandler;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.inventory.Book;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.sound.SoundStop;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.title.TitlePart;

import java.time.Duration;
import java.util.List;
import java.util.Map;
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
    private final Map<String, Map<UUID, BossBar>> activeBossBars = new ConcurrentHashMap<>();

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
        activeBossBars.clear();
    }

    private void handlePlayerMessage(Message msg) {
        UUID playerId = extractPlayerId(msg.getSubject());
        if (playerId == null) return;

        PlayerAction action = prepareAction(extractAction(msg.getSubject()), msg);
        if (action == null) return;

        // Every server receives player actions; only the server holding the player runs them.
        playerAudiences.runForPlayer(playerId, action);
    }

    private void handleBroadcastMessage(Message msg) {
        PlayerAction action = prepareAction(extractAction(msg.getSubject()), msg);
        if (action == null) return;

        playerAudiences.runForAllPlayers(action);
    }

    private UUID extractPlayerId(String subject) {
        // Subject pattern: {networkId}.adventure.player.{uuid}.{action}
        String[] parts = subject.split("\\.");
        if (parts.length < 2) return null;
        try {
            return UUID.fromString(parts[parts.length - 2]);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String extractAction(String subject) {
        String[] parts = subject.split("\\.");
        return parts.length > 0 ? parts[parts.length - 1] : "";
    }

    private PlayerAction prepareAction(String action, Message msg) {
        try {
            return switch (action) {
                case "message" -> {
                    Component message = deserialize(SendMessageRequest.parseFrom(msg.getData()).getMessage());
                    yield (playerId, player) -> player.sendMessage(message);
                }
                case "actionbar" -> {
                    Component message = deserialize(SendActionbarRequest.parseFrom(msg.getData()).getMessage());
                    yield (playerId, player) -> player.sendActionBar(message);
                }
                case "title" -> {
                    Component title = deserialize(SendTitleRequest.parseFrom(msg.getData()).getTitle());
                    yield (playerId, player) -> player.sendTitlePart(TitlePart.TITLE, title);
                }
                case "subtitle" -> {
                    Component subtitle = deserialize(SendSubtitleRequest.parseFrom(msg.getData()).getSubtitle());
                    yield (playerId, player) -> player.sendTitlePart(TitlePart.SUBTITLE, subtitle);
                }
                case "title-times" -> {
                    TitleTimes times = SendTitleTimesRequest.parseFrom(msg.getData()).getTimes();
                    Title.Times titleTimes = Title.Times.times(
                            Duration.ofMillis(times.getFadeInMs()),
                            Duration.ofMillis(times.getStayMs()),
                            Duration.ofMillis(times.getFadeOutMs())
                    );
                    yield (playerId, player) -> player.sendTitlePart(TitlePart.TIMES, titleTimes);
                }
                case "clear-title" -> (playerId, player) -> player.clearTitle();
                case "reset-title" -> (playerId, player) -> player.resetTitle();
                case "sound" -> {
                    Sound sound = convertSound(SendPlaySoundRequest.parseFrom(msg.getData()).getSound());
                    yield (playerId, player) -> player.playSound(sound);
                }
                case "sound-at" -> {
                    SendPlaySoundAtCoordinatesRequest request = SendPlaySoundAtCoordinatesRequest.parseFrom(msg.getData());
                    Sound sound = convertSound(request.getSound());
                    SoundCoordinates coords = request.getCoordinates();
                    yield (playerId, player) -> player.playSound(sound, coords.getX(), coords.getY(), coords.getZ());
                }
                case "stop-sound" -> {
                    SoundStop soundStop = convertSoundStop(SendStopSoundRequest.parseFrom(msg.getData()));
                    yield (playerId, player) -> player.stopSound(soundStop);
                }
                case "bossbar" -> prepareBossBar(SendBossBarRequest.parseFrom(msg.getData()));
                case "bossbar-remove" -> prepareBossBarRemove(SendBossBarRemoveRequest.parseFrom(msg.getData()));
                case "player-list" -> {
                    SendPlayerListHeaderAndFooterRequest request = SendPlayerListHeaderAndFooterRequest.parseFrom(msg.getData());
                    Component header = deserialize(request.getHeader());
                    Component footer = deserialize(request.getFooter());
                    yield (playerId, player) -> player.sendPlayerListHeaderAndFooter(header, footer);
                }
                case "open-book" -> {
                    Book book = convertBook(SendOpenBookRequest.parseFrom(msg.getData()).getBook());
                    yield (playerId, player) -> player.openBook(book);
                }
                default -> null;
            };
        } catch (Exception ignored) {
            return null;
        }
    }

    private PlayerAction prepareBossBar(SendBossBarRequest request) {
        AdventureBossBar config = request.getBossBar();
        BossBar bossBar = BossBar.bossBar(
                deserialize(config.getTitle()),
                config.getProgress(),
                convertBossBarColor(config.getColor()),
                convertBossBarOverlay(config.getOverlay())
        );

        return (playerId, player) -> {
            Map<UUID, BossBar> perPlayerBossBars = activeBossBars.computeIfAbsent(config.getId(), ignored -> new ConcurrentHashMap<>());
            BossBar oldBar = perPlayerBossBars.put(playerId, bossBar);
            if (oldBar != null) {
                player.hideBossBar(oldBar);
            }
            player.showBossBar(bossBar);
        };
    }

    private PlayerAction prepareBossBarRemove(SendBossBarRemoveRequest request) {
        String bossBarId = request.getBossBarId();
        return (playerId, player) -> {
            Map<UUID, BossBar> perPlayerBossBars = activeBossBars.get(bossBarId);
            if (perPlayerBossBars == null) return;

            BossBar bossBar = perPlayerBossBars.remove(playerId);
            if (bossBar != null) {
                player.hideBossBar(bossBar);
            }
            if (perPlayerBossBars.isEmpty()) {
                activeBossBars.remove(bossBarId, perPlayerBossBars);
            }
        };
    }

    private Book convertBook(AdventureBook bookConfig) {
        Book.Builder bookBuilder = Book.builder()
                .title(deserialize(bookConfig.getTitle()))
                .author(deserialize(bookConfig.getAuthor()));

        for (AdventureComponent page : bookConfig.getPagesList()) {
            bookBuilder.addPage(deserialize(page));
        }

        return bookBuilder.build();
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

    private SoundStop convertSoundStop(SendStopSoundRequest request) {
        if (request.hasSound()) {
            return SoundStop.named(Key.key(request.getSound()));
        }
        if (request.hasSource()) {
            return SoundStop.source(convertSoundSource(request.getSource()));
        }
        return SoundStop.all();
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
