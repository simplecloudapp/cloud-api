package app.simplecloud.api.internal.networkstate.read;

import app.simplecloud.api.CloudApiException;
import app.simplecloud.api.internal.networkstate.AnsweringCopy;
import app.simplecloud.api.internal.networkstate.NetworkCopy;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Answers a read from the copy when the view lets a copy answer, else from the controller. The copy is asked
 * first, because the query cache in front of the controller may hold an older answer than the copy.
 *
 * <p>When the copy is there at once, which is the common case, the read is answered, or handed to the
 * controller, on the caller's thread before the read returns. A read that fails holds its exception as the
 * cause of a {@link java.util.concurrent.CompletionException}, whichever source failed.
 *
 * <p>One instance serves all API classes of a runtime, so that their reads of one copy share its built
 * objects.
 */
public final class CopyFirstRead {

    /**
     * A lookup that follows an event can be ahead of the copy by one relay round, so a copy that misses the
     * item is asked again shortly, as the query cache asks the controller again.
     */
    private static final long MISS_RETRY_DELAY_MILLIS = 200;
    private static final int MISS_MAX_RETRIES = 2;

    private static final Logger LOGGER = Logger.getLogger(CopyFirstRead.class.getName());

    /**
     * What the copy said to one read. It did not answer when the view let no copy answer, or when building the
     * answer failed.
     */
    private record CopyAnswer<T>(boolean answered, @Nullable T value) {

        static <T> CopyAnswer<T> of(@Nullable T value) {
            return new CopyAnswer<>(true, value);
        }

        static <T> CopyAnswer<T> none() {
            return new CopyAnswer<>(false, null);
        }
    }

    private final AnsweringCopy answeringCopy;
    private final String networkId;
    private final AtomicBoolean failureReported = new AtomicBoolean();
    /**
     * Compared by identity: equals would compare every item of both copies. A reader that still holds an older
     * copy may replace a newer one here; that costs a build, never a wrong answer.
     */
    private volatile @Nullable ReadableCopy latestCopy;

    /**
     * @param networkId the network of the copies, a UUID, which their rows do not carry
     */
    public CopyFirstRead(AnsweringCopy answeringCopy, String networkId) {
        this.answeringCopy = answeringCopy;
        this.networkId = networkId;
    }

    /**
     * Returns {@code fromCopy}'s answer on the copy, or {@code fromController}'s when the copy did not answer. A
     * {@link CloudApiException} from {@code fromCopy}, which the copy throws for bad input, fails the read.
     */
    public <T> CompletableFuture<T> read(
            Function<ReadableCopy, T> fromCopy,
            Supplier<CompletableFuture<T>> fromController
    ) {
        CompletableFuture<CopyAnswer<T>> copyAnswer = askCopy(fromCopy);
        return copyAnswer.thenCompose(answer -> answerOrAskController(answer, fromController));
    }

    /**
     * Reads as {@link #read} does, but asks the copy again when its answer {@code isMiss}: after a short wait,
     * at most twice. An answer of the controller is never asked again here; the query cache has its own
     * recovery.
     */
    public <T> CompletableFuture<T> readAgainOnMiss(
            Function<ReadableCopy, T> fromCopy,
            Predicate<T> isMiss,
            Supplier<CompletableFuture<T>> fromController
    ) {
        CompletableFuture<CopyAnswer<T>> copyAnswer = askCopyAgainOnMiss(fromCopy, isMiss, MISS_MAX_RETRIES);
        return copyAnswer.thenCompose(answer -> answerOrAskController(answer, fromController));
    }

    private static <T> CompletableFuture<T> answerOrAskController(
            CopyAnswer<T> answer,
            Supplier<CompletableFuture<T>> fromController
    ) {
        if (answer.answered()) {
            return CompletableFuture.completedFuture(answer.value());
        }
        return fromController.get();
    }

    private <T> CompletableFuture<CopyAnswer<T>> askCopyAgainOnMiss(
            Function<ReadableCopy, T> fromCopy,
            Predicate<T> isMiss,
            int retriesLeft
    ) {
        return askCopy(fromCopy).thenCompose(answer -> {
            boolean missed = answer.answered() && isMiss.test(answer.value());
            if (!missed || retriesLeft == 0) {
                return CompletableFuture.completedFuture(answer);
            }
            return afterMissDelay().thenCompose(delayEnded -> askCopyAgainOnMiss(fromCopy, isMiss, retriesLeft - 1));
        });
    }

    private static CompletableFuture<Void> afterMissDelay() {
        Executor afterDelay = CompletableFuture.delayedExecutor(MISS_RETRY_DELAY_MILLIS, TimeUnit.MILLISECONDS);
        return CompletableFuture.runAsync(() -> { }, afterDelay);
    }

    private <T> CompletableFuture<CopyAnswer<T>> askCopy(Function<ReadableCopy, T> fromCopy) {
        return answeringCopy.copyToAnswer().thenApply(copy -> answerOf(copy, fromCopy));
    }

    private <T> CopyAnswer<T> answerOf(Optional<NetworkCopy> copy, Function<ReadableCopy, T> fromCopy) {
        if (copy.isEmpty()) {
            return CopyAnswer.none();
        }
        ReadableCopy readable = readableCopyOf(copy.get());
        try {
            return CopyAnswer.of(fromCopy.apply(readable));
        } catch (CloudApiException badInput) {
            throw badInput;
        } catch (RuntimeException failure) {
            reportFailedAnswer(failure);
            return CopyAnswer.none();
        }
    }

    private ReadableCopy readableCopyOf(NetworkCopy copy) {
        ReadableCopy latest = latestCopy;
        if (latest != null && latest.copy() == copy) {
            return latest;
        }
        ReadableCopy readable = new ReadableCopy(copy, networkId);
        latestCopy = readable;
        return readable;
    }

    /**
     * Apart from bad input, the copy's answer throws only where building its objects fails, for example on a
     * JSON value the generated models refuse. The controller then answers, as it would without a view.
     */
    private void reportFailedAnswer(RuntimeException failure) {
        Level level = failureReported.compareAndSet(false, true) ? Level.WARNING : Level.FINE;
        LOGGER.log(level, "Answering a read from the network view failed; the controller answers it", failure);
    }
}
