package app.simplecloud.api.internal.integration.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.simplecloud.api.support.NatsTest;
import app.simplecloud.api.support.TestNats;
import io.nats.client.Connection;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@NatsTest
class AdventureIntegrationTest {

    private static final UUID ONLINE_PLAYER = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private final String networkId = TestNats.randomNetworkId();
    private final BlockingQueue<Component> messages = new LinkedBlockingQueue<>();
    private Connection serverConnection;
    private Connection senderConnection;
    private AdventureIntegration integration;

    @BeforeEach
    void startServer() throws Exception {
        serverConnection = TestNats.connect();
        senderConnection = TestNats.connect();
        Audience player = new Audience() {
            @Override
            public void sendMessage(Component message) {
                messages.add(message);
            }
        };
        PlayerAudiences players = new PlayerAudiences() {
            @Override
            public void runForPlayer(UUID playerId, PlayerAction action) {
                if (ONLINE_PLAYER.equals(playerId)) {
                    action.run(playerId, player);
                }
            }

            @Override
            public void runForAllPlayers(PlayerAction action) {
                action.run(ONLINE_PLAYER, player);
            }
        };
        integration = new AdventureIntegration(serverConnection, networkId, players, "server-1", "lobby");
        integration.start();
        serverConnection.flush(TestNats.TIMEOUT);
    }

    @AfterEach
    void stopServer() throws InterruptedException {
        integration.stop();
        serverConnection.close();
        senderConnection.close();
    }

    @Test
    void messageToAPlayerReachesThatPlayer() throws InterruptedException {
        audiences().forPlayer(ONLINE_PLAYER).sendMessage(Component.text("hello"));

        assertEquals(Component.text("hello"), TestNats.next(messages));
    }

    @Test
    void groupBroadcastReachesPlayersOfThatGroupOnly() throws InterruptedException {
        audiences().forGroup("game").sendMessage(Component.text("other group"));
        audiences().forGroup("lobby").sendMessage(Component.text("own group"));

        assertEquals(Component.text("own group"), TestNats.next(messages));
        assertTrue(messages.isEmpty());
    }

    @Test
    void serverBroadcastReachesPlayersOfThatServerOnly() throws InterruptedException {
        audiences().forServer("server-2").sendMessage(Component.text("other server"));
        audiences().forServer("server-1").sendMessage(Component.text("own server"));

        assertEquals(Component.text("own server"), TestNats.next(messages));
        assertTrue(messages.isEmpty());
    }

    private RemoteAudience.Builder audiences() {
        return RemoteAudience.builder(senderConnection, networkId);
    }
}
