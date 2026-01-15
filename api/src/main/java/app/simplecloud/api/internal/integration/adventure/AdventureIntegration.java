package app.simplecloud.api.internal.integration.adventure;

import app.simplecloud.api.CloudApi;
import app.simplecloud.api.internal.CloudApiImpl;
import build.buf.gen.simplecloud.adventure.v1.*;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.Message;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.title.Title;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Adventure integration for server plugins.
 * Handles adventure actions (messages, titles, sounds, boss bars) from remote sources.
 */
public class AdventureIntegration {

    private final Connection natsConnection;
    private final String networkId;
    private final Function<UUID, Audience> playerResolver;
    private final Supplier<Audience> allPlayersSupplier;

    private final boolean subscribeToPlayers;
    private final String subscribeToServerId;
    private final String subscribeToGroup;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Map<String, BossBar> activeBossBars = new ConcurrentHashMap<>();

    private Dispatcher dispatcher;

    private AdventureIntegration(Builder builder) {
        this.natsConnection = builder.natsConnection;
        this.networkId = builder.networkId;
        this.playerResolver = builder.playerResolver;
        this.allPlayersSupplier = builder.allPlayersSupplier;
        this.subscribeToPlayers = builder.subscribeToPlayers;
        this.subscribeToServerId = builder.subscribeToServerId;
        this.subscribeToGroup = builder.subscribeToGroup;
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
        private Function<UUID, Audience> playerResolver;
        private Supplier<Audience> allPlayersSupplier;
        private boolean subscribeToPlayers = false;
        private String subscribeToServerId = null;
        private String subscribeToGroup = null;

        private Builder(Connection natsConnection, String networkId) {
            this.natsConnection = natsConnection;
            this.networkId = networkId;
        }

        /**
         * Sets the resolver for player-specific messages.
         * The function receives a player UUID and returns their Audience (or null if not on this server).
         */
        public Builder playerResolver(Function<UUID, Audience> resolver) {
            this.playerResolver = resolver;
            return this;
        }

        /**
         * Sets the supplier for all players on this server.
         * Used for server-wide and group-wide messages.
         * Can be a ForwardingAudience wrapping multiple audiences.
         */
        public Builder allPlayersSupplier(Supplier<Audience> supplier) {
            this.allPlayersSupplier = supplier;
            return this;
        }

        /**
         * Subscribe to player-specific messages.
         * Subject pattern: {networkId}.adventure.player.{uuid}.{action}
         */
        public Builder forPlayers() {
            this.subscribeToPlayers = true;
            return this;
        }

        /**
         * Subscribe to server-specific messages.
         * Subject pattern: {networkId}.adventure.server.{serverId}.{action}
         */
        public Builder forServer(String serverId) {
            this.subscribeToServerId = serverId;
            return this;
        }

        /**
         * Subscribe to group-specific messages.
         * Subject pattern: {networkId}.adventure.group.{groupName}.{action}
         */
        public Builder forGroup(String groupName) {
            this.subscribeToGroup = groupName;
            return this;
        }

        public AdventureIntegration build() {
            if (playerResolver == null) {
                throw new IllegalStateException("playerResolver is required");
            }
            if ((subscribeToServerId != null || subscribeToGroup != null) && allPlayersSupplier == null) {
                throw new IllegalStateException("allPlayersSupplier is required when subscribing to server or group");
            }
            return new AdventureIntegration(this);
        }
    }

    /**
     * Starts listening for adventure actions.
     */
    public void start() {
        if (!running.compareAndSet(false, true)) return;

        dispatcher = natsConnection.createDispatcher(null);

        if (subscribeToPlayers) {
            String playerPrefix = networkId + ".adventure.player.*.";
            subscribeActions(playerPrefix, this::handlePlayerMessage);
        }

        if (subscribeToServerId != null) {
            String serverPrefix = networkId + ".adventure.server." + subscribeToServerId + ".";
            subscribeActions(serverPrefix, this::handleBroadcastMessage);
        }

        if (subscribeToGroup != null) {
            String groupPrefix = networkId + ".adventure.group." + subscribeToGroup + ".";
            subscribeActions(groupPrefix, this::handleBroadcastMessage);
        }
    }

    private void subscribeActions(String prefix, java.util.function.BiConsumer<Message, AudienceProvider> handler) {
        dispatcher.subscribe(prefix + "message", msg -> handler.accept(msg, () -> getAudienceFromSubject(msg.getSubject())));
        dispatcher.subscribe(prefix + "actionbar", msg -> handler.accept(msg, () -> getAudienceFromSubject(msg.getSubject())));
        dispatcher.subscribe(prefix + "title", msg -> handler.accept(msg, () -> getAudienceFromSubject(msg.getSubject())));
        dispatcher.subscribe(prefix + "subtitle", msg -> handler.accept(msg, () -> getAudienceFromSubject(msg.getSubject())));
        dispatcher.subscribe(prefix + "title-times", msg -> handler.accept(msg, () -> getAudienceFromSubject(msg.getSubject())));
        dispatcher.subscribe(prefix + "clear-title", msg -> handler.accept(msg, () -> getAudienceFromSubject(msg.getSubject())));
        dispatcher.subscribe(prefix + "reset-title", msg -> handler.accept(msg, () -> getAudienceFromSubject(msg.getSubject())));
        dispatcher.subscribe(prefix + "sound", msg -> handler.accept(msg, () -> getAudienceFromSubject(msg.getSubject())));
        dispatcher.subscribe(prefix + "sound-at", msg -> handler.accept(msg, () -> getAudienceFromSubject(msg.getSubject())));
        dispatcher.subscribe(prefix + "stop-sound", msg -> handler.accept(msg, () -> getAudienceFromSubject(msg.getSubject())));
        dispatcher.subscribe(prefix + "bossbar", msg -> handler.accept(msg, () -> getAudienceFromSubject(msg.getSubject())));
        dispatcher.subscribe(prefix + "bossbar-remove", msg -> handler.accept(msg, () -> getAudienceFromSubject(msg.getSubject())));
        dispatcher.subscribe(prefix + "player-list", msg -> handler.accept(msg, () -> getAudienceFromSubject(msg.getSubject())));
        dispatcher.subscribe(prefix + "open-book", msg -> handler.accept(msg, () -> getAudienceFromSubject(msg.getSubject())));
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

    @FunctionalInterface
    private interface AudienceProvider {
        Audience get();
    }

    private Audience getAudienceFromSubject(String subject) {
        // Subject patterns:
        // Player: {networkId}.adventure.player.{uuid}.{action}
        // Server: {networkId}.adventure.server.{serverId}.{action}
        // Group:  {networkId}.adventure.group.{groupName}.{action}
        String[] parts = subject.split("\\.");
        if (parts.length < 5) return Audience.empty();

        String type = parts[2]; // "player", "server", or "group"

        if ("player".equals(type)) {
            try {
                UUID uuid = UUID.fromString(parts[3]);
                Audience audience = playerResolver.apply(uuid);
                return audience != null ? audience : Audience.empty();
            } catch (IllegalArgumentException e) {
                return Audience.empty();
            }
        } else {
            // Server or group - return all players
            return allPlayersSupplier.get();
        }
    }

    private void handlePlayerMessage(Message msg, AudienceProvider provider) {
        String action = extractAction(msg.getSubject());
        executeAction(action, msg, provider.get());
    }

    private void handleBroadcastMessage(Message msg, AudienceProvider ignored) {
        String action = extractAction(msg.getSubject());
        executeAction(action, msg, allPlayersSupplier.get());
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
                    audience.showTitle(Title.title(deserialize(titleReq.getTitle()), Component.empty()));
                    break;
                case "subtitle":
                    SendSubtitleRequest subReq = SendSubtitleRequest.parseFrom(msg.getData());
                    audience.showTitle(Title.title(Component.empty(), deserialize(subReq.getSubtitle())));
                    break;
                case "title-times":
                    SendTitleTimesRequest timesReq = SendTitleTimesRequest.parseFrom(msg.getData());
                    TitleTimes times = timesReq.getTimes();
                    Title.Times titleTimes = Title.Times.times(
                            Duration.ofMillis(times.getFadeInMs()),
                            Duration.ofMillis(times.getStayMs()),
                            Duration.ofMillis(times.getFadeOutMs())
                    );
                    audience.showTitle(Title.title(Component.empty(), Component.empty(), titleTimes));
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
        switch (source) {
            case SOUND_SOURCE_MUSIC:
                return Sound.Source.MUSIC;
            case SOUND_SOURCE_RECORD:
                return Sound.Source.RECORD;
            case SOUND_SOURCE_WEATHER:
                return Sound.Source.WEATHER;
            case SOUND_SOURCE_BLOCK:
                return Sound.Source.BLOCK;
            case SOUND_SOURCE_HOSTILE:
                return Sound.Source.HOSTILE;
            case SOUND_SOURCE_NEUTRAL:
                return Sound.Source.NEUTRAL;
            case SOUND_SOURCE_PLAYER:
                return Sound.Source.PLAYER;
            case SOUND_SOURCE_AMBIENT:
                return Sound.Source.AMBIENT;
            case SOUND_SOURCE_VOICE:
                return Sound.Source.VOICE;
            default:
                return Sound.Source.MASTER;
        }
    }

    private BossBar.Color convertBossBarColor(BossBarColor color) {
        switch (color) {
            case BOSS_BAR_COLOR_BLUE:
                return BossBar.Color.BLUE;
            case BOSS_BAR_COLOR_RED:
                return BossBar.Color.RED;
            case BOSS_BAR_COLOR_GREEN:
                return BossBar.Color.GREEN;
            case BOSS_BAR_COLOR_YELLOW:
                return BossBar.Color.YELLOW;
            case BOSS_BAR_COLOR_PURPLE:
                return BossBar.Color.PURPLE;
            case BOSS_BAR_COLOR_WHITE:
                return BossBar.Color.WHITE;
            default:
                return BossBar.Color.PINK;
        }
    }

    private BossBar.Overlay convertBossBarOverlay(BossBarOverlay overlay) {
        switch (overlay) {
            case BOSS_BAR_OVERLAY_NOTCHED_6:
                return BossBar.Overlay.NOTCHED_6;
            case BOSS_BAR_OVERLAY_NOTCHED_10:
                return BossBar.Overlay.NOTCHED_10;
            case BOSS_BAR_OVERLAY_NOTCHED_12:
                return BossBar.Overlay.NOTCHED_12;
            case BOSS_BAR_OVERLAY_NOTCHED_20:
                return BossBar.Overlay.NOTCHED_20;
            default:
                return BossBar.Overlay.PROGRESS;
        }
    }
}
