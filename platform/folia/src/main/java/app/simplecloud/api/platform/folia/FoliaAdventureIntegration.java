package app.simplecloud.api.platform.folia;

import app.simplecloud.api.internal.CloudApiImpl;
import app.simplecloud.api.runtime.SimpleCloudRuntime;
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
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.inventory.Book;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.sound.SoundStop;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class FoliaAdventureIntegration {

    private final JavaPlugin plugin;
    private final Connection natsConnection;
    private final String networkId;
    private final String serverId;
    private final String groupName;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Map<String, Map<UUID, BossBar>> activeBossBars = new ConcurrentHashMap<>();

    private Dispatcher dispatcher;

    public FoliaAdventureIntegration(JavaPlugin plugin, CloudApiImpl cloudApi) {
        this.plugin = plugin;
        this.natsConnection = cloudApi.getNatsConnection();
        this.networkId = cloudApi.getNetworkId();
        this.serverId = SimpleCloudRuntime.serverId();
        String runtimeGroupName = SimpleCloudRuntime.groupName();
        this.groupName = runtimeGroupName != null ? runtimeGroupName : serverId;
    }

    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }

        dispatcher = natsConnection.createDispatcher(null);

        subscribeActions(networkId + ".adventure.player.*.", this::handlePlayerMessage);
        subscribeActions(networkId + ".adventure.server." + serverId + ".", this::handleBroadcastMessage);
        subscribeActions(networkId + ".adventure.group." + groupName + ".", this::handleBroadcastMessage);
    }

    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }

        if (dispatcher != null) {
            try {
                dispatcher.drain(Duration.ofSeconds(1));
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
        activeBossBars.clear();
    }

    private void subscribeActions(String prefix, Consumer<Message> handler) {
        dispatcher.subscribe(prefix + "message", handler::accept);
        dispatcher.subscribe(prefix + "actionbar", handler::accept);
        dispatcher.subscribe(prefix + "title", handler::accept);
        dispatcher.subscribe(prefix + "subtitle", handler::accept);
        dispatcher.subscribe(prefix + "title-times", handler::accept);
        dispatcher.subscribe(prefix + "clear-title", handler::accept);
        dispatcher.subscribe(prefix + "reset-title", handler::accept);
        dispatcher.subscribe(prefix + "sound", handler::accept);
        dispatcher.subscribe(prefix + "sound-at", handler::accept);
        dispatcher.subscribe(prefix + "stop-sound", handler::accept);
        dispatcher.subscribe(prefix + "bossbar", handler::accept);
        dispatcher.subscribe(prefix + "bossbar-remove", handler::accept);
        dispatcher.subscribe(prefix + "player-list", handler::accept);
        dispatcher.subscribe(prefix + "open-book", handler::accept);
    }

    private void handlePlayerMessage(Message msg) {
        UUID playerId = extractPlayerId(msg.getSubject());
        if (playerId == null) {
            return;
        }

        scheduleForPlayer(playerId, player -> executeAction(extractAction(msg.getSubject()), msg, player));
    }

    private void handleBroadcastMessage(Message msg) {
        String action = extractAction(msg.getSubject());
        scheduleForAllPlayers(player -> executeAction(action, msg, player));
    }

    private void scheduleForPlayer(UUID playerId, Consumer<Player> action) {
        Bukkit.getGlobalRegionScheduler().execute(plugin, () -> {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                return;
            }
            player.getScheduler().run(plugin, task -> action.accept(player), null);
        });
    }

    private void scheduleForAllPlayers(Consumer<Player> action) {
        Bukkit.getGlobalRegionScheduler().execute(plugin, () -> {
            List<Player> players = List.copyOf(Bukkit.getOnlinePlayers());
            for (Player player : players) {
                player.getScheduler().run(plugin, task -> action.accept(player), null);
            }
        });
    }

    private UUID extractPlayerId(String subject) {
        String[] parts = subject.split("\\.");
        if (parts.length < 5 || !"player".equals(parts[2])) {
            return null;
        }

        try {
            return UUID.fromString(parts[3]);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private String extractAction(String subject) {
        String[] parts = subject.split("\\.");
        return parts.length > 0 ? parts[parts.length - 1] : "";
    }

    private void executeAction(String action, Message msg, Player player) {
        try {
            switch (action) {
                case "message" -> {
                    SendMessageRequest msgReq = SendMessageRequest.parseFrom(msg.getData());
                    player.sendMessage(deserialize(msgReq.getMessage()));
                }
                case "actionbar" -> {
                    SendActionbarRequest request = SendActionbarRequest.parseFrom(msg.getData());
                    player.sendActionBar(deserialize(request.getMessage()));
                }
                case "title" -> {
                    SendTitleRequest request = SendTitleRequest.parseFrom(msg.getData());
                    player.showTitle(Title.title(deserialize(request.getTitle()), Component.empty()));
                }
                case "subtitle" -> {
                    SendSubtitleRequest request = SendSubtitleRequest.parseFrom(msg.getData());
                    player.showTitle(Title.title(Component.empty(), deserialize(request.getSubtitle())));
                }
                case "title-times" -> {
                    SendTitleTimesRequest request = SendTitleTimesRequest.parseFrom(msg.getData());
                    TitleTimes times = request.getTimes();
                    Title.Times titleTimes = Title.Times.times(
                        Duration.ofMillis(times.getFadeInMs()),
                        Duration.ofMillis(times.getStayMs()),
                        Duration.ofMillis(times.getFadeOutMs())
                    );
                    player.showTitle(Title.title(Component.empty(), Component.empty(), titleTimes));
                }
                case "clear-title" -> player.clearTitle();
                case "reset-title" -> player.resetTitle();
                case "sound" -> {
                    SendPlaySoundRequest request = SendPlaySoundRequest.parseFrom(msg.getData());
                    player.playSound(convertSound(request.getSound()));
                }
                case "sound-at" -> {
                    SendPlaySoundAtCoordinatesRequest request = SendPlaySoundAtCoordinatesRequest.parseFrom(msg.getData());
                    SoundCoordinates coordinates = request.getCoordinates();
                    player.playSound(convertSound(request.getSound()), coordinates.getX(), coordinates.getY(), coordinates.getZ());
                }
                case "stop-sound" -> handleStopSound(player, SendStopSoundRequest.parseFrom(msg.getData()));
                case "bossbar" -> handleBossBar(player, SendBossBarRequest.parseFrom(msg.getData()));
                case "bossbar-remove" -> handleBossBarRemove(player, SendBossBarRemoveRequest.parseFrom(msg.getData()));
                case "player-list" -> {
                    SendPlayerListHeaderAndFooterRequest request = SendPlayerListHeaderAndFooterRequest.parseFrom(msg.getData());
                    player.sendPlayerListHeaderAndFooter(deserialize(request.getHeader()), deserialize(request.getFooter()));
                }
                case "open-book" -> handleOpenBook(player, SendOpenBookRequest.parseFrom(msg.getData()));
                default -> {
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void handleStopSound(Player player, SendStopSoundRequest request) {
        if (request.hasSound()) {
            player.stopSound(SoundStop.named(Key.key(request.getSound())));
        } else if (request.hasSource()) {
            player.stopSound(SoundStop.source(convertSoundSource(request.getSource())));
        } else {
            player.stopSound(SoundStop.all());
        }
    }

    private void handleBossBar(Player player, SendBossBarRequest request) {
        AdventureBossBar config = request.getBossBar();
        BossBar bossBar = BossBar.bossBar(
            deserialize(config.getTitle()),
            config.getProgress(),
            convertBossBarColor(config.getColor()),
            convertBossBarOverlay(config.getOverlay())
        );

        Map<UUID, BossBar> perPlayerBossBars = activeBossBars.computeIfAbsent(config.getId(), ignored -> new ConcurrentHashMap<>());
        BossBar oldBossBar = perPlayerBossBars.put(player.getUniqueId(), bossBar);
        if (oldBossBar != null) {
            player.hideBossBar(oldBossBar);
        }
        player.showBossBar(bossBar);
    }

    private void handleBossBarRemove(Player player, SendBossBarRemoveRequest request) {
        Map<UUID, BossBar> perPlayerBossBars = activeBossBars.get(request.getBossBarId());
        if (perPlayerBossBars == null) {
            return;
        }

        BossBar bossBar = perPlayerBossBars.remove(player.getUniqueId());
        if (bossBar != null) {
            player.hideBossBar(bossBar);
        }
        if (perPlayerBossBars.isEmpty()) {
            activeBossBars.remove(request.getBossBarId(), perPlayerBossBars);
        }
    }

    private void handleOpenBook(Player player, SendOpenBookRequest request) {
        AdventureBook bookConfig = request.getBook();
        Book.Builder bookBuilder = Book.builder()
            .title(deserialize(bookConfig.getTitle()))
            .author(deserialize(bookConfig.getAuthor()));

        for (AdventureComponent page : bookConfig.getPagesList()) {
            bookBuilder.addPage(deserialize(page));
        }

        player.openBook(bookBuilder.build());
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
