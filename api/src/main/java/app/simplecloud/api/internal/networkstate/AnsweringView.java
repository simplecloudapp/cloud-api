package app.simplecloud.api.internal.networkstate;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * The view as a read sees it: it hands out the copy that may answer now. {@link NetworkViewShell} implements it.
 * For an instance that has no view, {@link app.simplecloud.api.internal.networkstate.read.ReadableCopies#none()}
 * holds a stand-in that never hands out a copy, so that readers need no null check.
 */
@FunctionalInterface
public interface AnsweringView {

    /**
     * Returns the copy to answer a read from, or empty, so the read goes over HTTP. The future is complete on
     * return unless the read waits for an own write.
     */
    CompletableFuture<Optional<NetworkCopy>> copyToAnswer();
}
