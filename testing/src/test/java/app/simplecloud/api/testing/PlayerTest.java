package app.simplecloud.api.testing;

import app.simplecloud.api.CloudApiException;
import app.simplecloud.api.event.player.PlayerClientSettingsUpdatedEvent;
import app.simplecloud.api.event.player.PlayerDisconnectEvent;
import app.simplecloud.api.event.player.PlayerKickEvent;
import app.simplecloud.api.event.player.PlayerLoginEvent;
import app.simplecloud.api.event.player.PlayerProfileChangedEvent;
import app.simplecloud.api.event.player.PlayerServerSwitchEvent;
import app.simplecloud.api.player.CloudPlayer;
import app.simplecloud.api.player.CloudPlayer.ConnectResult;
import app.simplecloud.api.player.PlayerApi;
import app.simplecloud.api.player.PlayerClientSettings;
import app.simplecloud.api.player.PlayerQuery;
import app.simplecloud.api.player.ServerSelectionMode;
import app.simplecloud.api.player.UpdateClientSettingsRequest;
import app.simplecloud.api.server.ServerState;
import app.simplecloud.api.testing.event.TestEvents;
import app.simplecloud.api.testing.group.TestGroup;
import app.simplecloud.api.testing.player.TestPlayer;
import app.simplecloud.api.testing.server.TestServer;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@CloudTest
class PlayerTest {

    @Test
    void createdPlayersAreOfflineAndVisibleThroughApi(TestCloud cloud) {
        TestPlayer steve = cloud.createPlayer("Steve");

        CloudPlayer player = cloud.api().player().get("steve").join();

        assertEquals(steve.getUniqueId(), player.getUniqueId());
        assertFalse(player.isOnline());
        assertEquals(steve, cloud.player(steve.getUniqueId()).orElseThrow());
        assertEquals(UUID.nameUUIDFromBytes("OfflinePlayer:Steve".getBytes()), steve.getUniqueId());
    }

    @Test
    void duplicatePlayerNameIsRejected(TestCloud cloud) {
        cloud.createPlayer("Steve");

        CloudApiException exception = assertThrows(CloudApiException.class,
                () -> cloud.createPlayer(UUID.randomUUID(), "steve"));

        assertEquals(CloudApiException.Reason.CONFLICT, exception.getReason());
    }

    @Test
    void joinLogsInAndSwitchesServer(TestCloud cloud) {
        TestServer proxy = cloud.createGroup("proxy").startServer();
        TestServer lobby = cloud.createGroup("lobby").startServer();
        var logins = cloud.collect(PlayerLoginEvent.class);
        var switches = cloud.collect(PlayerServerSwitchEvent.class);

        TestPlayer steve = cloud.createPlayer("Steve").login(proxy).join(lobby);

        PlayerLoginEvent login = logins.single();
        assertTrue(login.isNewPlayer());
        assertEquals("proxy-1", login.getProxyName());
        assertEquals(steve.getSessionId(), login.getSessionId());
        PlayerServerSwitchEvent change = switches.single();
        assertNull(change.getPreviousServerName());
        assertEquals("lobby-1", change.getNewServerName());
        assertEquals("lobby-1", steve.getConnectedServerName());
        assertEquals(1, lobby.getPlayerCount());
        assertEquals(1, proxy.getPlayerCount());
    }

    @Test
    void switchingServersMovesPlayerCount(TestCloud cloud) {
        TestGroup lobby = cloud.createGroup("lobby");
        TestServer first = lobby.startServer();
        TestServer second = lobby.startServer();
        TestPlayer steve = cloud.createPlayer("Steve").join(first);

        steve.join(second);

        assertEquals(0, first.getPlayerCount());
        assertEquals(1, second.getPlayerCount());
    }

    @Test
    void disconnectRecordsSessionAndOnlineTime(TestCloud cloud) {
        TestServer lobby = cloud.createGroup("lobby").startServer();
        TestPlayer steve = cloud.createPlayer("Steve").join(lobby);
        var disconnects = cloud.collect(PlayerDisconnectEvent.class);

        cloud.advanceTime(Duration.ofMinutes(5));
        assertEquals(300, steve.getOnlineTimeSeconds());
        steve.disconnect();

        PlayerDisconnectEvent event = disconnects.single();
        assertEquals(300, event.getSessionDurationSeconds());
        assertEquals("lobby-1", event.getServerName());
        assertFalse(steve.isOnline());
        assertEquals(0, lobby.getPlayerCount());
        assertEquals(300, cloud.api().player().getOnlineTimeSeconds(steve.getUniqueId()).join());
        assertEquals(1, cloud.api().player().getSessions(steve.getUniqueId()).join().size());
        assertTrue(cloud.api().player().findCurrentSession(steve.getUniqueId()).join().isEmpty());
    }

    @Test
    void secondLoginIsNotNew(TestCloud cloud) {
        TestPlayer steve = cloud.createPlayer("Steve").login();
        steve.disconnect();
        var logins = cloud.collect(PlayerLoginEvent.class);

        steve.login();

        assertFalse(logins.single().isNewPlayer());
    }

    @Test
    void onlinePlayersCanBeFilteredByServer(TestCloud cloud) {
        TestServer lobby = cloud.createGroup("lobby").startServer();
        TestServer game = cloud.createGroup("game").startServer();
        cloud.createPlayer("Steve").join(lobby);
        cloud.createPlayer("Alex").join(game);
        cloud.createPlayer("Herobrine");
        PlayerApi players = cloud.api().player();

        assertEquals(2, players.getOnlinePlayerCount().join());
        assertEquals(List.of("Steve"), names(players.getOnlinePlayers("lobby-1").join()));
        assertEquals(List.of("Alex", "Herobrine", "Steve"), names(players.list(null).join()));
    }

    @Test
    void listSupportsSearchSortingAndPaging(TestCloud cloud) {
        cloud.createPlayer("Steve").login();
        cloud.advanceTime(Duration.ofMinutes(1));
        cloud.createPlayer("Stella").login();
        cloud.createPlayer("Alex").login();

        List<CloudPlayer> result = cloud.api().player().list(PlayerQuery.create()
                .search("ste")
                .sortBy(PlayerQuery.SortField.ONLINE_TIME)
                .sortOrder(PlayerQuery.SortOrder.DESC)
                .limit(1)).join();

        assertEquals(List.of("Steve"), names(result));
    }

    @Test
    void connectThroughApiSwitchesServer(TestCloud cloud) {
        TestServer lobby = cloud.createGroup("lobby").startServer();
        TestServer game = cloud.createGroup("game").startServer();
        TestPlayer steve = cloud.createPlayer("Steve").join(lobby);
        CloudPlayer player = cloud.api().player().get(steve.getUniqueId()).join();

        assertEquals(ConnectResult.SUCCESS, player.connect("game-1").join());
        assertEquals(ConnectResult.ALREADY_CONNECTED, player.connect("game-1").join());
        assertEquals(ConnectResult.SERVER_NOT_FOUND, player.connect("missing-1").join());
        assertEquals("game-1", steve.getConnectedServerName());
        assertEquals(1, game.getPlayerCount());
    }

    @Test
    void connectFailsForOfflinePlayers(TestCloud cloud) {
        cloud.createGroup("lobby").startServer();
        TestPlayer steve = cloud.createPlayer("Steve");

        assertEquals(ConnectResult.PLAYER_NOT_FOUND, steve.connect("lobby-1").join());
    }

    @Test
    void connectToGroupHonorsSelectionMode(TestCloud cloud) {
        TestGroup lobby = cloud.createGroup("lobby");
        TestServer first = lobby.startServer().setPlayerCount(5);
        TestServer second = lobby.startServer().setPlayerCount(2);
        lobby.startServer().setState(ServerState.INGAME);
        TestPlayer steve = cloud.createPlayer("Steve").login();
        TestPlayer alex = cloud.createPlayer("Alex").login();

        assertEquals(ConnectResult.SUCCESS, steve.connectToGroup("lobby", ServerSelectionMode.MIN_PLAYERS).join());
        assertEquals(ConnectResult.SUCCESS, alex.connectToGroup("lobby", ServerSelectionMode.FIRST).join());

        assertEquals(second.getName(), steve.getConnectedServerName());
        assertEquals(first.getName(), alex.getConnectedServerName());
        assertEquals(ConnectResult.ALREADY_CONNECTED, steve.connectToGroup("lobby").join());
        assertEquals(ConnectResult.SERVER_NOT_FOUND, steve.connectToGroup("missing").join());
    }

    @Test
    void kickPublishesKickAndDisconnect(TestCloud cloud) {
        TestServer lobby = cloud.createGroup("lobby").startServer();
        TestPlayer steve = cloud.createPlayer("Steve").join(lobby);
        var kicks = cloud.collect(PlayerKickEvent.class);
        var disconnects = cloud.collect(PlayerDisconnectEvent.class);

        steve.kick(Component.text("bye")).join();

        assertEquals(Component.text("bye"), kicks.single().getReason());
        assertEquals("lobby-1", kicks.single().getServerName());
        assertEquals(1, disconnects.count());
        assertFalse(cloud.api().player().kick(steve.getUniqueId(), null).join());
    }

    @Test
    void stoppingProxyDisconnectsPlayers(TestCloud cloud) {
        TestServer proxy = cloud.createGroup("proxy").startServer();
        TestServer lobby = cloud.createGroup("lobby").startServer();
        TestPlayer steve = cloud.createPlayer("Steve").login(proxy).join(lobby);
        TestPlayer alex = cloud.createPlayer("Alex").join(lobby);

        lobby.stop();
        assertTrue(alex.isOnline());
        assertNull(alex.getConnectedServerName());

        proxy.stop();
        assertFalse(steve.isOnline());
    }

    @Test
    void messagesSentThroughApiArriveAtTestPlayer(TestCloud cloud) {
        TestPlayer steve = cloud.createPlayer("Steve").login();

        cloud.api().player().get("Steve").join().sendMessage(Component.text("hello"));
        steve.sendActionBar(Component.text("bar"));

        assertEquals(List.of(Component.text("hello")), steve.messages());
        assertEquals(List.of(Component.text("bar")), steve.actionBars());

        steve.clearReceived();

        assertTrue(steve.messages().isEmpty());
    }

    @Test
    void profileChangeRenamesPlayer(TestCloud cloud) {
        TestPlayer steve = cloud.createPlayer("Steve").login();
        steve.disconnect();
        var changes = cloud.collect(PlayerProfileChangedEvent.class);
        var logins = cloud.collect(PlayerLoginEvent.class);

        steve.changeProfile("Steven", "texture").login();

        PlayerProfileChangedEvent change = changes.single();
        assertEquals("Steve", change.getOldProfile().getName());
        assertEquals("Steven", change.getNewProfile().getName());
        assertEquals("Steven", steve.getName());
        assertTrue(logins.single().isNameChanged());
        assertEquals("texture", cloud.api().player().getLatestProfile(steve.getUniqueId()).join().getTexture());
        assertEquals(2, cloud.api().player().getProfiles(steve.getUniqueId()).join().size());
    }

    @Test
    void clientSettingsCanBeUpdated(TestCloud cloud) {
        TestPlayer steve = cloud.createPlayer("Steve").login();
        var updates = cloud.collect(PlayerClientSettingsUpdatedEvent.class);

        steve.updateClientSettings(UpdateClientSettingsRequest.builder()
                .locale("de_DE")
                .viewDistance(12)
                .build());

        PlayerClientSettings settings = cloud.api().player().getClientSettings(steve.getUniqueId()).join();
        assertEquals("de_DE", settings.getLocale());
        assertEquals(12, settings.getViewDistance());
        assertEquals(steve.getSessionId(), updates.single().getSessionId());
        assertEquals("de_DE", cloud.api().player().getCurrentSession(steve.getUniqueId()).join()
                .getClientSettings().getLocale());
    }

    @Test
    void propertiesCanBeChangedThroughApi(TestCloud cloud) {
        TestPlayer steve = cloud.createPlayer("Steve").setProperty("rank", "admin");
        PlayerApi players = cloud.api().player();

        players.updatePlayerProperty(steve.getUniqueId(), "coins", "10").join();
        players.deletePlayerProperty(steve.getUniqueId(), "rank").join();

        assertEquals(Map.of("coins", "10"), steve.getProperties());
    }

    @Test
    void onlineTimeCanBeAdjustedThroughApi(TestCloud cloud) {
        TestPlayer steve = cloud.createPlayer("Steve");

        cloud.api().player().addOnlineTimeSeconds(steve.getUniqueId(), 60).join();

        assertEquals(60, steve.getOnlineTimeSeconds());
    }

    @Test
    void unknownPlayerFailsWithNotFound(TestCloud cloud) {
        CompletionException exception = assertThrows(CompletionException.class,
                () -> cloud.api().player().get(UUID.randomUUID()).join());

        CloudApiException cause = assertInstanceOf(CloudApiException.class, exception.getCause());
        assertEquals(CloudApiException.Reason.NOT_FOUND, cause.getReason());
        assertTrue(cloud.api().player().find("Nobody").join().isEmpty());
    }

    @Test
    void publishedPlayerEventsReachApiSubscribers(TestCloud cloud) {
        TestPlayer steve = cloud.createPlayer("Steve");
        var logins = cloud.collect(PlayerLoginEvent.class);

        cloud.publish(TestEvents.playerLogin(steve, true, false));

        PlayerLoginEvent event = logins.single();
        assertEquals(steve.getUniqueId(), event.getPlayerId());
        assertEquals(cloud.networkId(), event.getNetworkId());
        assertFalse(steve.isOnline());
    }

    @Test
    void networkFailuresAffectPlayerApi(TestCloud cloud) {
        TestPlayer steve = cloud.createPlayer("Steve");
        cloud.network().failNext(PlayerApi.class, "get");

        assertThrows(CompletionException.class, () -> cloud.api().player().get(steve.getUniqueId()).join());
        assertEquals("Steve", cloud.api().player().get(steve.getUniqueId()).join().getName());
    }

    private static List<String> names(List<CloudPlayer> players) {
        return players.stream().map(CloudPlayer::getName).toList();
    }
}
