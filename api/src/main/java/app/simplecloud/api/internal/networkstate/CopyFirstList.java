package app.simplecloud.api.internal.networkstate;

import app.simplecloud.api.internal.networkstate.read.CopyList;
import app.simplecloud.api.internal.web.ApiErrors;
import app.simplecloud.api.web.ApiException;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * One controller list route that two sources answer with the same response: a view's copy, where the view lets
 * it answer, and otherwise the controller over HTTP through the caller's query cache. The copy is asked first,
 * because the cache may hold an older answer than the copy.
 *
 * <p>When the copy's answer is there at once, which is the common case, the result is taken from it, or the
 * read handed to the cache, on the caller's thread before {@link #read} returns. When the read waits for the copy
 * to reach an own write, that happens on the thread that ends the wait, and the result is relayed with the same
 * value or the same exception, not wrapped in another.
 *
 * @param <P> the route's parameters
 * @param <R> the generated response model
 */
public final class CopyFirstList<P, R> {

    /**
     * Calls the list route over HTTP with the generated client.
     */
    @FunctionalInterface
    public interface ControllerList<P, R> {

        R list(P parameters) throws ApiException;
    }

    /**
     * Sends a read the copy did not answer through the query cache, with that read's cache key and miss
     * recovery. {@code fromController} fetches the read's result from the controller.
     */
    @FunctionalInterface
    public interface CachedControllerRead<T> {

        CompletableFuture<T> read(Supplier<CompletableFuture<T>> fromController);
    }

    private static final Logger LOGGER = Logger.getLogger(CopyFirstList.class.getName());

    private final CopyList<P, R> copyList;
    private final AnsweringCopy answeringCopy;
    private final ControllerList<P, R> controllerList;
    private final Executor executor;
    private final AnswersOfOneCopy<P, R> copyAnswers;
    private final AtomicBoolean failureReported = new AtomicBoolean();

    /**
     * @param executor runs the HTTP calls
     */
    public CopyFirstList(
            CopyList<P, R> copyList,
            AnsweringCopy answeringCopy,
            ControllerList<P, R> controllerList,
            Executor executor
    ) {
        this.copyList = copyList;
        this.answeringCopy = answeringCopy;
        this.controllerList = controllerList;
        this.executor = executor;
        this.copyAnswers = new AnswersOfOneCopy<>(copyList);
    }

    /**
     * Returns the read's result: {@code resultOf} the copy's answer, or what {@code throughCache} returns for a
     * fetch that applies {@code resultOf} to the controller's answer.
     *
     * @param resultOf picks the read's result out of the response, the same for both sources
     */
    public <T> CompletableFuture<T> read(P parameters, Function<R, T> resultOf, CachedControllerRead<T> throughCache) {
        CompletableFuture<Optional<R>> fromCopy = answerFromCopy(parameters);
        if (fromCopy.isDone()) {
            Optional<R> answered = fromCopy.join();
            return resultFromCopyOrController(answered, parameters, resultOf, throughCache);
        }
        return resultAfterWait(fromCopy, parameters, resultOf, throughCache);
    }

    /**
     * A read with parameters the controller refuses asks it at once: the copy never answers it, so waiting for
     * an own write would only delay it.
     */
    private CompletableFuture<Optional<R>> answerFromCopy(P parameters) {
        if (copyList.refuses(parameters)) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        CompletableFuture<Optional<NetworkCopy>> copyToAnswer = answeringCopy.copyToAnswer(copyList.kind());
        return copyToAnswer.thenApply(copy -> answerOf(copy, parameters));
    }

    private Optional<R> answerOf(Optional<NetworkCopy> copy, P parameters) {
        if (copy.isEmpty()) {
            return Optional.empty();
        }
        try {
            return copyAnswers.answer(copy.get(), parameters);
        } catch (RuntimeException e) {
            reportFailedAnswer(e);
            return Optional.empty();
        }
    }

    private <T> CompletableFuture<T> resultFromCopyOrController(
            Optional<R> answered,
            P parameters,
            Function<R, T> resultOf,
            CachedControllerRead<T> throughCache
    ) {
        if (answered.isPresent()) {
            T result = resultOf.apply(answered.get());
            return CompletableFuture.completedFuture(result);
        }
        return throughCache.read(() -> fetchFromController(parameters, resultOf));
    }

    private <T> CompletableFuture<T> fetchFromController(P parameters, Function<R, T> resultOf) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                R response = controllerList.list(parameters);
                return resultOf.apply(response);
            } catch (ApiException e) {
                throw ApiErrors.from(e);
            }
        }, executor);
    }

    private <T> CompletableFuture<T> resultAfterWait(
            CompletableFuture<Optional<R>> fromCopy,
            P parameters,
            Function<R, T> resultOf,
            CachedControllerRead<T> throughCache
    ) {
        CompletableFuture<T> relayed = new CompletableFuture<>();
        fromCopy.whenComplete((answered, copyFailure) ->
                readAndRelay(answered, copyFailure, parameters, resultOf, throughCache, relayed));
        return relayed;
    }

    /**
     * Relays the exception a future holds as it is: {@code thenCompose} would wrap one that is not a
     * {@link java.util.concurrent.CompletionException} yet, so a caller's {@code exceptionally} would see
     * another type than over the controller's path alone.
     */
    private <T> void readAndRelay(
            Optional<R> answered,
            @Nullable Throwable copyFailure,
            P parameters,
            Function<R, T> resultOf,
            CachedControllerRead<T> throughCache,
            CompletableFuture<T> relayed
    ) {
        if (copyFailure != null) {
            relayed.completeExceptionally(copyFailure);
            return;
        }
        try {
            CompletableFuture<T> result = resultFromCopyOrController(answered, parameters, resultOf, throughCache);
            result.whenComplete((value, failure) -> complete(relayed, value, failure));
        } catch (RuntimeException | Error failure) {
            relayed.completeExceptionally(failure);
        }
    }

    private static <T> void complete(CompletableFuture<T> relayed, T value, @Nullable Throwable failure) {
        if (failure != null) {
            relayed.completeExceptionally(failure);
            return;
        }
        relayed.complete(value);
    }

    /**
     * The read side throws where the HTTP client would fail to read the controller's body. The request over
     * HTTP then fails in the same way, with the HTTP client's own error, so the caller asks the controller.
     */
    private void reportFailedAnswer(RuntimeException failure) {
        Level level = failureReported.compareAndSet(false, true) ? Level.WARNING : Level.FINE;
        LOGGER.log(level, "Answering a read from the network view failed; it goes over HTTP", failure);
    }
}
