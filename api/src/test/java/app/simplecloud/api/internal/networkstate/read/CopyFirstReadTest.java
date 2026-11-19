package app.simplecloud.api.internal.networkstate.read;

import app.simplecloud.api.CloudApiException;
import app.simplecloud.api.internal.networkstate.AnsweringCopy;
import app.simplecloud.api.internal.networkstate.NetworkCopy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CopyFirstReadTest {

    private static final String NETWORK_ID = "0d000000-0000-4000-8000-000000000001";
    private static final String FROM_CONTROLLER = "from the controller";
    private static final String ITEM = "item";

    private final Logger logger = Logger.getLogger(CopyFirstRead.class.getName());
    private final List<Level> loggedLevels = new ArrayList<>();
    private final Handler recordingHandler = new Handler() {
        @Override
        public void publish(LogRecord record) {
            loggedLevels.add(record.getLevel());
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    };
    private final AtomicInteger controllerReads = new AtomicInteger();
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
        CopyFirstRead reads = new CopyFirstRead(answering(copyAt(1)), NETWORK_ID);
        Thread caller = Thread.currentThread();
        AtomicReference<Thread> answeringThread = new AtomicReference<>();

        CompletableFuture<String> read = reads.read(copy -> {
            answeringThread.set(Thread.currentThread());
            return ITEM;
        }, this::controllerAnswer);

        assertTrue(read.isDone());
        assertEquals(ITEM, read.join());
        assertSame(caller, answeringThread.get());
        assertEquals(0, controllerReads.get());
    }

    @Test
    void noCopyLeavesTheReadToTheController() {
        CopyFirstRead reads = new CopyFirstRead(AnsweringCopy.NONE, NETWORK_ID);

        String answer = reads.read(copy -> ITEM, this::controllerAnswer).join();

        assertEquals(FROM_CONTROLLER, answer);
        assertEquals(1, controllerReads.get());
    }

    @Test
    void aReadThatWaitsForTheCopyGetsTheCopysAnswer() {
        CompletableFuture<Optional<NetworkCopy>> waitingCopy = new CompletableFuture<>();
        CopyFirstRead reads = new CopyFirstRead(() -> waitingCopy, NETWORK_ID);

        CompletableFuture<String> read = reads.read(copy -> ITEM, this::controllerAnswer);
        boolean waited = !read.isDone();
        waitingCopy.complete(Optional.of(copyAt(1)));

        assertTrue(waited);
        assertEquals(ITEM, read.join());
    }

    @Test
    void aReadThatWaitedRelaysTheControllersExceptionUnwrapped() {
        CompletableFuture<Optional<NetworkCopy>> waitingCopy = new CompletableFuture<>();
        CopyFirstRead reads = new CopyFirstRead(() -> waitingCopy, NETWORK_ID);
        CloudApiException unavailable = new CloudApiException(CloudApiException.Reason.UNAVAILABLE, "timed out");

        CompletableFuture<String> read = reads.read(copy -> ITEM, () -> CompletableFuture.failedFuture(unavailable));
        waitingCopy.complete(Optional.empty());
        Throwable seen = read.handle((answer, failure) -> failure).join();

        assertSame(unavailable, seen);
    }

    @Test
    void badInputFailsTheReadWithoutAskingTheController() {
        CopyFirstRead reads = new CopyFirstRead(answering(copyAt(1)), NETWORK_ID);
        CloudApiException badInput = new CloudApiException(CloudApiException.Reason.INVALID_REQUEST, "bad sort");

        CompletableFuture<String> read = reads.read(copy -> {
            throw badInput;
        }, this::controllerAnswer);
        Throwable seen = read.handle((answer, failure) -> failure).join();

        assertSame(badInput, seen);
        assertEquals(0, controllerReads.get());
    }

    @Test
    void aFaultOfTheCopyLeavesTheReadToTheControllerAndWarnsOnce() {
        CopyFirstRead reads = new CopyFirstRead(answering(copyAt(1)), NETWORK_ID);

        String first = reads.read(copy -> failingCopyRead(), this::controllerAnswer).join();
        String second = reads.read(copy -> failingCopyRead(), this::controllerAnswer).join();

        assertEquals(FROM_CONTROLLER, first);
        assertEquals(FROM_CONTROLLER, second);
        assertEquals(List.of(Level.WARNING, Level.FINE), loggedLevels);
    }

    @Test
    void aMissAsksAgainAndGetsTheItemOfALaterCopy() throws Exception {
        AtomicInteger asks = new AtomicInteger();
        AnsweringCopy laterCopyHasTheItem = () -> {
            int ask = asks.incrementAndGet();
            return CompletableFuture.completedFuture(Optional.of(copyAt(ask)));
        };
        CopyFirstRead reads = new CopyFirstRead(laterCopyHasTheItem, NETWORK_ID);

        String answer = reads.readAgainOnMiss(CopyFirstReadTest::itemFromRevisionTwo, Objects::isNull,
                this::controllerAnswer).get(5, TimeUnit.SECONDS);

        assertEquals(ITEM, answer);
        assertEquals(2, asks.get());
    }

    @Test
    void aMissAsksAgainAtMostTwice() throws Exception {
        AtomicInteger asks = new AtomicInteger();
        AnsweringCopy copyNeverHasTheItem = () -> {
            asks.incrementAndGet();
            return CompletableFuture.completedFuture(Optional.of(copyAt(1)));
        };
        CopyFirstRead reads = new CopyFirstRead(copyNeverHasTheItem, NETWORK_ID);

        String answer = reads.readAgainOnMiss(copy -> null, Objects::isNull, this::controllerAnswer)
                .get(5, TimeUnit.SECONDS);

        assertNull(answer);
        assertEquals(3, asks.get());
        assertEquals(0, controllerReads.get());
    }

    @Test
    void aMissOfTheControllerIsNotAskedAgain() throws Exception {
        AtomicInteger asks = new AtomicInteger();
        AnsweringCopy noCopy = () -> {
            asks.incrementAndGet();
            return CompletableFuture.completedFuture(Optional.empty());
        };
        CopyFirstRead reads = new CopyFirstRead(noCopy, NETWORK_ID);
        Supplier<CompletableFuture<String>> controllerMisses = () -> {
            controllerReads.incrementAndGet();
            return CompletableFuture.completedFuture(null);
        };

        String answer = reads.readAgainOnMiss(copy -> ITEM, Objects::isNull, controllerMisses)
                .get(5, TimeUnit.SECONDS);

        assertNull(answer);
        assertEquals(1, asks.get());
        assertEquals(1, controllerReads.get());
    }

    @Test
    void readersOfOneCopyShareItsReadableCopy() {
        AtomicReference<NetworkCopy> current = new AtomicReference<>(copyAt(1));
        CopyFirstRead reads = new CopyFirstRead(() -> CompletableFuture.completedFuture(Optional.of(current.get())),
                NETWORK_ID);

        ReadableCopy first = reads.read(copy -> copy, this::noControllerRead).join();
        ReadableCopy second = reads.read(copy -> copy, this::noControllerRead).join();
        current.set(copyAt(2));
        ReadableCopy ofNewCopy = reads.read(copy -> copy, this::noControllerRead).join();

        assertSame(first, second);
        assertNotSame(first, ofNewCopy);
        assertSame(current.get(), ofNewCopy.copy());
    }

    private CompletableFuture<String> controllerAnswer() {
        controllerReads.incrementAndGet();
        return CompletableFuture.completedFuture(FROM_CONTROLLER);
    }

    private CompletableFuture<ReadableCopy> noControllerRead() {
        throw new AssertionError("the copy answers");
    }

    private static String failingCopyRead() {
        throw new IllegalStateException("a group the generated models refuse");
    }

    private static String itemFromRevisionTwo(ReadableCopy copy) {
        return copy.copy().revision() >= 2 ? ITEM : null;
    }

    private static AnsweringCopy answering(NetworkCopy copy) {
        return () -> CompletableFuture.completedFuture(Optional.of(copy));
    }

    private static NetworkCopy copyAt(long revision) {
        return new NetworkCopy(1, revision, Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
    }
}
