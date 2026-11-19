package app.simplecloud.api.internal.networkstate.read;

import app.simplecloud.api.CloudApiException;
import app.simplecloud.api.internal.networkstate.AnsweringView;
import app.simplecloud.api.internal.networkstate.NetworkCopy;
import app.simplecloud.api.internal.server.ServerSource;
import build.buf.gen.simplecloud.controller.v2.StateServer;
import com.google.protobuf.ByteString;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReadSourcesTest {

    private static final String NETWORK_ID = "0d000000-0000-4000-8000-000000000001";
    private static final String FROM_COPY = "from the copy";
    private static final String FROM_CONTROLLER = "from the controller";
    private static final ReadSources.Read<ItemSource, String> ITEM = ItemSource::item;
    private static final ReadSources.Read<ItemSource, List<String>> ITEMS = ItemSource::items;

    private final Logger logger = Logger.getLogger(ReadSources.class.getName());
    private final List<LogRecord> logged = new ArrayList<>();
    private final Handler recordingHandler = new Handler() {
        @Override
        public void publish(LogRecord record) {
            logged.add(record);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    };
    private final ItemSource copySource = new ItemSource(FROM_COPY);
    private final ItemSource controller = new ItemSource(FROM_CONTROLLER);
    private final CountingView view = new CountingView(true);
    private Level loggerLevel;

    @BeforeEach
    void recordLogs() {
        loggerLevel = logger.getLevel();
        logger.setLevel(Level.ALL);
        logger.addHandler(recordingHandler);
    }

    @AfterEach
    void stopRecordingLogs() {
        logger.removeHandler(recordingHandler);
        logger.setLevel(loggerLevel);
    }

    @Test
    void aCopyThereAtOnceAnswersOnTheCallersThreadBeforeTheReadReturns() {
        ReadSources<ItemSource> sources = sourcesWithCopySource(view);
        Thread caller = Thread.currentThread();

        CompletableFuture<String> read = sources.ask(ITEM);

        assertTrue(read.isDone());
        assertEquals(FROM_COPY, read.join());
        assertSame(caller, copySource.answeringThread.get());
        assertEquals(0, controller.reads.get());
    }

    @Test
    void noCopyLeavesTheReadToTheController() {
        ReadSources<ItemSource> sources = new ReadSources<>(ReadableCopies.none(), copy -> copySource, controller);

        String answer = sources.ask(ITEM).join();

        assertEquals(FROM_CONTROLLER, answer);
        assertEquals(1, controller.reads.get());
    }

    @Test
    void aReadThatWaitsForTheCopyGetsTheCopysAnswer() {
        CompletableFuture<Optional<NetworkCopy>> waitingCopy = new CompletableFuture<>();
        ReadSources<ItemSource> sources = sourcesWithCopySource(() -> waitingCopy);

        CompletableFuture<String> read = sources.ask(ITEM);
        boolean waited = !read.isDone();
        waitingCopy.complete(Optional.of(copyAt(1)));

        assertTrue(waited);
        assertEquals(FROM_COPY, read.join());
    }

    @Test
    void aReadThatWaitedFailsWithTheControllersException() {
        CompletableFuture<Optional<NetworkCopy>> waitingCopy = new CompletableFuture<>();
        CloudApiException unavailable = new CloudApiException(CloudApiException.Reason.UNAVAILABLE, "timed out");
        ItemSource failingController = ItemSource.failingWith(unavailable);
        ReadableCopies copies = new ReadableCopies(() -> waitingCopy, NETWORK_ID);
        ReadSources<ItemSource> sources = new ReadSources<>(copies, copy -> copySource, failingController);

        CompletableFuture<String> read = sources.ask(ITEM);
        waitingCopy.complete(Optional.empty());
        Throwable seen = failureOf(read);

        assertSame(unavailable, seen.getCause());
    }

    @Test
    void badInputThrownByTheCopyFailsTheReadWithoutAskingTheController() {
        CloudApiException badInput = new CloudApiException(CloudApiException.Reason.INVALID_REQUEST, "bad sort");
        ItemSource throwingCopy = ItemSource.throwing(badInput);
        ReadSources<ItemSource> sources = new ReadSources<>(copiesOf(view), copy -> throwingCopy, controller);

        Throwable seen = failureOf(sources.ask(ITEM));

        assertSame(badInput, seen.getCause());
        assertEquals(0, controller.reads.get());
    }

    @Test
    void badInputAsTheCopysFailedAnswerFailsTheReadWithoutAskingTheController() {
        CloudApiException badInput = new CloudApiException(CloudApiException.Reason.INVALID_REQUEST, "bad limit");
        ItemSource failingCopy = ItemSource.failingWith(badInput);
        ReadSources<ItemSource> sources = new ReadSources<>(copiesOf(view), copy -> failingCopy, controller);

        Throwable seen = failureOf(sources.ask(ITEM));

        assertSame(badInput, seen.getCause());
        assertEquals(0, controller.reads.get());
    }

    @Test
    void aFaultOfTheCopyLeavesTheReadToTheControllerAndWarnsOnceInTenMinutes() {
        ItemSource brokenCopy = ItemSource.throwing(new IllegalStateException("a group the models refuse"));
        AtomicLong nanos = new AtomicLong();
        ReadSources<ItemSource> sources = new ReadSources<>(copiesOf(view), copy -> brokenCopy, controller,
                nanos::get);

        String first = sources.ask(ITEM).join();
        nanos.addAndGet(Duration.ofMinutes(9).toNanos());
        String withinTenMinutes = sources.ask(ITEM).join();
        nanos.addAndGet(Duration.ofMinutes(1).toNanos());
        String afterTenMinutes = sources.ask(ITEM).join();

        assertEquals(FROM_CONTROLLER, first);
        assertEquals(FROM_CONTROLLER, withinTenMinutes);
        assertEquals(FROM_CONTROLLER, afterTenMinutes);
        assertEquals(List.of(Level.WARNING, Level.FINE, Level.WARNING), loggedLevels());
    }

    @Test
    void aFailedBuildIsNotRepeatedForTheSameCopy() {
        NetworkCopy serverWithoutItsGroup = serverWithoutItsGroup();
        AnsweringView sameCopy = () -> CompletableFuture.completedFuture(Optional.of(serverWithoutItsGroup));
        ServerSource controllerServers = new ServerIndex(copyAt(1), NETWORK_ID);
        ReadableCopies copies = new ReadableCopies(sameCopy, NETWORK_ID);
        ReadSources<ServerSource> sources = new ReadSources<>(copies, ReadableCopy::servers, controllerServers);

        sources.ask(source -> source.servers(null)).join();
        sources.ask(source -> source.servers(null)).join();

        assertEquals(2, logged.size());
        assertInstanceOf(IllegalStateException.class, logged.get(0).getThrown());
        assertSame(logged.get(0).getThrown(), logged.get(1).getThrown());
    }

    @Test
    void aLookUpAsksAgainAndGetsTheItemOfALaterCopy() throws Exception {
        ReadSources<ItemSource> sources = new ReadSources<>(copiesOf(view), ReadSourcesTest::itemFromRevisionTwo,
                controller);

        String answer = sources.lookUp(ITEM).get(5, TimeUnit.SECONDS);

        assertEquals(FROM_COPY, answer);
        assertEquals(2, view.asks.get());
    }

    @Test
    void aLookUpAsksAgainAtMostTwice() throws Exception {
        ItemSource emptyCopy = new ItemSource(null);
        ReadSources<ItemSource> sources = new ReadSources<>(copiesOf(view), copy -> emptyCopy, controller);

        String answer = sources.lookUp(ITEM).get(5, TimeUnit.SECONDS);

        assertNull(answer);
        assertEquals(3, view.asks.get());
        assertEquals(0, controller.reads.get());
    }

    @Test
    void aLookUpTakesAnEmptyListOfTheCopyAsAMiss() throws Exception {
        ItemSource emptyCopy = new ItemSource(null);
        ReadSources<ItemSource> sources = new ReadSources<>(copiesOf(view), copy -> emptyCopy, controller);

        List<String> answer = sources.lookUp(ITEMS).get(5, TimeUnit.SECONDS);

        assertEquals(List.of(), answer);
        assertEquals(3, view.asks.get());
    }

    @Test
    void aLookUpKeepsAListOfTheCopyThatHoldsAnItem() {
        ReadSources<ItemSource> sources = sourcesWithCopySource(view);

        List<String> answer = sources.lookUp(ITEMS).join();

        assertEquals(List.of(FROM_COPY), answer);
        assertEquals(1, view.asks.get());
    }

    @Test
    void aLookUpDoesNotAskTheControllerAgainWhenItMisses() throws Exception {
        CountingView noCopy = new CountingView(false);
        ItemSource missingController = new ItemSource(null);
        ReadSources<ItemSource> sources = new ReadSources<>(copiesOf(noCopy), copy -> copySource, missingController);

        String answer = sources.lookUp(ITEM).get(5, TimeUnit.SECONDS);

        assertNull(answer);
        assertEquals(1, noCopy.asks.get());
        assertEquals(1, missingController.reads.get());
    }

    private ReadSources<ItemSource> sourcesWithCopySource(AnsweringView answeringView) {
        return new ReadSources<>(copiesOf(answeringView), copy -> copySource, controller);
    }

    private List<Level> loggedLevels() {
        return logged.stream().map(LogRecord::getLevel).toList();
    }

    private static ReadableCopies copiesOf(AnsweringView answeringView) {
        return new ReadableCopies(answeringView, NETWORK_ID);
    }

    private static Throwable failureOf(CompletableFuture<?> read) {
        CompletableFuture<Throwable> failure = read.handle((answer, thrown) -> thrown);
        Throwable thrown = failure.join();
        return assertInstanceOf(CompletionException.class, thrown);
    }

    private static ItemSource itemFromRevisionTwo(ReadableCopy copy) {
        boolean holdsTheItem = copy.copy().revision() >= 2;
        return new ItemSource(holdsTheItem ? FROM_COPY : null);
    }

    private static NetworkCopy serverWithoutItsGroup() {
        ByteString serverId = ByteString.fromHex("0d000000000040008000000000000201");
        ByteString missingGroupId = ByteString.fromHex("0d000000000040008000000000000301");
        StateServer server = StateServer.newBuilder()
                .setId(serverId)
                .setServerGroupId(missingGroupId)
                .setState("AVAILABLE")
                .build();
        return new NetworkCopy(1, 1, Map.of(serverId, server), Map.of(), Map.of(), Map.of(), Map.of());
    }

    private static NetworkCopy copyAt(long revision) {
        return new NetworkCopy(1, revision, Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
    }

    /**
     * Hands out a new copy for each read, its revision the number of the read, or no copy at all.
     */
    private static final class CountingView implements AnsweringView {

        private final AtomicInteger asks = new AtomicInteger();
        private final boolean handsOutCopies;

        CountingView(boolean handsOutCopies) {
            this.handsOutCopies = handsOutCopies;
        }

        @Override
        public CompletableFuture<Optional<NetworkCopy>> copyToAnswer() {
            int ask = asks.incrementAndGet();
            Optional<NetworkCopy> copy = handsOutCopies ? Optional.of(copyAt(ask)) : Optional.empty();
            return CompletableFuture.completedFuture(copy);
        }
    }

    /**
     * Answers with one item, or with nothing for a null item, or fails as it was told to.
     */
    private static final class ItemSource {

        private final @Nullable String item;
        private final @Nullable RuntimeException thrown;
        private final @Nullable RuntimeException failure;
        private final AtomicInteger reads = new AtomicInteger();
        private final AtomicReference<Thread> answeringThread = new AtomicReference<>();

        ItemSource(@Nullable String item) {
            this(item, null, null);
        }

        private ItemSource(@Nullable String item, @Nullable RuntimeException thrown,
                           @Nullable RuntimeException failure) {
            this.item = item;
            this.thrown = thrown;
            this.failure = failure;
        }

        static ItemSource throwing(RuntimeException thrown) {
            return new ItemSource(null, thrown, null);
        }

        static ItemSource failingWith(RuntimeException failure) {
            return new ItemSource(null, null, failure);
        }

        CompletableFuture<String> item() {
            reads.incrementAndGet();
            answeringThread.set(Thread.currentThread());
            if (thrown != null) {
                throw thrown;
            }
            if (failure != null) {
                return CompletableFuture.failedFuture(failure);
            }
            return CompletableFuture.completedFuture(item);
        }

        CompletableFuture<List<String>> items() {
            List<String> items = item == null ? List.of() : List.of(item);
            return CompletableFuture.completedFuture(items);
        }
    }
}
