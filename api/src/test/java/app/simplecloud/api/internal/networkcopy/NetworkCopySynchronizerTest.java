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

class NetworkCopySynchronizerTest {

    private static final String NETWORK_ID = "0d000000-0000-4000-8000-000000000001";
    private static final String FIRST_SERVER_ID = "00000000-0000-4000-8000-000000000201";
    private static final String SECOND_SERVER_ID = "00000000-0000-4000-8000-000000000202";

    // Close to the end of the long range, so the tests of both limits cross the wrap, as System.nanoTime may.
    private final AtomicLong nanoTime = new AtomicLong(Long.MAX_VALUE - Duration.ofSeconds(10).toNanos());
    private MockWebServer controllerHttp;
    private volatile NetworkState servedSnapshot = buildSnapshot(1, 5, FIRST_SERVER_ID);
    private volatile int responseCode = 200;
    private NetworkCopySynchronizer synchronizer;

    @BeforeEach
    void startControllerAndCreateSynchronizer() throws IOException {
        controllerHttp = new MockWebServer();
        controllerHttp.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                Buffer snapshotBody = new Buffer().write(servedSnapshot.toByteArray());
                return new MockResponse().setResponseCode(responseCode).setBody(snapshotBody);
            }
        });
        controllerHttp.start();
        synchronizer = new NetworkCopySynchronizer(new OkHttpClient(), getControllerUrl(), NETWORK_ID, "network-password", nanoTime::get);
    }

    @AfterEach
    void stopController() throws IOException {
        controllerHttp.close();
    }

    @Test
    void readsGoToTheControllerUntilTheFirstPulseLoads() throws InterruptedException {
        assertNull(synchronizer.currentContents());

        synchronizer.handlePulse(buildPulse(1, 5));

        assertNotNull(synchronizer.currentContents().findServerSummaryById(FIRST_SERVER_ID));
        RecordedRequest loadRequest = controllerHttp.takeRequest();
        assertEquals("/v0/network-state", loadRequest.getPath());
        assertEquals(NETWORK_ID, loadRequest.getHeader("X-Network-ID"));
        assertEquals("network-password", loadRequest.getHeader("X-Network-Credential"));
        assertEquals("application/x-protobuf", loadRequest.getHeader("Accept"));
    }

    @Test
    void pulseAtTheCopysRevisionOnlyTakesItsMetrics() {
        synchronizer.handlePulse(buildPulse(1, 5));
        StateServerMetrics metrics = StateServerMetrics.newBuilder()
                .setServerId(toUuidBytes(FIRST_SERVER_ID))
                .setPlayerCount(6)
                .build();

        synchronizer.handlePulse(buildPulse(1, 5).toBuilder().addMetrics(metrics).build());

        assertEquals(1, controllerHttp.getRequestCount());
        assertEquals(6, synchronizer.currentContents().findServerSummaryById(FIRST_SERVER_ID).getPlayerCount());
    }

    @Test
    void pulseWithANewerRevisionLoads() {
        synchronizer.handlePulse(buildPulse(1, 5));
        servedSnapshot = buildSnapshot(1, 6, FIRST_SERVER_ID, SECOND_SERVER_ID);

        synchronizer.handlePulse(buildPulse(1, 6));

        assertEquals(2, controllerHttp.getRequestCount());
        assertNotNull(synchronizer.currentContents().findServerSummaryById(SECOND_SERVER_ID));
    }

    @Test
    void pulseWithAnotherEpochLoads() {
        synchronizer.handlePulse(buildPulse(1, 5));
        servedSnapshot = buildSnapshot(2, 5, SECOND_SERVER_ID);

        synchronizer.handlePulse(buildPulse(2, 5));

        assertEquals(2, controllerHttp.getRequestCount());
        assertEquals(2, synchronizer.currentContents().epoch());
    }

    @Test
    void pulseAtTheCopysRevisionLoadsAgainOnceTheLastLoadIsThirtyMinutesOld() {
        synchronizer.handlePulse(buildPulse(1, 5));

        advanceTime(Duration.ofMinutes(30).minusMillis(1));
        synchronizer.handlePulse(buildPulse(1, 5));
        assertEquals(1, controllerHttp.getRequestCount());

        advanceTime(Duration.ofMillis(1));
        synchronizer.handlePulse(buildPulse(1, 5));
        assertEquals(2, controllerHttp.getRequestCount());
    }

    @Test
    void batchThatFitsIsApplied() {
        synchronizer.handlePulse(buildPulse(1, 5));

        synchronizer.handleBatch(createBatchBuilder(1, 5, 6).addServers(buildStateServer(SECOND_SERVER_ID)).build());

        assertEquals(6, synchronizer.currentContents().revision());
        assertNotNull(synchronizer.currentContents().findServerSummaryById(SECOND_SERVER_ID));
    }

    @Test
    void batchThatStartsAfterTheCopyMakesItForgetItselfUntilTheNextPulseLoads() {
        synchronizer.handlePulse(buildPulse(1, 5));

        synchronizer.handleBatch(createBatchBuilder(1, 6, 7).build());
        assertNull(synchronizer.currentContents());

        synchronizer.handlePulse(buildPulse(1, 5));
        assertEquals(2, controllerHttp.getRequestCount());
        assertNotNull(synchronizer.currentContents());
    }

    @Test
    void batchOfAnotherEpochMakesTheCopyForgetItself() {
        synchronizer.handlePulse(buildPulse(1, 5));

        synchronizer.handleBatch(createBatchBuilder(2, 5, 6).build());

        assertNull(synchronizer.currentContents());
    }

    @Test
    void batchTheCopyAlreadyHoldsIsIgnored() {
        synchronizer.handlePulse(buildPulse(1, 5));

        synchronizer.handleBatch(createBatchBuilder(1, 3, 5).addDeletedServerIds(toUuidBytes(FIRST_SERVER_ID)).build());

        assertNotNull(synchronizer.currentContents().findServerSummaryById(FIRST_SERVER_ID));
    }

    @Test
    void readsGoToTheControllerOnceNoPulseCameFor15Seconds() {
        synchronizer.handlePulse(buildPulse(1, 5));

        advanceTime(Duration.ofSeconds(15));
        assertNotNull(synchronizer.currentContents());

        advanceTime(Duration.ofMillis(1));
        assertNull(synchronizer.currentContents());

        synchronizer.handlePulse(buildPulse(1, 5));
        assertNotNull(synchronizer.currentContents());
    }

    @Test
    void pulseThatTurnsAnswersOffForgetsTheCopyAndNeverLoads() {
        synchronizer.handlePulse(buildPulse(1, 5));
        StatePulse answersOffPulse = buildPulse(1, 5).toBuilder().setViewsMayAnswer(false).build();

        synchronizer.handlePulse(answersOffPulse);
        synchronizer.handlePulse(answersOffPulse);

        assertNull(synchronizer.currentContents());
        assertEquals(1, controllerHttp.getRequestCount());
    }

    @Test
    void snapshotThatCannotBeReadIsForgottenAndLoadedAgainOnTheNextPulse() {
        StateServerGroup unreadableStateServerGroup = StateServerGroup.newBuilder()
                .setId(toUuidBytes("0a000000-0000-4000-8000-000000000301"))
                .setSummaryJson(ByteString.copyFromUtf8("{not json"))
                .build();
        servedSnapshot = buildSnapshot(1, 5, FIRST_SERVER_ID).toBuilder().addServerGroups(unreadableStateServerGroup).build();

        synchronizer.handlePulse(buildPulse(1, 5));
        assertNull(synchronizer.currentContents());

        servedSnapshot = buildSnapshot(1, 5, FIRST_SERVER_ID);
        synchronizer.handlePulse(buildPulse(1, 5));
        assertNotNull(synchronizer.currentContents());
    }

    @Test
    void refusedLoadIsNeverTriedAgain() {
        responseCode = 403;

        synchronizer.handlePulse(buildPulse(1, 5));
        synchronizer.handlePulse(buildPulse(1, 5));

        assertNull(synchronizer.currentContents());
        assertEquals(1, controllerHttp.getRequestCount());
    }

    @Test
    void networkIdThatIsNoUuidNeverSubscribes() {
        FakeNatsConnection natsConnection = FakeNatsConnection.withoutRequests();
        NetworkCopySynchronizer synchronizerWithNonUuidId = new NetworkCopySynchronizer(new OkHttpClient(), getControllerUrl(), "lobby-network", "", nanoTime::get);

        synchronizerWithNonUuidId.start(natsConnection.connection());

        assertEquals(Set.of(), natsConnection.subscribedSubjects());
    }

    @Test
    void pulseOnTheLowerCaseSubjectReachesASynchronizerOfAnUpperCaseNetworkId() throws InterruptedException {
        FakeNatsConnection natsConnection = FakeNatsConnection.withoutRequests();
        NetworkCopySynchronizer upperCaseSynchronizer = new NetworkCopySynchronizer(new OkHttpClient(), getControllerUrl(), NETWORK_ID.toUpperCase(), "", nanoTime::get);
        upperCaseSynchronizer.start(natsConnection.connection());
        String subject = NETWORK_ID + ".state.v1.pulse";

        natsConnection.deliver(subject, NatsMessage.builder().subject(subject).data(buildPulse(1, 5).toByteArray()).build());

        assertNotNull(upperCaseSynchronizer.currentContents());
    }

    @Test
    void serversOfACopyOfAnUpperCaseNetworkIdShowTheLowerCaseId() {
        NetworkCopySynchronizer upperCaseSynchronizer = new NetworkCopySynchronizer(new OkHttpClient(), getControllerUrl(), NETWORK_ID.toUpperCase(), "", nanoTime::get);

        upperCaseSynchronizer.handlePulse(buildPulse(1, 5));

        NetworkContents contents = upperCaseSynchronizer.currentContents();
        ModelsServerSummary summary = contents.findServerSummaryById(FIRST_SERVER_ID);
        assertEquals(NETWORK_ID, summary.getNetworkId());
    }

    @Test
    void ownWriteWaitsUntilABatchBringsTheWrite() {
        synchronizer.handlePulse(buildPulse(1, 5));
        StateBatch writeBatch = createBatchBuilder(1, 5, 6).addServers(buildStateServer(SECOND_SERVER_ID)).build();
        Thread.ofVirtual().start(() -> applyLater(writeBatch));
        Map<String, List<String>> headers = Map.of(
                "x-sc-state-epoch", List.of("1"),
                "x-sc-state-revision", List.of("6")
        );

        synchronizer.waitForOwnWrite(headers);

        assertEquals(6, synchronizer.currentContents().revision());
    }

    @Test
    void ownWriteWithoutRevisionHeadersDoesNotWait() {
        synchronizer.handlePulse(buildPulse(1, 5));
        Map<String, List<String>> noHeaders = Map.of();

        assertTimeout(Duration.ofSeconds(1), () -> synchronizer.waitForOwnWrite(noHeaders));
    }

    @Test
    void ownWriteDoesNotWaitWhileReadsGoToTheController() {
        Map<String, List<String>> headers = Map.of(
                "x-sc-state-epoch", List.of("1"),
                "x-sc-state-revision", List.of("6")
        );

        assertTimeout(Duration.ofSeconds(1), () -> synchronizer.waitForOwnWrite(headers));
    }

    @Test
    @Timeout(10)
    void ownWriteGivesUpAfterThreeSecondsWhenTheCopyNeverGetsTheWrite() {
        synchronizer.handlePulse(buildPulse(1, 5));
        Map<String, List<String>> headers = Map.of(
                "x-sc-state-epoch", List.of("1"),
                "x-sc-state-revision", List.of("6")
        );
        Duration shortestWait = Duration.ofSeconds(3);
        Duration longestWait = Duration.ofSeconds(5);
        long startNanos = System.nanoTime();

        synchronizer.waitForOwnWrite(headers);

        Duration waited = Duration.ofNanos(System.nanoTime() - startNanos);
        assertTrue(waited.compareTo(shortestWait) >= 0, "waited " + waited);
        assertTrue(waited.compareTo(longestWait) <= 0, "waited " + waited);
    }

    @Test
    void closeReturnsNormallyWhenTheNatsConnectionIsAlreadyClosed() {
        FakeNatsConnection natsConnection = FakeNatsConnection.withoutRequests();
        synchronizer.start(natsConnection.connection());
        natsConnection.close();

        assertDoesNotThrow(synchronizer::close);
    }

    @Test
    void neverStartedSynchronizerNeverHasContents() {
        assertNull(NetworkCopySynchronizer.createNeverStarted().currentContents());
    }

    private void applyLater(StateBatch batch) {
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            return;
        }
        synchronizer.handleBatch(batch);
    }

    private String getControllerUrl() {
        return "http://" + controllerHttp.getHostName() + ":" + controllerHttp.getPort();
    }

    private static StatePulse buildPulse(long epoch, long revision) {
        return StatePulse.newBuilder().setEpoch(epoch).setRevision(revision).setViewsMayAnswer(true).build();
    }

    private static StateBatch.Builder createBatchBuilder(long epoch, long baseRevision, long revision) {
        return StateBatch.newBuilder().setEpoch(epoch).setBaseRevision(baseRevision).setRevision(revision);
    }

    private static NetworkState buildSnapshot(long epoch, long revision, String... serverIds) {
        NetworkState.Builder snapshot = NetworkState.newBuilder().setEpoch(epoch).setRevision(revision);
        for (String serverId : serverIds) {
            snapshot.addServers(buildStateServer(serverId));
        }
        return snapshot.build();
    }

    private static StateServer buildStateServer(String id) {
        return StateServer.newBuilder()
                .setId(toUuidBytes(id))
                .setNumericalId(1)
                .setState("AVAILABLE")
                .setPropertiesJson(ByteString.copyFromUtf8("{}"))
                .build();
    }

    private static ByteString toUuidBytes(String id) {
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
