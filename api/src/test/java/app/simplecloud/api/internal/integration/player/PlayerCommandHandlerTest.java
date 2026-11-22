package app.simplecloud.api.internal.integration.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.simplecloud.api.internal.player.PlayerApiImpl;
import app.simplecloud.api.player.CloudPlayer;
import app.simplecloud.api.proxy.ProxyServerNames;
import app.simplecloud.api.support.NatsTest;
import app.simplecloud.api.support.TestContexts;
import app.simplecloud.api.support.TestNats;
import build.buf.gen.simplecloud.adventure.v1.AdventureComponent;
import build.buf.gen.simplecloud.player.v2.ConnectPlayerRequest;
import build.buf.gen.simplecloud.player.v2.ConnectPlayerResponse;
import build.buf.gen.simplecloud.player.v2.ConnectResult;
import build.buf.gen.simplecloud.player.v2.KickPlayerRequest;
import build.buf.gen.simplecloud.player.v2.KickPlayerResponse;
import io.nats.client.Connection;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@NatsTest
class PlayerCommandHandlerTest {

    private static final UUID PLAYER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private final String networkId = TestNats.randomNetworkId();
    private final BlockingQueue<String> connects = new LinkedBlockingQueue<>();
    private final BlockingQueue<Component> kicks = new LinkedBlockingQueue<>();
    private Connection proxyConnection;
    private Connection controller;
    private PlayerCommandHandler handler;

    @BeforeEach
    void startProxy() throws Exception {
        proxyConnection = TestNats.connect();
        controller = TestNats.connect();
        ProxyPlayer player = new ProxyPlayer() {
            @Override
            public void kick(Component reason) {
                kicks.add(reason);
            }

            @Override
            public CompletableFuture<CloudPlayer.ConnectResult> connect(String proxyServerName) {
                connects.add(proxyServerName);
                return CompletableFuture.completedFuture(CloudPlayer.ConnectResult.SUCCESS);
            }
        };
        handler = new PlayerCommandHandler(
                proxyConnection,
                networkId,
                Runnable::run,
                playerId -> PLAYER_ID.equals(playerId) ? Optional.of(player) : Optional.empty());
        handler.start();
        proxyConnection.flush(TestNats.TIMEOUT);
    }

    @AfterEach
    void stopProxy() throws InterruptedException {
        handler.stop();
        proxyConnection.close();
        controller.close();
    }

    @Test
    void connectDirectReachesThePlayersProxy() {
        PlayerApiImpl api = new PlayerApiImpl(TestContexts.forNetwork(networkId), controller);

        CloudPlayer.ConnectResult result =
                api.connectDirect(PLAYER_ID, "lobby-1").join();

        assertEquals(CloudPlayer.ConnectResult.SUCCESS, result);
        assertEquals("lobby-1", connects.poll());
    }

    @Test
    void connectDirectFailsWhenNoProxyKnowsThePlayer() throws Exception {
        handler.stop();
        proxyConnection.flush(TestNats.TIMEOUT);
        PlayerApiImpl api = new PlayerApiImpl(TestContexts.forNetwork(networkId), controller);

        assertEquals(
                CloudPlayer.ConnectResult.CONNECTION_FAILED,
                api.connectDirect(PLAYER_ID, "lobby-1").join());
    }

    @Test
    void serverIdIsResolvedToTheProxyServerName() throws Exception {
        try (ProxyServerNames.Registration ignored =
                ProxyServerNames.register(serverId -> Optional.of("lobby-" + serverId))) {
            ConnectPlayerResponse response = connect(ConnectPlayerRequest.newBuilder()
                    .setPlayerId(PLAYER_ID.toString())
                    .setServerId("abc"));

            assertEquals(ConnectResult.CONNECT_RESULT_SUCCESS, response.getResult());
            assertEquals("lobby-abc", connects.poll());
        }
    }

    @Test
    void unknownServerIdIsReportedAsNotFound() throws Exception {
        try (ProxyServerNames.Registration ignored = ProxyServerNames.register(serverId -> Optional.empty())) {
            ConnectPlayerResponse response = connect(ConnectPlayerRequest.newBuilder()
                    .setPlayerId(PLAYER_ID.toString())
                    .setServerId("missing"));

            assertEquals(ConnectResult.CONNECT_RESULT_SERVER_NOT_FOUND, response.getResult());
            assertTrue(connects.isEmpty());
        }
    }

    @Test
    void kickUsesTheReasonFromTheRequest() throws Exception {
        KickPlayerRequest request = KickPlayerRequest.newBuilder()
                .setPlayerId(PLAYER_ID.toString())
                .setReason(AdventureComponent.newBuilder().setJson("{\"text\":\"bye\"}"))
                .build();

        KickPlayerResponse response = KickPlayerResponse.parseFrom(
                TestNats.request(controller, networkId + ".player." + PLAYER_ID + ".kick", request.toByteArray())
                        .getData());

        assertTrue(response.getSuccess());
        assertEquals(Component.text("bye"), kicks.poll());
    }

    @Test
    void proxyWithoutThePlayerDoesNotReply() throws Exception {
        UUID otherPlayer = UUID.randomUUID();
        KickPlayerRequest request = KickPlayerRequest.newBuilder()
                .setPlayerId(otherPlayer.toString())
                .build();

        assertNull(controller.request(
                networkId + ".player." + otherPlayer + ".kick", request.toByteArray(), Duration.ofMillis(500)));
    }

    private ConnectPlayerResponse connect(ConnectPlayerRequest.Builder request) throws Exception {
        return ConnectPlayerResponse.parseFrom(TestNats.request(
                        controller,
                        networkId + ".player." + PLAYER_ID + ".connect",
                        request.build().toByteArray())
                .getData());
    }
}
