package app.simplecloud.api.internal.networkcopy;

import app.simplecloud.api.internal.nats.FakeNatsConnection;
import build.buf.gen.simplecloud.controller.v2.NetworkState;
import build.buf.gen.simplecloud.controller.v2.StateBatch;
import build.buf.gen.simplecloud.controller.v2.StatePulse;
import build.buf.gen.simplecloud.controller.v2.StateServer;
import build.buf.gen.simplecloud.controller.v2.StateServerGroup;
import build.buf.gen.simplecloud.controller.v2.StateServerMetrics;
import com.google.protobuf.ByteString;
import io.nats.client.impl.NatsMessage;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okio.Buffer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NetworkCopyTest {

    private static final String NETWORK_ID = "0d000000-0000-4000-8000-000000000001";
    private static final String FIRST_SERVER = "00000000-0000-4000-8000-000000000201";
    private static final String SECOND_SERVER = "00000000-0000-4000-8000-000000000202";

    private final TestClock clock = new TestClock();
    private MockWebServer controller;
    private volatile NetworkState servedState = state(1, 5, FIRST_SERVER);
    private volatile int answerCode = 200;
    private NetworkCopy copy;

    @BeforeEach
    void startController() throws IOException {
        controller = new MockWebServer();
        controller.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                Buffer body = new Buffer().write(servedState.toByteArray());
                return new MockResponse().setResponseCode(answerCode).setBody(body);
            }
        });
        controller.start();
        copy = new NetworkCopy(new OkHttpClient(), controllerUrl(), NETWORK_ID, "network-password", clock);
    }

    @AfterEach
    void stopController() throws IOException {
        controller.close();
    }

    @Test
    void readsGoToTheControllerUntilTheFirstPulseLoads() throws InterruptedException {
        assertNull(copy.contents());

        copy.onPulse(pulse(1, 5));

        assertNotNull(copy.contents().serverById(FIRST_SERVER));
        RecordedRequest load = controller.takeRequest();
        assertEquals("/v0/network-state", load.getPath());
        assertEquals(NETWORK_ID, load.getHeader("X-Network-ID"));
        assertEquals("network-password", load.getHeader("X-Network-Credential"));
        assertEquals("application/x-protobuf", load.getHeader("Accept"));
    }

    @Test
    void pulseAtTheCopysRevisionOnlyTakesItsMetrics() {
        copy.onPulse(pulse(1, 5));
        StateServerMetrics metrics = StateServerMetrics.newBuilder()
                .setServerId(uuidBytes(FIRST_SERVER))
                .setPlayerCount(6)
                .build();

        copy.onPulse(pulse(1, 5).toBuilder().addMetrics(metrics).build());

        assertEquals(1, controller.getRequestCount());
        assertEquals(6, copy.contents().serverById(FIRST_SERVER).getPlayerCount());
    }

    @Test
    void pulseWithANewerRevisionLoads() {
        copy.onPulse(pulse(1, 5));
        servedState = state(1, 6, FIRST_SERVER, SECOND_SERVER);

        copy.onPulse(pulse(1, 6));

        assertEquals(2, controller.getRequestCount());
        assertNotNull(copy.contents().serverById(SECOND_SERVER));
    }

    @Test
    void pulseWithAnotherEpochLoads() {
        copy.onPulse(pulse(1, 5));
        servedState = state(2, 5, SECOND_SERVER);

        copy.onPulse(pulse(2, 5));

        assertEquals(2, controller.getRequestCount());
        assertEquals(2, copy.contents().epoch());
    }

    @Test
    void pulseAtTheCopysRevisionLoadsAgainOnceTheLastLoadIsThirtyMinutesOld() {
        copy.onPulse(pulse(1, 5));

        clock.advance(Duration.ofMinutes(30).minusMillis(1));
        copy.onPulse(pulse(1, 5));
        assertEquals(1, controller.getRequestCount());

        clock.advance(Duration.ofMillis(1));
        copy.onPulse(pulse(1, 5));
        assertEquals(2, controller.getRequestCount());
    }

    @Test
    void batchThatFitsIsApplied() {
        copy.onPulse(pulse(1, 5));

        copy.onBatch(batch(1, 5, 6).addServers(server(SECOND_SERVER)).build());

        assertEquals(6, copy.contents().revision());
        assertNotNull(copy.contents().serverById(SECOND_SERVER));
    }

    @Test
    void batchThatStartsAfterTheCopyMakesItForgetItselfUntilTheNextPulseLoads() {
        copy.onPulse(pulse(1, 5));

        copy.onBatch(batch(1, 6, 7).build());
        assertNull(copy.contents());

        copy.onPulse(pulse(1, 5));
        assertEquals(2, controller.getRequestCount());
        assertNotNull(copy.contents());
    }

    @Test
    void batchOfAnotherEpochMakesTheCopyForgetItself() {
        copy.onPulse(pulse(1, 5));

        copy.onBatch(batch(2, 5, 6).build());

        assertNull(copy.contents());
    }

    @Test
    void batchTheCopyAlreadyHoldsIsIgnored() {
        copy.onPulse(pulse(1, 5));

        copy.onBatch(batch(1, 3, 5).addDeletedServerIds(uuidBytes(FIRST_SERVER)).build());

        assertNotNull(copy.contents().serverById(FIRST_SERVER));
    }

    @Test
    void readsGoToTheControllerOnceNoPulseCameFor15Seconds() {
        copy.onPulse(pulse(1, 5));

        clock.advance(Duration.ofSeconds(15));
        assertNotNull(copy.contents());

        clock.advance(Duration.ofMillis(1));
        assertNull(copy.contents());

        copy.onPulse(pulse(1, 5));
        assertNotNull(copy.contents());
    }

    @Test
    void pulseThatTurnsAnswersOffForgetsTheCopyAndNeverLoads() {
        copy.onPulse(pulse(1, 5));
        StatePulse answersOff = pulse(1, 5).toBuilder().setViewsMayAnswer(false).build();

        copy.onPulse(answersOff);
        copy.onPulse(answersOff);

        assertNull(copy.contents());
        assertEquals(1, controller.getRequestCount());
    }

    @Test
    void stateThatCannotBeReadIsForgottenAndLoadedAgainOnTheNextPulse() {
        StateServerGroup unreadableGroup = StateServerGroup.newBuilder()
                .setId(uuidBytes("0a000000-0000-4000-8000-000000000301"))
                .setSummaryJson(ByteString.copyFromUtf8("{not json"))
                .build();
        servedState = state(1, 5, FIRST_SERVER).toBuilder().addServerGroups(unreadableGroup).build();

        copy.onPulse(pulse(1, 5));
        assertNull(copy.contents());

        servedState = state(1, 5, FIRST_SERVER);
        copy.onPulse(pulse(1, 5));
        assertNotNull(copy.contents());
    }

    @Test
    void refusedLoadIsNeverTriedAgain() {
        answerCode = 403;

        copy.onPulse(pulse(1, 5));
        copy.onPulse(pulse(1, 5));

        assertNull(copy.contents());
        assertEquals(1, controller.getRequestCount());
    }

    @Test
    void networkIdThatIsNoUuidNeverSubscribes() {
        FakeNatsConnection nats = FakeNatsConnection.withoutRequests();
        NetworkCopy copyOfOtherId = new NetworkCopy(new OkHttpClient(), controllerUrl(), "lobby-network", "", clock);

        copyOfOtherId.start(nats.connection());

        NatsMessage message = NatsMessage.builder().subject("lobby-network.state.v1.pulse").data(new byte[0]).build();
        assertThrows(AssertionError.class, () -> nats.deliver("lobby-network.state.v1.pulse", message));
    }

    @Test
    void pulseOnTheLowerCaseSubjectReachesACopyOfAnUpperCaseNetworkId() throws InterruptedException {
        FakeNatsConnection nats = FakeNatsConnection.withoutRequests();
        NetworkCopy upperCaseCopy = new NetworkCopy(new OkHttpClient(), controllerUrl(), NETWORK_ID.toUpperCase(), "", clock);
        upperCaseCopy.start(nats.connection());
        String subject = NETWORK_ID + ".state.v1.pulse";

        nats.deliver(subject, NatsMessage.builder().subject(subject).data(pulse(1, 5).toByteArray()).build());

        assertNotNull(upperCaseCopy.contents());
    }

    @Test
    void ownWriteWaitsUntilABatchBringsTheWrite() {
        copy.onPulse(pulse(1, 5));
        StateBatch write = batch(1, 5, 6).addServers(server(SECOND_SERVER)).build();
        Thread.ofVirtual().start(() -> applyLater(write));
        Map<String, List<String>> headers = Map.of(
                "x-sc-state-epoch", List.of("1"),
                "x-sc-state-revision", List.of("6")
        );

        copy.waitForOwnWrite(headers);

        assertEquals(6, copy.contents().revision());
    }

    @Test
    void absentCopyNeverHasContents() {
        assertNull(NetworkCopy.absent().contents());
    }

    private void applyLater(StateBatch batch) {
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            return;
        }
        copy.onBatch(batch);
    }

    private String controllerUrl() {
        return "http://" + controller.getHostName() + ":" + controller.getPort();
    }

    private static StatePulse pulse(long epoch, long revision) {
        return StatePulse.newBuilder().setEpoch(epoch).setRevision(revision).setViewsMayAnswer(true).build();
    }

    private static StateBatch.Builder batch(long epoch, long baseRevision, long revision) {
        return StateBatch.newBuilder().setEpoch(epoch).setBaseRevision(baseRevision).setRevision(revision);
    }

    private static NetworkState state(long epoch, long revision, String... serverIds) {
        NetworkState.Builder state = NetworkState.newBuilder().setEpoch(epoch).setRevision(revision);
        for (String serverId : serverIds) {
            state.addServers(server(serverId));
        }
        return state.build();
    }

    private static StateServer server(String id) {
        return StateServer.newBuilder()
                .setId(uuidBytes(id))
                .setNumericalId(1)
                .setState("AVAILABLE")
                .setPropertiesJson(ByteString.copyFromUtf8("{}"))
                .build();
    }

    private static ByteString uuidBytes(String id) {
        UUID uuid = UUID.fromString(id);
        ByteBuffer bytes = ByteBuffer.allocate(16);
        bytes.putLong(uuid.getMostSignificantBits());
        bytes.putLong(uuid.getLeastSignificantBits());
        return ByteString.copyFrom(bytes.array());
    }

    private static final class TestClock extends Clock {
        private volatile Instant now = Instant.parse("2026-11-19T10:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
