package app.simplecloud.api.internal.integration.player;

import app.simplecloud.api.internal.nats.FakeNatsConnection;
import build.buf.gen.simplecloud.player.v2.PlayerDisconnectRequest;
import build.buf.gen.simplecloud.player.v2.PlayerDisconnectResponse;
import build.buf.gen.simplecloud.player.v2.PlayerLoginRequest;
import build.buf.gen.simplecloud.player.v2.PlayerLoginResponse;
import build.buf.gen.simplecloud.player.v2.PlayerServerSwitchRequest;
import build.buf.gen.simplecloud.player.v2.PlayerServerSwitchResponse;
import com.google.protobuf.InvalidProtocolBufferException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(5)
class PlayerSessionReporterTest {

    private static final String NETWORK_ID = "test-network";
    private static final String PLAYER_ID = "11111111-1111-1111-1111-111111111111";
    private static final UUID PLAYER_UUID = UUID.fromString(PLAYER_ID);

    enum Operation {
        DISCONNECT("disconnect"), SERVER_SWITCH("switch");

        final String subjectSuffix;

        Operation(String subjectSuffix) {
            this.subjectSuffix = subjectSuffix;
        }

        CompletableFuture<Void> invoke(PlayerSessionReporter reporter) {
            return this == DISCONNECT
                    ? reporter.disconnect(PLAYER_UUID)
                    : reporter.serverSwitch(PLAYER_UUID, "lobby-1");
        }

        byte[] response(boolean success) {
            return this == DISCONNECT
                    ? PlayerDisconnectResponse.newBuilder().setSuccess(success)
                            .setSessionDurationSeconds(success ? 42 : 0).build().toByteArray()
                    : PlayerServerSwitchResponse.newBuilder().setSuccess(success).build().toByteArray();
        }
    }

    @ParameterizedTest
    @EnumSource(Operation.class)
    void successfulReplyCompletesNormallyAndSendsExpectedRequest(Operation operation) {
        AtomicInteger requests = new AtomicInteger();
        PlayerSessionReporter reporter = reporterAnswering((subject, data, timeout) -> {
            requests.incrementAndGet();
            assertEquals(NETWORK_ID + ".player." + operation.subjectSuffix, subject);
            assertEquals(Duration.ofSeconds(30), timeout);
            if (operation == Operation.DISCONNECT) {
                assertEquals(PLAYER_ID, PlayerDisconnectRequest.parseFrom(data).getPlayerId());
            } else {
                PlayerServerSwitchRequest request = PlayerServerSwitchRequest.parseFrom(data);
                assertEquals(PLAYER_ID, request.getPlayerId());
                assertEquals("lobby-1", request.getNewServerName());
            }
            return FakeNatsConnection.reply(operation.response(true));
        });

        assertNull(operation.invoke(reporter).join());
        assertEquals(1, requests.get());
    }

    @Test
    void alreadyOfflineDisconnectCompletesNormally() {
        // Controller's idempotent reply has success=true and no session duration.
        PlayerSessionReporter reporter = reporterAnswering((subject, data, timeout) -> FakeNatsConnection.reply(
                PlayerDisconnectResponse.newBuilder().setSuccess(true).build().toByteArray()
        ));

        assertNull(reporter.disconnect(PLAYER_UUID).join());
    }

    @ParameterizedTest
    @EnumSource(Operation.class)
    void unsuccessfulReplyReachesTheProxyErrorCallback(Operation operation) {
        AtomicInteger requests = new AtomicInteger();
        PlayerSessionReporter reporter = reporterAnswering((subject, data, timeout) -> {
            requests.incrementAndGet();
            return FakeNatsConnection.reply(operation.response(false));
        });
        AtomicReference<Throwable> reportedFailure = new AtomicReference<>();
        CompletableFuture<Void> future = operation.invoke(reporter);

        // Both proxy listeners attach this callback to the returned future.
        future.exceptionally(error -> {
            reportedFailure.set(error);
            return null;
        }).join();

        assertNotNull(reportedFailure.get());
        CompletionException failure = assertFailure(operation, future);
        assertInstanceOf(IllegalStateException.class, failure.getCause());
        assertTrue(failure.getMessage().contains("rejected"));
        assertEquals(1, requests.get());
    }

    @ParameterizedTest
    @EnumSource(Operation.class)
    void missingReplyFailsAsTimeout(Operation operation) {
        PlayerSessionReporter reporter = reporterAnswering((subject, data, timeout) -> null);

        CompletionException failure = assertFailure(operation, operation.invoke(reporter));

        assertInstanceOf(TimeoutException.class, failure.getCause());
    }

    @ParameterizedTest
    @EnumSource(Operation.class)
    void malformedReplyPreservesDecodeFailure(Operation operation) {
        PlayerSessionReporter reporter = reporterAnswering((subject, data, timeout) -> FakeNatsConnection.reply(new byte[]{(byte) 0xff}));

        CompletionException failure = assertFailure(operation, operation.invoke(reporter));

        assertInstanceOf(InvalidProtocolBufferException.class, failure.getCause());
    }

    @ParameterizedTest
    @EnumSource(Operation.class)
    void transportFailurePreservesCause(Operation operation) {
        IllegalStateException transportFailure = new IllegalStateException("connection closed");
        PlayerSessionReporter reporter = reporterAnswering((subject, data, timeout) -> {
            throw transportFailure;
        });

        CompletionException failure = assertFailure(operation, operation.invoke(reporter));

        assertSame(transportFailure, failure.getCause());
    }

    @ParameterizedTest
    @EnumSource(Operation.class)
    void interruptionPreservesCause(Operation operation) {
        InterruptedException interruption = new InterruptedException("request interrupted");
        PlayerSessionReporter reporter = reporterAnswering((subject, data, timeout) -> {
            throw interruption;
        });

        CompletionException failure = assertFailure(operation, operation.invoke(reporter));

        assertSame(interruption, failure.getCause());
    }

    @Test
    void loginStillReturnsControllerFailureAsLoginResult() {
        PlayerSessionReporter reporter = reporterAnswering((subject, data, timeout) -> FakeNatsConnection.reply(
                PlayerLoginResponse.newBuilder().setSuccess(false).setErrorMessage("login rejected").build().toByteArray()
        ));

        PlayerLogin login = new PlayerLogin(PLAYER_UUID, "Player", "Player", "proxy-1", "hash", "en_US", 1, true, null);

        LoginResult result = reporter.login(login).join();

        assertFalse(result.isSuccess());
        assertEquals("login rejected", result.getErrorMessage());
    }

    @Test
    void loginSendsEveryFieldOfThePlayerLogin() {
        AtomicReference<PlayerLoginRequest> sentRequest = new AtomicReference<>();
        PlayerSessionReporter reporter = reporterAnswering((subject, data, timeout) -> {
            assertEquals(NETWORK_ID + ".player.login", subject);
            sentRequest.set(PlayerLoginRequest.parseFrom(data));
            return FakeNatsConnection.reply(PlayerLoginResponse.newBuilder().setSuccess(true).setSessionId("session-1").build().toByteArray());
        });
        PlayerLogin login = new PlayerLogin(PLAYER_UUID, "Player", "Display", "proxy-1", "hash", "de_DE", 767, true, "skin");

        LoginResult result = reporter.login(login).join();

        PlayerLoginRequest request = sentRequest.get();
        assertEquals("session-1", result.getSessionId());
        assertEquals(PLAYER_ID, request.getUniqueId());
        assertEquals("Player", request.getName());
        assertEquals("Display", request.getDisplayName());
        assertEquals("proxy-1", request.getConnectedProxyName());
        assertEquals("hash", request.getAddressHash());
        assertEquals("de_DE", request.getClientLanguage());
        assertEquals(767, request.getClientVersion());
        assertTrue(request.getOnlineMode());
        assertEquals("skin", request.getTexture());
    }

    @Test
    void loginWithoutTextureSendsNoTexture() {
        AtomicReference<PlayerLoginRequest> sentRequest = new AtomicReference<>();
        PlayerSessionReporter reporter = reporterAnswering((subject, data, timeout) -> {
            sentRequest.set(PlayerLoginRequest.parseFrom(data));
            return FakeNatsConnection.reply(PlayerLoginResponse.newBuilder().setSuccess(true).build().toByteArray());
        });
        PlayerLogin login = new PlayerLogin(PLAYER_UUID, "Player", "Player", "proxy-1", "hash", "en_US", 767, false, null);

        reporter.login(login).join();

        assertEquals("", sentRequest.get().getTexture());
    }

    private static CompletionException assertFailure(Operation operation, CompletableFuture<Void> future) {
        CompletionException failure = assertThrows(CompletionException.class, future::join);
        assertTrue(failure.getMessage().contains(PLAYER_ID));
        assertTrue(failure.getMessage().contains(operation.subjectSuffix));
        return failure;
    }

    private static PlayerSessionReporter reporterAnswering(FakeNatsConnection.RequestHandler requestHandler) {
        FakeNatsConnection nats = FakeNatsConnection.answering(requestHandler);
        return new PlayerSessionReporter(nats.connection(), NETWORK_ID, ForkJoinPool.commonPool());
    }
}
