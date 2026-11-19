package app.simplecloud.api.internal.networkstate.read;

import app.simplecloud.api.CloudApiException;
import app.simplecloud.api.internal.networkstate.MonotonicClock;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The two sources of one kind's reads, and the choice between them for each read: the view's copy while the
 * view hands one out, else the controller. The copy is asked first, because the query cache in front of the
 * controller may hold an older answer than the copy.
 *
 * <p>When the copy is there at once, which is the common case, the read is answered, or handed to the
 * controller, on the caller's thread before the read returns. A read that fails holds its exception as the
 * cause of a {@link CompletionException}, whichever source failed.
 *
 * @param <S> the kind's source, such as {@link app.simplecloud.api.internal.server.ServerSource}
 */
public final class ReadSources<S> {

    /**
     * One read, asked of the source that answers it.
     */
    @FunctionalInterface
    public interface Read<S, T> {

        CompletableFuture<T> from(S source);
    }

    /**
     * A kind's source in a copy, such as {@code ReadableCopy::servers}.
     */
    @FunctionalInterface
    public interface SourceInCopy<S> {

        S in(ReadableCopy copy);
    }

    /**
     * A lookup that follows an event can be ahead of the copy by one relay round, so a copy that has nothing
     * for it is asked again shortly.
     */
    private static final long MISS_RETRY_DELAY_MILLIS = 200;
    private static final int MISS_MAX_RETRIES = 2;
    /**
     * The copy changes every few seconds, so a broken item would otherwise warn on every read, or warn once and
     * stay silent while it stays broken.
     */
    private static final Duration FAILURE_WARNING_INTERVAL = Duration.ofMinutes(10);

    private static final Logger LOGGER = Logger.getLogger(ReadSources.class.getName());

    /**
     * What a source said to one read.
     */
    private record Answer<T>(@Nullable T value, boolean fromCopy) {

        static <T> Answer<T> ofCopy(@Nullable T value) {
            return new Answer<>(value, true);
        }

        static <T> Answer<T> ofController(@Nullable T value) {
            return new Answer<>(value, false);
        }
    }

    private final ReadableCopies copies;
    private final SourceInCopy<S> sourceInCopy;
    private final S controller;
    private final MonotonicClock clock;
    private final AtomicLong nextWarningAtMillis;

    public ReadSources(ReadableCopies copies, SourceInCopy<S> sourceInCopy, S controller) {
        this(copies, sourceInCopy, controller, MonotonicClock.SYSTEM);
    }

    ReadSources(ReadableCopies copies, SourceInCopy<S> sourceInCopy, S controller, MonotonicClock clock) {
        this.copies = copies;
        this.sourceInCopy = sourceInCopy;
        this.controller = controller;
        this.clock = clock;
        this.nextWarningAtMillis = new AtomicLong(clock.nowMillis());
    }

    /**
     * Returns the copy's answer, or the controller's when the view hands out no copy or the copy's source fails.
     * A {@link CloudApiException} of the copy's source, which it throws for bad input, fails the read.
     */
    public <T> CompletableFuture<T> ask(Read<S, T> read) {
        CompletableFuture<Answer<T>> answer = answerOf(read);
        return answer.thenApply(Answer::value);
    }

    /**
     * Asks as {@link #ask} does, and asks again when the copy's answer holds nothing: after a short wait, at most
     * twice, choosing the source anew each time. An answer of the controller is never asked again here; the
     * query cache has its own recovery.
     */
    public <T> CompletableFuture<T> lookUp(Read<S, T> read) {
        CompletableFuture<Answer<T>> answer = lookUpWithRetries(read, MISS_MAX_RETRIES);
        return answer.thenApply(Answer::value);
    }

    private <T> CompletableFuture<Answer<T>> lookUpWithRetries(Read<S, T> read, int retriesLeft) {
        CompletableFuture<Answer<T>> answer = answerOf(read);
        return answer.thenCompose(answered -> keepOrAskAgain(answered, read, retriesLeft));
    }

    private <T> CompletableFuture<Answer<T>> keepOrAskAgain(Answer<T> answer, Read<S, T> read, int retriesLeft) {
        boolean copyHadNothing = answer.fromCopy() && holdsNothing(answer.value());
        boolean askAgain = copyHadNothing && retriesLeft > 0;
        if (!askAgain) {
            return CompletableFuture.completedFuture(answer);
        }
        return lookUpAfterMissDelay(read, retriesLeft - 1);
    }

    private <T> CompletableFuture<Answer<T>> lookUpAfterMissDelay(Read<S, T> read, int retriesLeft) {
        // Not completeOnTimeout or orTimeout: their stages run on the JVM's one shared delay thread, while the
        // delayed executor hands the retry to the common pool.
        Executor afterMissDelay = CompletableFuture.delayedExecutor(MISS_RETRY_DELAY_MILLIS, TimeUnit.MILLISECONDS);
        CompletableFuture<Read<S, T>> readAfterDelay = CompletableFuture.supplyAsync(() -> read, afterMissDelay);
        return readAfterDelay.thenCompose(delayedRead -> lookUpWithRetries(delayedRead, retriesLeft));
    }

    private static boolean holdsNothing(@Nullable Object answer) {
        boolean emptyCollection = answer instanceof Collection<?> items && items.isEmpty();
        return answer == null || emptyCollection;
    }

    private <T> CompletableFuture<Answer<T>> answerOf(Read<S, T> read) {
        CompletableFuture<Optional<ReadableCopy>> copy = copies.copyToAnswer();
        return copy.thenCompose(copyToAnswer -> answerFrom(copyToAnswer, read));
    }

    private <T> CompletableFuture<Answer<T>> answerFrom(Optional<ReadableCopy> copy, Read<S, T> read) {
        if (copy.isEmpty()) {
            return controllerAnswer(read);
        }
        CompletableFuture<T> copyAnswer = askCopy(copy.get(), read);
        CompletableFuture<Answer<T>> answer = copyAnswer.thenApply(Answer::ofCopy);
        return answer.exceptionallyCompose(failure -> answerAfterCopyFailure(failure, read));
    }

    /**
     * Turns a throw of the copy's source into a failed answer, so that both ways it fails are handled alike.
     */
    private <T> CompletableFuture<T> askCopy(ReadableCopy copy, Read<S, T> read) {
        try {
            S source = sourceInCopy.in(copy);
            return read.from(source);
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    private <T> CompletableFuture<Answer<T>> controllerAnswer(Read<S, T> read) {
        CompletableFuture<T> answer = read.from(controller);
        return answer.thenApply(Answer::ofController);
    }

    /**
     * Bad input fails the read, as the controller would refuse it too. Any other failure of the copy's source
     * comes from building or selecting its objects, for example on a JSON value the generated models refuse;
     * the controller then answers, as it would without a view.
     */
    private <T> CompletableFuture<Answer<T>> answerAfterCopyFailure(Throwable failure, Read<S, T> read) {
        Throwable cause = causeOf(failure);
        if (cause instanceof CloudApiException) {
            return CompletableFuture.failedFuture(cause);
        }
        logCopyFailure(cause);
        return controllerAnswer(read);
    }

    private static Throwable causeOf(Throwable failure) {
        boolean wrapped = failure instanceof CompletionException && failure.getCause() != null;
        return wrapped ? failure.getCause() : failure;
    }

    private void logCopyFailure(Throwable failure) {
        Level level = warningDue() ? Level.WARNING : Level.FINE;
        LOGGER.log(level, "Answering a read from the network view failed; the controller answers it", failure);
    }

    private boolean warningDue() {
        long nowMillis = clock.nowMillis();
        long nextWarningMillis = nextWarningAtMillis.get();
        boolean intervalPassed = nowMillis >= nextWarningMillis;
        long afterThisWarningMillis = nowMillis + FAILURE_WARNING_INTERVAL.toMillis();
        return intervalPassed && nextWarningAtMillis.compareAndSet(nextWarningMillis, afterThisWarningMillis);
    }
}
