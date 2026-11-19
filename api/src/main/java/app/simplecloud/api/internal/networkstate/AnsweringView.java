package app.simplecloud.api.internal.networkstate;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * The view as a read sees it: it hands out the copy that may answer now. {@link NetworkViewShell} implements it;
 * {@link #NO_VIEW} stands in for it in an instance that has no view, so that readers need no null check.
 */
@FunctionalInterface
public interface AnsweringView {

    /**
     * For an instance whose network or credential allows no view: no copy ever answers, so every read goes over
     * HTTP.
     */
    AnsweringView NO_VIEW = () -> CompletableFuture.completedFuture(Optional.empty());

    /**
     * Returns the copy to answer a read from, or empty, so the read goes over HTTP. The future is complete on
     * return unless the read waits for an own write.
     */
    CompletableFuture<Optional<NetworkCopy>> copyToAnswer();
}
