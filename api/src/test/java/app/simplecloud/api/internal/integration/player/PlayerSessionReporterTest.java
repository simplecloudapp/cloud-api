package app.simplecloud.api.internal.integration.player;

import app.simplecloud.api.support.NatsTest;
import app.simplecloud.api.support.TestNats;
import build.buf.gen.simplecloud.player.v2.PlayerDisconnectResponse;
import build.buf.gen.simplecloud.player.v2.PlayerLoginRequest;
import build.buf.gen.simplecloud.player.v2.PlayerLoginResponse;
import io.nats.client.Connection;
import io.nats.client.MessageHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionException;
import java.util.concurrent.LinkedBlockingQueue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@NatsTest
class PlayerSessionReporterTest {

    private static final UUID PLAYER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private final String networkId = TestNats.randomNetworkId();
    private Connection proxyConnection;
    private Connection controller;
    private PlayerSessionReporter reporter;

    @BeforeEach
    void connect() throws Exception {
        proxyConnection = TestNats.connect();
        controller = TestNats.connect();
        reporter = new PlayerSessionReporter(proxyConnection, networkId, Runnable::run);
    }

    @AfterEach
    void close() throws InterruptedException {
        proxyConnection.close();
        controller.close();
    }

    @Test
    void loginReturnsTheSessionOfTheController() throws Exception {
        BlockingQueue<byte[]> requests = new LinkedBlockingQueue<>();
        answer("player.login", message -> {
            requests.add(message.getData());
            controller.publish(message.getReplyTo(),
                    PlayerLoginResponse.newBuilder().setSuccess(true).setSessionId("session-1").build().toByteArray());
        });

        LoginResult result = reporter.reportLogin(login()).join();

        assertTrue(result.success());
        assertEquals("session-1", result.sessionId());
        PlayerLoginRequest request = PlayerLoginRequest.parseFrom(TestNats.next(requests));
        assertEquals(PLAYER_ID.toString(), request.getUniqueId());
        assertEquals("Steve", request.getName());
        assertEquals("proxy-1", request.getConnectedProxyName());
    }

    @Test
    void rejectedLoginCarriesTheControllerMessage() throws Exception {
        answer("player.login", message -> controller.publish(message.getReplyTo(),
                PlayerLoginResponse.newBuilder().setSuccess(false).setErrorMessage("network is full").build().toByteArray()));

        LoginResult result = reporter.reportLogin(login()).join();

        assertFalse(result.success());
        assertEquals("network is full", result.errorMessage());
    }

    @Test
    void rejectedDisconnectFailsTheFuture() throws Exception {
        answer("player.disconnect", message -> controller.publish(message.getReplyTo(),
                PlayerDisconnectResponse.newBuilder().setSuccess(false).build().toByteArray()));

        CompletionException failure = assertThrows(CompletionException.class, () -> reporter.reportDisconnect(PLAYER_ID).join());

        assertInstanceOf(IllegalStateException.class, failure.getCause());
    }

    private void answer(String subject, MessageHandler handler) throws Exception {
        controller.createDispatcher(handler).subscribe(networkId + "." + subject);
        controller.flush(TestNats.TIMEOUT);
    }

    private static PlayerLogin login() {
        return new PlayerLogin(PLAYER_ID, "Steve", "Steve", "proxy-1", "hash", "en_us", 770, true, null);
    }
}
