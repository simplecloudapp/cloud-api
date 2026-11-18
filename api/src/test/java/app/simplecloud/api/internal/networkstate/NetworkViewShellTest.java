package app.simplecloud.api.internal.networkstate;

import app.simplecloud.api.CloudApiOptions;
import app.simplecloud.api.internal.nats.FakeNatsConnection;
import app.simplecloud.api.internal.web.ApiClients;
import app.simplecloud.api.web.ApiClient;
import build.buf.gen.simplecloud.controller.v2.NetworkState;
import com.google.protobuf.MessageLite;
import io.nats.client.Message;
import io.nats.client.impl.NatsMessage;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okio.Buffer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

import static app.simplecloud.api.internal.networkstate.StateItems.batch;
import static app.simplecloud.api.internal.networkstate.StateItems.network;
import static app.simplecloud.api.internal.networkstate.StateItems.pulse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkViewShellTest {

    private static final String NETWORK_ID = "0d000000-0000-4000-8000-000000000001";
    private static final String BATCH_SUBJECT = NETWORK_ID + ".state.v1.batch";
    private static final String PULSE_SUBJECT = NETWORK_ID + ".state.v1.pulse";
    private static final Duration WAIT_LIMIT = Duration.ofSeconds(5);
    /**
     * Two time ticks of 100 ms and some room.
     */
    private static final long QUIET_MILLIS = 300;

    private static final NetworkCopy EMPTY_COPY = new NetworkCopy(StateItems.EPOCH, 10, Map.of(), Map.of(), Map.of(),
            Map.of(), Map.of());

    private final FakeNatsConnection nats = FakeNatsConnection.withoutRequests();
    private final AtomicLong clockMillis = new AtomicLong();
    private final MockWebServer snapshotServer = new MockWebServer();
    private NetworkViewShell shell;

    @AfterEach
    void closeShell() throws IOException {
        if (shell != null) {
            shell.close();
        }
        snapshotServer.close();
    }

    @Test
    void batchesAndPulsesReachTheViewInTheOrderTheyArrived() throws InterruptedException {
        RecordingView view = RecordingView.withoutCopy();
        shell = startShell(view, unusedSnapshots());
        ViewInput firstBatch = new ViewInput.BatchReceived(batch(10, 11).build());
        ViewInput pulse = new ViewInput.PulseReceived(pulse(11));
        ViewInput secondBatch = new ViewInput.BatchReceived(batch(11, 12).build());

        deliver(BATCH_SUBJECT, batch(10, 11).build());
        deliver(PULSE_SUBJECT, pulse(11));
        deliver(BATCH_SUBJECT, batch(11, 12).build());

        List<ViewInput> messages = view.received().stream()
                .filter(NetworkViewShellTest::isMessage)
                .toList();
        assertEquals(List.of(firstBatch, pulse, secondBatch), messages);
    }

    @Test
    void subscriptionsCountAsActiveOnlyOnceTheFlushReturned() {
        RecordingView view = RecordingView.withoutCopy();
        shell = startShell(view, unusedSnapshots());
        nats.holdFlushes();

        shell.subscriptionsSent();
        sleepQuietly();
        boolean activeBeforeFlush = !view.received(ViewInput.SubscriptionsActive.class).isEmpty();
        nats.releaseFlushes();

        assertFalse(activeBeforeFlush, "the subscriptions are active only once the flush returned");
        awaitTrue(() -> !view.received(ViewInput.SubscriptionsActive.class).isEmpty(),
                "the view hears that the subscriptions are active after the flush");
    }

    @Test
    void aFlushThatReturnsAfterALossDoesNotMakeTheSubscriptionsActive() {
        RecordingView view = RecordingView.withoutCopy();
        shell = startShell(view, unusedSnapshots());
        nats.holdFlushes();

        shell.subscriptionsSent();
        awaitTrue(() -> nats.startedFlushes() == 1, "the shell flushes after the subscriptions were sent");
        shell.connectionLost();
        nats.releaseFlushes();
        awaitTrue(() -> nats.returnedFlushes() == 1, "the held flush returns");
        sleepQuietly();

        assertEquals(1, view.received(ViewInput.ConnectionLost.class).size());
        assertTrue(view.received(ViewInput.SubscriptionsActive.class).isEmpty(),
                "a flush from before the loss confirms nothing after it");
    }

    @Test
    void aReconnectReloadsTheView() throws InterruptedException, IOException {
        try (MockWebServer controller = new MockWebServer()) {
            controller.enqueue(snapshotResponse(network(10)));
            controller.enqueue(snapshotResponse(network(12)));
            controller.start();
            NetworkView view = NetworkViewMachine.start(index -> 0.0);
            shell = startShell(view, snapshotsFrom(controller));

            shell.subscriptionsSent();
            RecordedRequest firstLoad = controller.takeRequest(WAIT_LIMIT.toMillis(), TimeUnit.MILLISECONDS);
            awaitLiveAt(10);
            clockMillis.set(5_000);
            shell.connectionLost();
            boolean answersAfterLoss = shell.copyToAnswer().join().isPresent();
            shell.subscriptionsSent();
            RecordedRequest reload = controller.takeRequest(WAIT_LIMIT.toMillis(), TimeUnit.MILLISECONDS);

            assertNotNull(firstLoad, "the view loads a snapshot once the subscriptions are active");
            assertEquals("/v0/network-state", firstLoad.getRequestUrl().encodedPath(), firstLoad.getRequestLine());
            assertFalse(answersAfterLoss, "the view answers nothing once the connection is lost");
            assertNotNull(reload, "the view loads a snapshot again after the reconnect");
            assertEquals("/v0/network-state", reload.getRequestUrl().encodedPath(), reload.getRequestLine());
        }
    }

    @Test
    void aWriteReachesTheViewBeforeTheCallReturns() {
        RecordingView view = RecordingView.withoutCopy();
        shell = startShell(view, unusedSnapshots());
        ViewInput withRevision = new ViewInput.WriteReturned(3, 7);
        ViewInput revisionUnknown = new ViewInput.WriteRevisionUnknown();

        shell.writeReturned(3, 7);
        boolean revisionTaken = view.received().contains(withRevision);
        shell.writeRevisionUnknown();
        boolean writeRevisionUnknownTaken = view.received().contains(revisionUnknown);

        assertTrue(revisionTaken);
        assertTrue(writeRevisionUnknownTaken);
    }

    @Test
    void closeEndsTheTicksTheSubscriptionsAndTheAnswers() {
        RecordingView view = new RecordingView(EMPTY_COPY);
        view.answer(true);
        shell = startShell(view, unusedSnapshots());
        awaitTrue(() -> !view.received(ViewInput.TimePassed.class).isEmpty(), "the time ticks run");

        shell.close();
        int inputsAtClose = view.received().size();
        shell.writeReturned(3, 7);
        sleepQuietly();

        assertEquals(inputsAtClose, view.received().size(), "a closed shell steps the view no more");
        assertFalse(nats.hasSubscription(BATCH_SUBJECT));
        assertFalse(nats.hasSubscription(PULSE_SUBJECT));
        assertTrue(shell.copyToAnswer().join().isEmpty(), "a closed shell answers nothing");
    }

    @Test
    void aTickThatThrowsAnErrorEndsTheAnswersAndLeavesTheWritersAlone() {
        RecordingView view = new RecordingView(EMPTY_COPY);
        view.answer(true);
        view.failOn(ViewInput.TimePassed.class);
        shell = startShell(view, unusedSnapshots());
        boolean answeredBeforeTheTick = shell.copyToAnswer().join().isPresent();

        awaitTrue(() -> shell.copyToAnswer().join().isEmpty(), "the failed tick ends the answers");
        shell.writeReturned(3, 7);

        assertTrue(answeredBeforeTheTick);
        assertTrue(view.received().isEmpty(), "a stopped shell steps the view no more");
    }

    @Test
    void aReadAfterAnOwnWriteWaitsUntilTheCopyHasTheWrite() throws Exception {
        startLiveShellAtRevision10();
        shell.writeReturned(StateItems.EPOCH, 11);
        CompletableFuture<Optional<NetworkCopy>> read = shell.copyToAnswer();
        sleepQuietly();
        boolean waitedForTheBatch = !read.isDone();
        deliver(BATCH_SUBJECT, batch(10, 11).build());
        Optional<NetworkCopy> answer = answerOf(read);

        assertTrue(waitedForTheBatch, "a read after an own write waits for the copy");
        assertEquals(11, answer.orElseThrow().revision(), "the copy that has the write answers it");
    }

    @Test
    void aReadThatWaitsForAnOwnWriteGoesOverHttpAfterThreeSeconds() throws Exception {
        startLiveShellAtRevision10();
        shell.writeReturned(StateItems.EPOCH, 11);
        CompletableFuture<Optional<NetworkCopy>> read = shell.copyToAnswer();
        clockMillis.set(2_999);
        sleepQuietly();
        boolean waitingJustBefore = !read.isDone();
        clockMillis.set(3_000);
        Optional<NetworkCopy> answer = answerOf(read);

        assertTrue(waitingJustBefore, "the read waits for up to 3 s");
        assertTrue(answer.isEmpty(), "after 3 s the read goes over HTTP");
    }

    @Test
    void aReadThatWaitsForAnOwnWriteGoesOverHttpOnceTheViewIsNotLive() throws Exception {
        startLiveShellAtRevision10();
        shell.writeReturned(StateItems.EPOCH, 11);
        CompletableFuture<Optional<NetworkCopy>> read = shell.copyToAnswer();
        shell.connectionLost();
        Optional<NetworkCopy> answer = answerOf(read);

        assertTrue(answer.isEmpty(), "a view that is not live sends the read over HTTP, before its 3 s end");
    }

    @Test
    void closeSendsAWaitingReadOverHttp() throws Exception {
        startLiveShellAtRevision10();
        shell.writeReturned(StateItems.EPOCH, 11);
        CompletableFuture<Optional<NetworkCopy>> read = shell.copyToAnswer();
        shell.close();
        Optional<NetworkCopy> answer = answerOf(read);

        assertTrue(answer.isEmpty());
    }

    @Test
    void aStepThatThrowsSendsAWaitingReadOverHttp() throws Exception {
        RecordingView view = new RecordingView(EMPTY_COPY);
        view.awaitOwnWrite(true);
        shell = startShell(view, unusedSnapshots());
        CompletableFuture<Optional<NetworkCopy>> read = shell.copyToAnswer();
        view.failOn(ViewInput.TimePassed.class);
        Optional<NetworkCopy> answer = answerOf(read);

        assertTrue(answer.isEmpty(), "a shell that stopped answers no read from the copy");
    }

    @Test
    void aReadThatABatchAnswersContinuesOnNeitherTheFeedingThreadNorTheTimerNorUnderTheLock() throws Exception {
        startLiveShellAtRevision10();
        Object stepLock = stepLockOf(shell);
        Thread timerThread = timerThreadOf(shell);
        shell.writeReturned(StateItems.EPOCH, 11);
        CompletableFuture<Optional<NetworkCopy>> read = shell.copyToAnswer();
        CompletableFuture<Continuation> continuation = read.thenApply(answer -> Continuation.runningNow(stepLock));
        deliver(BATCH_SUBJECT, batch(10, 11).build());
        Continuation ran = answerOf(continuation);

        assertNotEquals(Thread.currentThread(), ran.thread(),
                "the caller's continuation must not run on the thread that delivers batches");
        assertNotEquals(timerThread, ran.thread(), "the caller's continuation must not stall the ticks");
        assertFalse(ran.holdsStepLock(), "the caller's continuation must not run under the shell's lock");
    }

    @Test
    void aReadThatATickSendsOverHttpContinuesOnNeitherTheTimerNorUnderTheLock() throws Exception {
        startLiveShellAtRevision10();
        Object stepLock = stepLockOf(shell);
        Thread timerThread = timerThreadOf(shell);
        shell.writeReturned(StateItems.EPOCH, 11);
        CompletableFuture<Optional<NetworkCopy>> read = shell.copyToAnswer();
        CompletableFuture<Continuation> continuation = read.thenApply(answer -> Continuation.runningNow(stepLock));
        clockMillis.set(3_000);
        Continuation ran = answerOf(continuation);

        assertNotEquals(timerThread, ran.thread(), "the caller's continuation must not stall the ticks");
        assertFalse(ran.holdsStepLock(), "the caller's continuation must not run under the shell's lock");
    }

    @Test
    void aReadThatWaitsWhileTheShellClosesGoesOverHttp() throws Exception {
        ClosesShellOnFirstLook view = new ClosesShellOnFirstLook();
        shell = startShell(view, unusedSnapshots());
        view.shell = shell;

        CompletableFuture<Optional<NetworkCopy>> read = shell.copyToAnswer();
        Optional<NetworkCopy> answer = answerOf(read);

        assertTrue(answer.isEmpty(), "a read that registers after the close has settled the others is not lost");
    }

    private record Continuation(Thread thread, boolean holdsStepLock) {

        static Continuation runningNow(Object stepLock) {
            return new Continuation(Thread.currentThread(), Thread.holdsLock(stepLock));
        }
    }

    /**
     * Lets every read wait, and closes the shell during a read's first look at it, as a close on another thread
     * between that look and the read's registration would.
     */
    private static final class ClosesShellOnFirstLook implements NetworkView {

        private volatile NetworkViewShell shell;
        private boolean looked;

        @Override
        public ViewStep step(ViewInput input, long nowMillis) {
            return new ViewStep(this, List.of());
        }

        @Override
        public ReadRoute routeRead(long readStartedAtMillis, long nowMillis) {
            if (!looked) {
                looked = true;
                shell.close();
            }
            return ReadRoute.WAIT;
        }

        @Override
        public Optional<NetworkCopy> copy() {
            return Optional.empty();
        }
    }

    private NetworkViewShell startShell(NetworkView view, SnapshotLoader snapshots) {
        return NetworkViewShell.start(NETWORK_ID, nats.connection(), snapshots, view, clockMillis::get);
    }

    /**
     * Starts the shell on the production machine, which loads {@link StateItems#network} at revision 10 and goes
     * live on a pulse at 0 ms.
     */
    private void startLiveShellAtRevision10() throws IOException {
        snapshotServer.enqueue(snapshotResponse(network(10)));
        snapshotServer.start();
        shell = startShell(NetworkViewMachine.start(index -> 0.0), snapshotsFrom(snapshotServer));
        shell.subscriptionsSent();
        awaitLiveAt(10);
    }

    private static <T> T answerOf(CompletableFuture<T> read)
            throws InterruptedException, ExecutionException, TimeoutException {
        return read.get(WAIT_LIMIT.toMillis(), TimeUnit.MILLISECONDS);
    }

    private static Object stepLockOf(NetworkViewShell shell) throws ReflectiveOperationException {
        Field stepLock = NetworkViewShell.class.getDeclaredField("stepLock");
        stepLock.setAccessible(true);
        return stepLock.get(shell);
    }

    private static Thread timerThreadOf(NetworkViewShell shell) throws Exception {
        Field timer = NetworkViewShell.class.getDeclaredField("timer");
        timer.setAccessible(true);
        ScheduledExecutorService executor = (ScheduledExecutorService) timer.get(shell);
        return executor.submit(Thread::currentThread).get(WAIT_LIMIT.toMillis(), TimeUnit.MILLISECONDS);
    }

    private void awaitLiveAt(long revision) {
        long deadline = System.nanoTime() + WAIT_LIMIT.toNanos();
        // A pulse before the snapshot is taken is no good pulse, so the pulse repeats until one is.
        while (shell.copyToAnswer().join().isEmpty()) {
            assertTrue(System.nanoTime() < deadline, "the view went live on its snapshot");
            deliverQuietly(PULSE_SUBJECT, pulse(revision));
            sleepMillis(20);
        }
    }

    private void deliver(String subject, MessageLite payload) throws InterruptedException {
        Message message = NatsMessage.builder()
                .subject(subject)
                .data(payload.toByteArray())
                .build();
        nats.deliver(subject, message);
    }

    private void deliverQuietly(String subject, MessageLite payload) {
        try {
            deliver(subject, payload);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }

    private static boolean isMessage(ViewInput input) {
        return input instanceof ViewInput.BatchReceived || input instanceof ViewInput.PulseReceived;
    }

    private static SnapshotLoader unusedSnapshots() {
        return new SnapshotLoader(new ApiClient(), "http://127.0.0.1:1", NETWORK_ID);
    }

    private static SnapshotLoader snapshotsFrom(MockWebServer controller) {
        String controllerUrl = "http://" + controller.getHostName() + ":" + controller.getPort();
        CloudApiOptions options = CloudApiOptions.builder()
                .controllerUrl(controllerUrl)
                .networkId(NETWORK_ID)
                .networkSecret("secret")
                .build();
        ApiClient apiClient = ApiClients.create(options);
        return new SnapshotLoader(apiClient, controllerUrl, NETWORK_ID);
    }

    private static MockResponse snapshotResponse(NetworkState snapshot) {
        Buffer body = new Buffer().write(snapshot.toByteArray());
        return new MockResponse()
                .setHeader("Content-Type", "application/x-protobuf")
                .setBody(body);
    }

    private static void awaitTrue(BooleanSupplier condition, String message) {
        long deadline = System.nanoTime() + WAIT_LIMIT.toNanos();
        while (!condition.getAsBoolean()) {
            assertTrue(System.nanoTime() < deadline, message);
            sleepMillis(10);
        }
    }

    private static void sleepQuietly() {
        sleepMillis(QUIET_MILLIS);
    }

    private static void sleepMillis(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }
}
