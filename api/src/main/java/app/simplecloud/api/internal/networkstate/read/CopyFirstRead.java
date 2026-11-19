package app.simplecloud.api.internal.networkstate.read;

import app.simplecloud.api.CloudApiException;
import app.simplecloud.api.internal.networkstate.AnsweringCopy;
import app.simplecloud.api.internal.networkstate.NetworkCopy;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
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
 * <p>When the copy's answer is there at once, which is the common case, the result is taken from it, or the
 * read handed to the controller, on the caller's thread before the read returns. When the read waits for the
 * copy to reach an own write, that happens on the thread that ends the wait, and the result is relayed with
 * the same value or the same exception, not wrapped in another.
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
     * Returns {@code fromCopy}'s answer on the copy, or {@code fromController}'s when no copy may answer or
     * the copy's answer fails with another exception than a {@link CloudApiException}. A
     * {@code CloudApiException}, which the copy throws for bad input, fails the read.
     */
    public <T> CompletableFuture<T> read(
            Function<ReadableCopy, T> fromCopy,
            Supplier<CompletableFuture<T>> fromController
    ) {
        return read(fromCopy, CompletableFuture::completedFuture, fromController);
    }

    /**
     * Reads as {@link #read} does, and asks again when the copy's answer {@code isMiss}: after a short wait, at
     * most twice, each time from the copy or the controller as the view decides then. It returns the last
     * answer. An answer of the controller is never asked again here; the query cache has its own recovery.
     */
    public <T> CompletableFuture<T> readAgainOnMiss(
            Function<ReadableCopy, T> fromCopy,
            Predicate<T> isMiss,
            Supplier<CompletableFuture<T>> fromController
    ) {
        return readAgainOnMiss(fromCopy, isMiss, fromController, MISS_MAX_RETRIES);
    }

    private <T> CompletableFuture<T> readAgainOnMiss(
            Function<ReadableCopy, T> fromCopy,
            Predicate<T> isMiss,
            Supplier<CompletableFuture<T>> fromController,
            int retriesLeft
    ) {
        Function<T, CompletableFuture<T>> useCopyAnswer = copyAnswer -> keepOrAskAgain(copyAnswer, fromCopy, isMiss,
                fromController, retriesLeft);
        return read(fromCopy, useCopyAnswer, fromController);
    }

    private <T> CompletableFuture<T> keepOrAskAgain(
            T copyAnswer,
            Function<ReadableCopy, T> fromCopy,
            Predicate<T> isMiss,
            Supplier<CompletableFuture<T>> fromController,
            int retriesLeft
    ) {
        boolean askAgain = retriesLeft > 0 && isMiss.test(copyAnswer);
        if (!askAgain) {
            return CompletableFuture.completedFuture(copyAnswer);
        }
        Executor afterDelay = CompletableFuture.delayedExecutor(MISS_RETRY_DELAY_MILLIS, TimeUnit.MILLISECONDS);
        CompletableFuture<T> answered = new CompletableFuture<>();
        afterDelay.execute(() -> readAgainAndRelay(fromCopy, isMiss, fromController, retriesLeft - 1, answered));
        return answered;
    }

    private <T> void readAgainAndRelay(
            Function<ReadableCopy, T> fromCopy,
            Predicate<T> isMiss,
            Supplier<CompletableFuture<T>> fromController,
            int retriesLeft,
            CompletableFuture<T> relayed
    ) {
        try {
            CompletableFuture<T> answer = readAgainOnMiss(fromCopy, isMiss, fromController, retriesLeft);
            relay(answer, relayed);
        } catch (RuntimeException | Error failure) {
            relayed.completeExceptionally(failure);
        }
    }

    /**
     * @param useCopyAnswer turns the copy's answer into the read's result; the controller's answer is the
     *                      result as it is
     */
    private <T> CompletableFuture<T> read(
            Function<ReadableCopy, T> fromCopy,
            Function<T, CompletableFuture<T>> useCopyAnswer,
            Supplier<CompletableFuture<T>> fromController
    ) {
        CompletableFuture<Optional<NetworkCopy>> copyToAnswer = answeringCopy.copyToAnswer();
        boolean answerableNow = copyToAnswer.state() == Future.State.SUCCESS;
        if (answerableNow) {
            return answer(copyToAnswer.resultNow(), fromCopy, useCopyAnswer, fromController);
        }
        CompletableFuture<T> relayed = new CompletableFuture<>();
        copyToAnswer.whenComplete((copy, copyFailure) ->
                answerAndRelay(copy, copyFailure, fromCopy, useCopyAnswer, fromController, relayed));
        return relayed;
    }

    private <T> CompletableFuture<T> answer(
            Optional<NetworkCopy> copy,
            Function<ReadableCopy, T> fromCopy,
            Function<T, CompletableFuture<T>> useCopyAnswer,
            Supplier<CompletableFuture<T>> fromController
    ) {
        if (copy.isEmpty()) {
            return fromController.get();
        }
        ReadableCopy readable = readableCopyOf(copy.get());
        T copyAnswer;
        try {
            copyAnswer = fromCopy.apply(readable);
        } catch (CloudApiException badInput) {
            return CompletableFuture.failedFuture(badInput);
        } catch (RuntimeException failure) {
            reportFailedAnswer(failure);
            return fromController.get();
        }
        return useCopyAnswer.apply(copyAnswer);
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
     * Relays the exception a future holds as it is: {@code thenCompose} would wrap one that is not a
     * {@link java.util.concurrent.CompletionException} yet, so a caller's {@code exceptionally} would see
     * another type than over the controller's path alone.
     */
    private <T> void answerAndRelay(
            Optional<NetworkCopy> copy,
            @Nullable Throwable copyFailure,
            Function<ReadableCopy, T> fromCopy,
            Function<T, CompletableFuture<T>> useCopyAnswer,
            Supplier<CompletableFuture<T>> fromController,
            CompletableFuture<T> relayed
    ) {
        if (copyFailure != null) {
            relayed.completeExceptionally(copyFailure);
            return;
        }
        try {
            CompletableFuture<T> answer = answer(copy, fromCopy, useCopyAnswer, fromController);
            relay(answer, relayed);
        } catch (RuntimeException | Error failure) {
            relayed.completeExceptionally(failure);
        }
    }

    private static <T> void relay(CompletableFuture<T> answer, CompletableFuture<T> relayed) {
        answer.whenComplete((value, failure) -> complete(relayed, value, failure));
    }

    private static <T> void complete(CompletableFuture<T> relayed, T value, @Nullable Throwable failure) {
        if (failure != null) {
            relayed.completeExceptionally(failure);
            return;
        }
        relayed.complete(value);
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
