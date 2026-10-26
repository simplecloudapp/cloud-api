package app.simplecloud.api.internal.integration.player;

import build.buf.gen.simplecloud.player.v2.PlayerDisconnectRequest;
import build.buf.gen.simplecloud.player.v2.PlayerDisconnectResponse;
import build.buf.gen.simplecloud.player.v2.PlayerLoginResponse;
import build.buf.gen.simplecloud.player.v2.PlayerServerSwitchRequest;
import build.buf.gen.simplecloud.player.v2.PlayerServerSwitchResponse;
import com.google.protobuf.InvalidProtocolBufferException;
import io.nats.client.Connection;
import io.nats.client.Message;
import io.nats.client.impl.NatsMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.lang.reflect.Proxy;
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
            return message(operation.response(true));
        });

        assertNull(operation.invoke(reporter).join());
        assertEquals(1, requests.get());
    }

    @Test
    void alreadyOfflineDisconnectCompletesNormally() {
        // Controller's idempotent reply has success=true and no session duration.
        PlayerSessionReporter reporter = reporterAnswering((subject, data, timeout) -> message(
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
            return message(operation.response(false));
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
        PlayerSessionReporter reporter = reporterAnswering((subject, data, timeout) -> message(new byte[]{(byte) 0xff}));

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
        PlayerSessionReporter reporter = reporterAnswering((subject, data, timeout) -> message(
                PlayerLoginResponse.newBuilder().setSuccess(false).setErrorMessage("login rejected").build().toByteArray()
        ));

        LoginResult result = reporter.login(PLAYER_UUID, "Player", "Player", "proxy-1", "hash",
                "en_US", 1, true, null).join();

        assertFalse(result.isSuccess());
        assertEquals("login rejected", result.getErrorMessage());
    }

    private static CompletionException assertFailure(Operation operation, CompletableFuture<Void> future) {
        CompletionException failure = assertThrows(CompletionException.class, future::join);
        assertTrue(failure.getMessage().contains(PLAYER_ID));
        assertTrue(failure.getMessage().contains(operation.subjectSuffix));
        return failure;
    }

    private static Message message(byte[] data) {
        return NatsMessage.builder().subject("reply").data(data).build();
    }

    private static PlayerSessionReporter reporterAnswering(RequestHandler handler) {
        Connection connection = (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(), new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                    if (method.getName().equals("request") && args.length == 3) {
                        return handler.request((String) args[0], (byte[]) args[1], (Duration) args[2]);
                    }
                    throw new AssertionError("Unexpected connection call: " + method.getName());
                });
        return new PlayerSessionReporter(connection, NETWORK_ID, ForkJoinPool.commonPool());
    }

    @FunctionalInterface
    private interface RequestHandler {
        Message request(String subject, byte[] data, Duration timeout) throws Exception;
    }
}
