package app.simplecloud.api.internal.networkstate;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Tells a read which copy of the network may answer it now. The network's view, {@link NetworkViewShell}, is
 * the one that decides; {@link #NONE} stands in for it in an instance that has no view, so that readers need no
 * null check.
 */
@FunctionalInterface
public interface AnsweringCopy {

    /**
     * For an instance whose network or credential allows no view: no copy ever answers, so every read goes over
     * HTTP.
     */
    AnsweringCopy NONE = kind -> CompletableFuture.completedFuture(Optional.empty());

    /**
     * Returns the copy to answer a read of {@code kind} from, or empty, so the read goes over HTTP. The future
     * is complete on return unless the read waits for an own write.
     */
    CompletableFuture<Optional<NetworkCopy>> copyToAnswer(ReadKind kind);
}
