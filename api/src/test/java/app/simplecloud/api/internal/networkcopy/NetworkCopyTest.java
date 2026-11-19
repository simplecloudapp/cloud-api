package app.simplecloud.api.internal.networkcopy;

import app.simplecloud.api.internal.nats.FakeNatsConnection;
import app.simplecloud.api.web.models.ModelsServerSummary;
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
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkCopyTest {

    private static final String NETWORK_ID = "0d000000-0000-4000-8000-000000000001";
    private static final String FIRST_SERVER = "00000000-0000-4000-8000-000000000201";
    private static final String SECOND_SERVER = "00000000-0000-4000-8000-000000000202";

    // Close to the end of the long range, so the tests of both limits cross the wrap, as System.nanoTime may.
    private final AtomicLong nanoTime = new AtomicLong(Long.MAX_VALUE - Duration.ofSeconds(10).toNanos());
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
        copy = new NetworkCopy(new OkHttpClient(), controllerUrl(), NETWORK_ID, "network-password", nanoTime::get);
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

        advanceTime(Duration.ofMinutes(30).minusMillis(1));
        copy.onPulse(pulse(1, 5));
        assertEquals(1, controller.getRequestCount());

        advanceTime(Duration.ofMillis(1));
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

        advanceTime(Duration.ofSeconds(15));
        assertNotNull(copy.contents());

        advanceTime(Duration.ofMillis(1));
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
        NetworkCopy copyOfOtherId = new NetworkCopy(new OkHttpClient(), controllerUrl(), "lobby-network", "", nanoTime::get);

        copyOfOtherId.start(nats.connection());

        assertEquals(Set.of(), nats.subscribedSubjects());
    }

    @Test
    void pulseOnTheLowerCaseSubjectReachesACopyOfAnUpperCaseNetworkId() throws InterruptedException {
        FakeNatsConnection nats = FakeNatsConnection.withoutRequests();
        NetworkCopy upperCaseCopy = new NetworkCopy(new OkHttpClient(), controllerUrl(), NETWORK_ID.toUpperCase(), "", nanoTime::get);
        upperCaseCopy.start(nats.connection());
        String subject = NETWORK_ID + ".state.v1.pulse";

        nats.deliver(subject, NatsMessage.builder().subject(subject).data(pulse(1, 5).toByteArray()).build());

        assertNotNull(upperCaseCopy.contents());
    }

    @Test
    void serversOfACopyOfAnUpperCaseNetworkIdShowTheLowerCaseId() {
        NetworkCopy upperCaseCopy = new NetworkCopy(new OkHttpClient(), controllerUrl(), NETWORK_ID.toUpperCase(), "", nanoTime::get);

        upperCaseCopy.onPulse(pulse(1, 5));

        NetworkContents network = upperCaseCopy.contents();
        ModelsServerSummary server = network.serverById(FIRST_SERVER);
        assertEquals(NETWORK_ID, server.getNetworkId());
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
    void ownWriteWithoutRevisionHeadersDoesNotWait() {
        copy.onPulse(pulse(1, 5));
        Map<String, List<String>> noHeaders = Map.of();

        assertTimeout(Duration.ofSeconds(1), () -> copy.waitForOwnWrite(noHeaders));
    }

    @Test
    void ownWriteDoesNotWaitWhileReadsGoToTheController() {
        Map<String, List<String>> headers = Map.of(
                "x-sc-state-epoch", List.of("1"),
                "x-sc-state-revision", List.of("6")
        );

        assertTimeout(Duration.ofSeconds(1), () -> copy.waitForOwnWrite(headers));
    }

    @Test
    @Timeout(10)
    void ownWriteGivesUpAfterThreeSecondsWhenTheCopyNeverGetsTheWrite() {
        copy.onPulse(pulse(1, 5));
        Map<String, List<String>> headers = Map.of(
                "x-sc-state-epoch", List.of("1"),
                "x-sc-state-revision", List.of("6")
        );
        Duration shortestWait = Duration.ofSeconds(3);
        Duration longestWait = Duration.ofSeconds(5);
        long startNanos = System.nanoTime();

        copy.waitForOwnWrite(headers);

        Duration waited = Duration.ofNanos(System.nanoTime() - startNanos);
        assertTrue(waited.compareTo(shortestWait) >= 0, "waited " + waited);
        assertTrue(waited.compareTo(longestWait) <= 0, "waited " + waited);
    }

    @Test
    void closeReturnsNormallyWhenTheNatsConnectionIsAlreadyClosed() {
        FakeNatsConnection nats = FakeNatsConnection.withoutRequests();
        copy.start(nats.connection());
        nats.close();

        assertDoesNotThrow(copy::close);
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

    private void advanceTime(Duration duration) {
        nanoTime.addAndGet(duration.toNanos());
    }
}
