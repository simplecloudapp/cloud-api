package app.simplecloud.api.internal.integration.player;

import app.simplecloud.api.internal.nats.FakeNatsConnection;
import app.simplecloud.api.player.CloudPlayer;
import app.simplecloud.api.server.ServerApi;
import build.buf.gen.simplecloud.adventure.v1.AdventureComponent;
import build.buf.gen.simplecloud.player.v2.ConnectPlayerRequest;
import build.buf.gen.simplecloud.player.v2.ConnectPlayerResponse;
import build.buf.gen.simplecloud.player.v2.ConnectResult;
import build.buf.gen.simplecloud.player.v2.KickPlayerRequest;
import build.buf.gen.simplecloud.player.v2.KickPlayerResponse;
import io.nats.client.Message;
import io.nats.client.impl.NatsMessage;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(5)
class PlayerCommandHandlerTest {

    private static final String NETWORK_ID = "test-network";
    private static final String PLAYER_ID = "11111111-1111-1111-1111-111111111111";
    private static final UUID PLAYER_UUID = UUID.fromString(PLAYER_ID);
    private static final String OTHER_PLAYER_ID = "33333333-3333-3333-3333-333333333333";
    private static final UUID OTHER_PLAYER_UUID = UUID.fromString(OTHER_PLAYER_ID);
    private static final String KICK_SUBSCRIPTION = NETWORK_ID + ".player.*.kick";
    private static final String CONNECT_SUBSCRIPTION = NETWORK_ID + ".player.*.connect";

    @Test
    void kickRequestForPlayerOnAnotherProxy_isNotAnswered() throws Exception {
        FakeNatsConnection nats = FakeNatsConnection.withoutRequests();
        FakeProxyPlayers proxyPlayers = FakeProxyPlayers.withoutPlayers();
        startCommandHandler(nats, proxyPlayers);
        KickPlayerRequest request = KickPlayerRequest.newBuilder().setPlayerId(PLAYER_ID).build();

        nats.deliver(KICK_SUBSCRIPTION, kickMessage(PLAYER_ID, request));

        assertEquals(List.of(), proxyPlayers.kicks);
        assertEquals(List.of(), nats.published());
    }

    @Test
    void kickRequestWithBlankReason_kicksWithEmptyComponent() throws Exception {
        FakeNatsConnection nats = FakeNatsConnection.withoutRequests();
        FakeProxyPlayers proxyPlayers = FakeProxyPlayers.connected(PLAYER_UUID);
        startCommandHandler(nats, proxyPlayers);
        KickPlayerRequest request = KickPlayerRequest.newBuilder()
                .setPlayerId(PLAYER_ID)
                .setReason(AdventureComponent.newBuilder().setJson(" "))
                .build();

        nats.deliver(KICK_SUBSCRIPTION, kickMessage(PLAYER_ID, request));

        assertEquals(List.of(new Kick(PLAYER_UUID, Component.empty())), proxyPlayers.kicks);
        assertEquals(List.of(true), publishedKickResults(nats));
    }

    @Test
    void kickRequestWithReason_kicksWithDeserializedComponent() throws Exception {
        FakeNatsConnection nats = FakeNatsConnection.withoutRequests();
        FakeProxyPlayers proxyPlayers = FakeProxyPlayers.connected(PLAYER_UUID);
        startCommandHandler(nats, proxyPlayers);
        KickPlayerRequest request = KickPlayerRequest.newBuilder()
                .setPlayerId(PLAYER_ID)
                .setReason(AdventureComponent.newBuilder().setJson("{\"text\":\"Maintenance\"}"))
                .build();

        nats.deliver(KICK_SUBSCRIPTION, kickMessage(PLAYER_ID, request));

        assertEquals(List.of(new Kick(PLAYER_UUID, Component.text("Maintenance"))), proxyPlayers.kicks);
        assertEquals(List.of(true), publishedKickResults(nats));
    }

    @Test
    void connectRequestForPlayerOnAnotherProxy_isNotAnswered() throws Exception {
        FakeNatsConnection nats = FakeNatsConnection.withoutRequests();
        FakeProxyPlayers proxyPlayers = FakeProxyPlayers.withoutPlayers();
        startCommandHandler(nats, proxyPlayers);
        ConnectPlayerRequest request = ConnectPlayerRequest.newBuilder()
                .setPlayerId(PLAYER_ID)
                .setServerName("lobby-1")
                .build();

        nats.deliver(CONNECT_SUBSCRIPTION, connectMessage(PLAYER_ID, request));

        assertEquals(List.of(), proxyPlayers.connectRequests);
        assertEquals(List.of(), nats.published());
    }

    @Test
    void connectRequestForConnectedPlayer_answersWithTheConnectResult() throws Exception {
        FakeNatsConnection nats = FakeNatsConnection.withoutRequests();
        FakeProxyPlayers proxyPlayers = FakeProxyPlayers.connected(PLAYER_UUID);
        startCommandHandler(nats, proxyPlayers);
        ConnectPlayerRequest request = ConnectPlayerRequest.newBuilder()
                .setPlayerId(PLAYER_ID)
                .setServerName("lobby-1")
                .build();

        nats.deliver(CONNECT_SUBSCRIPTION, connectMessage(PLAYER_ID, request));

        assertEquals(List.of(PLAYER_UUID), proxyPlayers.connectRequests);
        assertEquals(List.of(ConnectResult.CONNECT_RESULT_SUCCESS), publishedConnectResults(nats));
    }

    @Test
    void kickRequest_takesThePlayerFromTheRequestBody() throws Exception {
        FakeNatsConnection nats = FakeNatsConnection.withoutRequests();
        FakeProxyPlayers proxyPlayers = FakeProxyPlayers.connected(PLAYER_UUID, OTHER_PLAYER_UUID);
        startCommandHandler(nats, proxyPlayers);
        KickPlayerRequest request = KickPlayerRequest.newBuilder().setPlayerId(PLAYER_ID).build();

        nats.deliver(KICK_SUBSCRIPTION, kickMessage(OTHER_PLAYER_ID, request));

        assertEquals(List.of(new Kick(PLAYER_UUID, Component.empty())), proxyPlayers.kicks);
    }

    @Test
    void connectRequest_takesThePlayerFromTheRequestBody() throws Exception {
        FakeNatsConnection nats = FakeNatsConnection.withoutRequests();
        FakeProxyPlayers proxyPlayers = FakeProxyPlayers.connected(PLAYER_UUID, OTHER_PLAYER_UUID);
        startCommandHandler(nats, proxyPlayers);
        ConnectPlayerRequest request = ConnectPlayerRequest.newBuilder()
                .setPlayerId(PLAYER_ID)
                .setServerName("lobby-1")
                .build();

        nats.deliver(CONNECT_SUBSCRIPTION, connectMessage(OTHER_PLAYER_ID, request));

        assertEquals(List.of(PLAYER_UUID), proxyPlayers.connectRequests);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "not-a-uuid"})
    void kickRequestWithoutValidPlayerId_answersFailure(String playerId) throws Exception {
        FakeNatsConnection nats = FakeNatsConnection.withoutRequests();
        FakeProxyPlayers proxyPlayers = FakeProxyPlayers.connected(PLAYER_UUID);
        startCommandHandler(nats, proxyPlayers);
        KickPlayerRequest request = KickPlayerRequest.newBuilder().setPlayerId(playerId).build();

        nats.deliver(KICK_SUBSCRIPTION, kickMessage(PLAYER_ID, request));

        assertEquals(List.of(), proxyPlayers.kicks);
        assertEquals(List.of(false), publishedKickResults(nats));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "not-a-uuid"})
    void connectRequestWithoutValidPlayerId_answersConnectionFailed(String playerId) throws Exception {
        FakeNatsConnection nats = FakeNatsConnection.withoutRequests();
        FakeProxyPlayers proxyPlayers = FakeProxyPlayers.connected(PLAYER_UUID);
        startCommandHandler(nats, proxyPlayers);
        ConnectPlayerRequest request = ConnectPlayerRequest.newBuilder()
                .setPlayerId(playerId)
                .setServerName("lobby-1")
                .build();

        nats.deliver(CONNECT_SUBSCRIPTION, connectMessage(PLAYER_ID, request));

        assertEquals(List.of(), proxyPlayers.connectRequests);
        assertEquals(List.of(ConnectResult.CONNECT_RESULT_FAILED), publishedConnectResults(nats));
    }

    private static void startCommandHandler(FakeNatsConnection nats, FakeProxyPlayers proxyPlayers) {
        // Runs the kick on the calling thread, so the reply is published before deliver returns.
        PlayerCommandHandler commandHandler = new PlayerCommandHandler(nats.connection(), NETWORK_ID, Runnable::run,
                unusedServerApi(), proxyPlayers);
        commandHandler.start();
    }

    private static ServerApi unusedServerApi() {
        return (ServerApi) Proxy.newProxyInstance(
                ServerApi.class.getClassLoader(), new Class<?>[]{ServerApi.class}, (proxy, method, args) -> {
                    throw new AssertionError("Unexpected server API call: " + method.getName());
                });
    }

    private static Message kickMessage(String subjectPlayerId, KickPlayerRequest request) {
        return NatsMessage.builder()
                .subject(NETWORK_ID + ".player." + subjectPlayerId + ".kick")
                .replyTo("kick-reply")
                .data(request.toByteArray())
                .build();
    }

    private static Message connectMessage(String subjectPlayerId, ConnectPlayerRequest request) {
        return NatsMessage.builder()
                .subject(NETWORK_ID + ".player." + subjectPlayerId + ".connect")
                .replyTo("connect-reply")
                .data(request.toByteArray())
                .build();
    }

    private static List<Boolean> publishedKickResults(FakeNatsConnection nats) throws Exception {
        List<Boolean> results = new ArrayList<>();
        for (Message message : nats.published()) {
            assertEquals("kick-reply", message.getSubject());
            KickPlayerResponse response = KickPlayerResponse.parseFrom(message.getData());
            results.add(response.getSuccess());
        }
        return results;
    }

    private static List<ConnectResult> publishedConnectResults(FakeNatsConnection nats) throws Exception {
        List<ConnectResult> results = new ArrayList<>();
        for (Message message : nats.published()) {
            assertEquals("connect-reply", message.getSubject());
            ConnectPlayerResponse response = ConnectPlayerResponse.parseFrom(message.getData());
            results.add(response.getResult());
        }
        return results;
    }

    private record Kick(UUID playerId, Component reason) {
    }

    private static final class FakeProxyPlayers implements ProxyPlayers {

        private final Set<UUID> players;
        private final List<Kick> kicks = new ArrayList<>();
        private final List<UUID> connectRequests = new ArrayList<>();

        private FakeProxyPlayers(Set<UUID> players) {
            this.players = players;
        }

        static FakeProxyPlayers withoutPlayers() {
            return new FakeProxyPlayers(Set.of());
        }

        static FakeProxyPlayers connected(UUID... playerIds) {
            return new FakeProxyPlayers(Set.of(playerIds));
        }

        @Override
        public Optional<ProxyPlayer> findPlayer(UUID playerId) {
            if (!players.contains(playerId)) {
                return Optional.empty();
            }
            return Optional.of(new FakeProxyPlayer(playerId));
        }

        private final class FakeProxyPlayer implements ProxyPlayer {

            private final UUID playerId;

            private FakeProxyPlayer(UUID playerId) {
                this.playerId = playerId;
            }

            @Override
            public void kick(Component reason) {
                kicks.add(new Kick(playerId, reason));
            }

            @Override
            public CompletableFuture<CloudPlayer.ConnectResult> connect(ConnectTarget target) {
                connectRequests.add(playerId);
                return CompletableFuture.completedFuture(CloudPlayer.ConnectResult.SUCCESS);
            }
        }
    }
}
